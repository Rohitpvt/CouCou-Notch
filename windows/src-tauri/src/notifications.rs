// Windows system-wide notification listener.
// Captures toast notifications from any app / software and forwards them to the island.

use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use std::time::Duration;
use tauri::{AppHandle, Emitter};

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct SystemNotification {
    pub id: u32,
    pub app_name: String,
    pub title: String,
    pub body: String,
    pub timestamp: u64,
}

#[cfg(windows)]
pub fn start(app: AppHandle) {
    std::thread::Builder::new()
        .name("coucou-notifications".into())
        .spawn(move || {
            run_listener(app);
        })
        .expect("could not start notification listener");
}

#[cfg(not(windows))]
pub fn start(_app: AppHandle) {}

#[cfg(windows)]
fn run_listener(app: AppHandle) {
    use windows::UI::Notifications::Management::{UserNotificationListener, UserNotificationListenerAccessStatus};
    use windows::UI::Notifications::NotificationKinds;
    use windows::UI::Notifications::KnownNotificationBindings;

    // Check if UserNotificationListener is supported on this Windows version
    let listener = match UserNotificationListener::Current() {
        Ok(l) => l,
        Err(e) => {
            crate::log::line(format!("UserNotificationListener::Current failed: {e}"));
            return;
        }
    };

    let status = match listener.GetAccessStatus() {
        Ok(s) => s,
        Err(e) => {
            crate::log::line(format!("UserNotificationListener::GetAccessStatus failed: {e}"));
            return;
        }
    };

    if status != UserNotificationListenerAccessStatus::Allowed {
        if let Ok(async_op) = listener.RequestAccessAsync() {
            if let Ok(res) = async_op.get() {
                crate::log::line(format!("Notification access status: {res:?}"));
            }
        }
    }

    let mut seen_ids: HashSet<u32> = HashSet::new();
    let mut initial_scan = true;

    loop {
        std::thread::sleep(Duration::from_millis(800));

        let notifications_op = match listener.GetNotificationsAsync(NotificationKinds::Toast) {
            Ok(op) => op,
            Err(_) => continue,
        };

        let notifications = match notifications_op.get() {
            Ok(n) => n,
            Err(_) => continue,
        };

        let count = match notifications.Size() {
            Ok(c) => c,
            Err(_) => continue,
        };

        let mut current_ids = HashSet::new();

        for i in 0..count {
            if let Ok(un) = notifications.GetAt(i) {
                let id = un.Id().unwrap_or(0);
                current_ids.insert(id);

                if !initial_scan && !seen_ids.contains(&id) {
                    let app_name = un.AppInfo()
                        .and_then(|info| info.DisplayInfo())
                        .and_then(|disp| disp.DisplayName())
                        .map(|h| h.to_string())
                        .unwrap_or_else(|_| "System".to_string());

                    let mut title = String::new();
                    let mut body = String::new();

                    if let Ok(notif) = un.Notification() {
                        if let Ok(visual) = notif.Visual() {
                            let generic_binding = KnownNotificationBindings::ToastGeneric().unwrap_or_default();
                            if let Ok(binding) = visual.GetBinding(&generic_binding) {
                                if let Ok(text_elements) = binding.GetTextElements() {
                                    if let Ok(elem_count) = text_elements.Size() {
                                        for idx in 0..elem_count {
                                            if let Ok(elem) = text_elements.GetAt(idx) {
                                                if let Ok(text) = elem.Text() {
                                                    let t = text.to_string();
                                                    if idx == 0 {
                                                        title = t;
                                                    } else if body.is_empty() {
                                                        body = t;
                                                    } else {
                                                        body.push_str(" ");
                                                        body.push_str(&t);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Fallback if title is empty
                    if title.is_empty() && !body.is_empty() {
                        title = app_name.clone();
                    } else if title.is_empty() && body.is_empty() {
                        title = format!("{app_name} Notification");
                    }

                    let now = std::time::SystemTime::now()
                        .duration_since(std::time::UNIX_EPOCH)
                        .map(|d| d.as_secs())
                        .unwrap_or(0);

                    let payload = SystemNotification {
                        id,
                        app_name,
                        title,
                        body,
                        timestamp: now,
                    };

                    crate::log::line(format!(
                        "System notification [#{}] {}: {} ({})",
                        payload.id, payload.app_name, payload.title, payload.body
                    ));
                    let _ = app.emit("system-notification", &payload);
                }
            }
        }

        seen_ids = current_ids;
        initial_scan = false;
    }
}
