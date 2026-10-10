// Integration pollers — the Rust side of StripePoller / GithubPoller /
// VercelPoller / N8nPoller / ResendPoller / NotionPoller / CalcomPoller.
//
// Same endpoints, same first-run delays and intervals as the Swift pollers. Each
// one emits an `integration` event; the island owns the badge, the sound and the
// 60 s auto-clear, exactly as the Swift handlers do.
//
// Nothing is polled until its key exists in the Credential Manager, and no
// request goes anywhere the user has not configured.

use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Mutex;
use std::time::Duration;

use serde::Serialize;
use serde_json::{json, Value};
use tauri::{AppHandle, Emitter, Manager};
use tokio::sync::Notify;

use crate::github::{self, GitHubActivity, GitHubPulse};
use crate::island::WINDOW_LABEL;
use crate::log;
use crate::secrets;

const TIMEOUT: Duration = Duration::from_secs(10);

/// What the island receives. `event` is only set when something actually changed,
/// which is what drives the pill badge and the sound.
#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct IntegrationUpdate {
    pub id: &'static str,
    pub data: Value,
    pub error: Option<String>,
    pub event: Option<IntegrationEvent>,
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct IntegrationEvent {
    pub success: bool,
    pub label: String,
    pub detail: Option<String>,
}

fn emit(app: &AppHandle, update: IntegrationUpdate) {
    let _ = app.emit_to(WINDOW_LABEL, "integration", update);
}

fn client() -> reqwest::Client {
    reqwest::Client::builder()
        .timeout(TIMEOUT)
        .build()
        .unwrap_or_default()
}

/// Set from the tray's Pause item. While it is on, nothing reaches the network:
/// pausing Coucou has to mean pausing Coucou, not just hiding the island.
pub static PAUSED: AtomicBool = AtomicBool::new(false);

pub fn set_paused(on: bool) {
    PAUSED.store(on, Ordering::Relaxed);
}

/// Spawns every poller with the macOS delays and intervals.
pub fn start(app: AppHandle) {
    spawn(app.clone(), "integration_n8n", 3, 15, poll_n8n);
    spawn(app.clone(), "integration_vercel", 5, 30, poll_vercel);
    spawn(app.clone(), "integration_stripe", 6, 30, poll_stripe);
    spawn(app.clone(), "integration_resend", 6, 60, poll_resend);
    spawn(app.clone(), "integration_github", 7, 300, poll_github);
    spawn_github_loops(app.clone());
    spawn(app.clone(), "integration_calcom", 8, 300, poll_calcom);
    spawn(app.clone(), "integration_gcal", 8, 60, poll_gcal);
    spawn(app, "integration_notion", 9, 300, poll_notion);
}

/// True when the user has this integration switched on in settings.
fn enabled(app: &AppHandle, id: &str) -> bool {
    app.try_state::<crate::Shared>()
        .map(|shared| {
            let settings = shared.settings.lock().unwrap();
            settings.active_integrations.iter().any(|x| x == id)
        })
        .unwrap_or(false)
}

fn spawn<F, Fut>(app: AppHandle, id: &'static str, delay_secs: u64, every_secs: u64, poll: F)
where
    F: Fn(AppHandle) -> Fut + Send + 'static,
    Fut: std::future::Future<Output = ()> + Send,
{
    tauri::async_runtime::spawn(async move {
        tokio::time::sleep(Duration::from_secs(delay_secs)).await;
        let mut ticker = tokio::time::interval(Duration::from_secs(every_secs));
        loop {
            ticker.tick().await;
            // The ticker keeps its cadence; we just decline to do the work. An
            // integration the user switched off, or a paused app, must make no
            // network calls at all — CLAUDE.md allows talking only to services
            // the user configured, and a disabled one is not configured.
            if PAUSED.load(Ordering::Relaxed) || !enabled(&app, id) {
                continue;
            }
            poll(app.clone()).await;
        }
    });
}

/// One-shot refresh from the Refresh buttons in the island.
pub async fn poll_once(app: AppHandle, id: &str) {
    match id {
        "integration_stripe" => poll_stripe(app).await,
        "integration_github" => {
            wake_github_pulse();
            github_refresh_if_stale("activity");
            poll_github(app).await
        }
        "integration_vercel" => poll_vercel(app).await,
        "integration_n8n" => poll_n8n(app).await,
        "integration_resend" => poll_resend(app).await,
        "integration_notion" => poll_notion(app).await,
        "integration_calcom" => poll_calcom(app).await,
        "integration_gcal" => poll_gcal(app).await,
        _ => {}
    }
}

/// Remembers the newest id per integration so an event fires once, not on every poll.
struct Seen(Mutex<std::collections::HashMap<&'static str, String>>);

static SEEN: std::sync::LazyLock<Seen> =
    std::sync::LazyLock::new(|| Seen(Mutex::new(std::collections::HashMap::new())));

/// Returns true the first time a given id is seen (and false on the very first
/// load, which only fills the card).
fn is_new(key: &'static str, id: &str) -> bool {
    let mut map = SEEN.0.lock().unwrap();
    match map.insert(key, id.to_string()) {
        Some(previous) => previous != id,
        None => false, // first poll: populate silently, like the Swift pollers
    }
}

/// The card's error line, in the interface language (i18n.rs);
/// `unauthorised_hint` comes translated.
fn status_error(code: u16, unauthorised_hint: &str) -> String {
    match code {
        401 => crate::i18n::t("Invalid API key (401)"),
        403 => unauthorised_hint.to_string(),
        _ => crate::i18n::tf("API error {code}", &[("code", &code.to_string())]),
    }
}

// ── Stripe ────────────────────────────────────────────────────────────────────

async fn poll_stripe(app: AppHandle) {
    let Some(key) = secrets::get("stripe-api-key") else { return };
    let auth = format!("Basic {}", crate::claude::base64_for(format!("{key}:").as_bytes()));
    let http = client();

    let balance = http
        .get("https://api.stripe.com/v1/balance")
        .header("Authorization", &auth)
        .send()
        .await;

    let (amount, currency) = match balance {
        Ok(r) if r.status().is_success() => {
            let json: Value = r.json().await.unwrap_or(json!({}));
            let mut buckets: Vec<Value> = Vec::new();
            for k in ["available", "pending"] {
                if let Some(arr) = json.get(k).and_then(Value::as_array) {
                    buckets.extend(arr.iter().cloned());
                }
            }
            let currency = buckets
                .first()
                .and_then(|b| b.get("currency"))
                .and_then(Value::as_str)
                .unwrap_or("eur")
                .to_string();
            let amount: i64 = buckets
                .iter()
                .filter_map(|b| b.get("amount").and_then(Value::as_i64))
                .sum();
            (amount, currency)
        }
        Ok(r) => {
            let code = r.status().as_u16();
            emit(&app, IntegrationUpdate {
                id: "integration_stripe",
                data: json!({}),
                error: Some(status_error(code, &crate::i18n::t("Use a secret key (sk_live_… not pk_live_…)"))),
                event: None,
            });
            return;
        }
        Err(e) => {
            emit(&app, IntegrationUpdate {
                id: "integration_stripe",
                data: json!({}),
                error: Some(crate::i18n::tf("No connection: {error}", &[("error", &e.to_string())])),
                event: None,
            });
            return;
        }
    };

    let charges = http
        .get("https://api.stripe.com/v1/charges?limit=3")
        .header("Authorization", &auth)
        .send()
        .await;
    let Ok(response) = charges else { return };
    if !response.status().is_success() {
        return;
    }
    let json: Value = response.json().await.unwrap_or(json!({}));
    let payments: Vec<Value> = json
        .get("data")
        .and_then(Value::as_array)
        .map(|list| {
            list.iter()
                .filter_map(|c| {
                    let description = c
                        .get("description")
                        .and_then(Value::as_str)
                        .or_else(|| {
                            c.get("billing_details")
                                .and_then(|b| b.get("name"))
                                .and_then(Value::as_str)
                        })
                        .map(str::to_string);
                    Some(json!({
                        "id": c.get("id")?.as_str()?,
                        "amount": c.get("amount")?.as_i64()?,
                        "currency": c.get("currency")?.as_str()?,
                        "description": description,
                        "createdAt": c.get("created").and_then(Value::as_i64).unwrap_or(0) * 1000,
                        "status": c.get("status").and_then(Value::as_str).unwrap_or("succeeded"),
                    }))
                })
                .collect()
        })
        .unwrap_or_default();

    let newest = payments
        .first()
        .and_then(|p| p.get("id"))
        .and_then(Value::as_str)
        .unwrap_or("")
        .to_string();
    let event = if !newest.is_empty() && is_new("stripe", &newest) {
        let label = payments[0]
            .get("description")
            .and_then(Value::as_str)
            .map(str::to_string)
            .unwrap_or_else(|| {
                let cents = payments[0].get("amount").and_then(Value::as_i64).unwrap_or(0);
                format!("{:.2}", cents as f64 / 100.0)
            });
        Some(IntegrationEvent { success: true, label, detail: None })
    } else {
        None
    };

    emit(&app, IntegrationUpdate {
        id: "integration_stripe",
        data: json!({ "balance": amount, "currency": currency, "payments": payments }),
        error: None,
        event,
    });
}

// ── GitHub ────────────────────────────────────────────────────────────────────

async fn poll_github(app: AppHandle) {
    let Some(token) = secrets::get("github-token") else { return };
    let http = client();

    let user = http
        .get("https://api.github.com/user")
        .header("Authorization", format!("Bearer {token}"))
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "Cocoa")
        .send()
        .await;
    let Ok(response) = user else { return };
    if !response.status().is_success() {
        emit(&app, IntegrationUpdate {
            id: "integration_github",
            data: json!({}),
            error: Some(status_error(response.status().as_u16(), &crate::i18n::t("Token lacks the needed scope"))),
            event: None,
        });
        return;
    }
    let json: Value = response.json().await.unwrap_or(json!({}));
    let public = json.get("public_repos").and_then(Value::as_i64).unwrap_or(0);
    let private = json
        .get("owned_private_repos")
        .or_else(|| json.get("total_private_repos"))
        .and_then(Value::as_i64)
        .unwrap_or(0);

    let repos = http
        .get("https://api.github.com/user/repos?per_page=100&affiliation=owner&sort=pushed")
        .header("Authorization", format!("Bearer {token}"))
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "Cocoa")
        .send()
        .await;
    let stars: i64 = match repos {
        Ok(r) if r.status().is_success() => r
            .json::<Value>()
            .await
            .ok()
            .and_then(|v| v.as_array().cloned())
            .map(|list| {
                list.iter()
                    .filter_map(|r| r.get("stargazers_count").and_then(Value::as_i64))
                    .sum()
            })
            .unwrap_or(0),
        _ => 0,
    };

    GITHUB.cache.lock().unwrap().stats = Some((public + private, stars));
    emit_github(&app);
}

// ── GitHub pulse and activity (GithubPoller.swift) ────────────────────────────
//
// Two more loops next to the stats above, with the Mac cadence: the pulse (my
// pull requests and their CI, reviews waiting for me, default-branch CI) 10 s
// after launch, then every 60 s while some CI is running and every 5 min
// otherwise; the contribution calendar 15 s after launch, then every 30 min.
// Neither touches the network while the pill is off or Coucou is paused, and
// the island wakes them when the card is opened on stale data.
//
// All three results are kept here and always sent together, so one poll never
// wipes what another one reported.

const GITHUB_ID: &str = "integration_github";

#[derive(Default)]
struct GitHubCache {
    /// (repositories, stars)
    stats: Option<(i64, i64)>,
    pulse: Option<GitHubPulse>,
    activity: Option<GitHubActivity>,
}

struct GitHubLoops {
    cache: Mutex<GitHubCache>,
    /// Bumped when the token changes: a response for the old token is dropped.
    generation: AtomicU64,
    pulse_wake: Notify,
    activity_wake: Notify,
    pulse_busy: AtomicBool,
    activity_busy: AtomicBool,
}

static GITHUB: std::sync::LazyLock<GitHubLoops> = std::sync::LazyLock::new(|| GitHubLoops {
    cache: Mutex::new(GitHubCache::default()),
    generation: AtomicU64::new(0),
    pulse_wake: Notify::new(),
    activity_wake: Notify::new(),
    pulse_busy: AtomicBool::new(false),
    activity_busy: AtomicBool::new(false),
});

fn now_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

/// What the island's GitHub card receives: whichever of the three is known.
fn github_card_data(cache: &GitHubCache) -> Value {
    let mut data = serde_json::Map::new();
    if let Some((repos, stars)) = cache.stats {
        data.insert("totalRepos".into(), json!(repos));
        data.insert("totalStars".into(), json!(stars));
    }
    if let Some(pulse) = &cache.pulse {
        data.insert("pulse".into(), serde_json::to_value(pulse).unwrap_or(Value::Null));
    }
    if let Some(activity) = &cache.activity {
        data.insert("activity".into(), serde_json::to_value(activity).unwrap_or(Value::Null));
    }
    Value::Object(data)
}

fn emit_github(app: &AppHandle) {
    let data = github_card_data(&GITHUB.cache.lock().unwrap());
    emit(app, IntegrationUpdate { id: GITHUB_ID, data, error: None, event: None });
}

fn spawn_github_loops(app: AppHandle) {
    let pulse_app = app.clone();
    tauri::async_runtime::spawn(async move {
        let mut wait = Duration::from_secs(10);
        loop {
            // Sleeps for `wait`, or less when the island asks for fresh data.
            let _ = tokio::time::timeout(wait, GITHUB.pulse_wake.notified()).await;
            let pending = run_github_pulse(&pulse_app).await;
            wait = Duration::from_secs(if pending { 60 } else { 300 });
        }
    });
    tauri::async_runtime::spawn(async move {
        let mut wait = Duration::from_secs(15);
        loop {
            let _ = tokio::time::timeout(wait, GITHUB.activity_wake.notified()).await;
            run_github_activity(&app).await;
            wait = Duration::from_secs(1800);
        }
    });
}

/// One authenticated GraphQL call. Partial errors are logged (as a count) and the
/// data parsed anyway; only a response without `data` is dropped.
async fn github_graphql(token: &str, query: &str, what: &str) -> Option<Value> {
    let response = client()
        .post("https://api.github.com/graphql")
        .header("Authorization", format!("Bearer {token}"))
        .header("Content-Type", "application/json")
        .header("User-Agent", "Cocoa")
        .json(&json!({ "query": query }))
        .send()
        .await;
    let Ok(response) = response else {
        log::line(format!("github {what}: no connection"));
        return None;
    };
    if !response.status().is_success() {
        log::line(format!("github {what} HTTP {}", response.status().as_u16()));
        return None;
    }
    let root: Value = response.json().await.ok()?;
    let errors = github::graphql_error_count(&root);
    if errors > 0 {
        log::line(format!("github {what} GraphQL errors: {errors}"));
    }
    root.get("data").filter(|d| d.is_object())?;
    Some(root)
}

/// Returns whether some CI is still running, which sets the next interval.
async fn run_github_pulse(app: &AppHandle) -> bool {
    if PAUSED.load(Ordering::Relaxed) || !enabled(app, GITHUB_ID) {
        return false;
    }
    let Some(token) = secrets::get("github-token") else { return false };
    let generation = GITHUB.generation.load(Ordering::SeqCst);
    GITHUB.pulse_busy.store(true, Ordering::SeqCst);
    let root = github_graphql(&token, github::PULSE_QUERY, "pulse").await;
    GITHUB.pulse_busy.store(false, Ordering::SeqCst);
    let Some(pulse) = root.and_then(|r| GitHubPulse::parse(&r, now_ms())) else { return false };
    let pending = pulse.has_pending();

    let events = {
        let mut cache = GITHUB.cache.lock().unwrap();
        // The token changed while this was in flight: it answers for someone else.
        if GITHUB.generation.load(Ordering::SeqCst) != generation {
            return pending;
        }
        let events = GitHubPulse::events(cache.pulse.as_ref(), &pulse);
        cache.pulse = Some(pulse);
        events
    };
    emit_github(app);
    if !events.is_empty() {
        let _ = app.emit_to(WINDOW_LABEL, "github-alerts", &events);
    }
    pending
}

async fn run_github_activity(app: &AppHandle) {
    if PAUSED.load(Ordering::Relaxed) || !enabled(app, GITHUB_ID) {
        return;
    }
    let Some(token) = secrets::get("github-token") else { return };
    let generation = GITHUB.generation.load(Ordering::SeqCst);
    GITHUB.activity_busy.store(true, Ordering::SeqCst);
    let root = github_graphql(&token, github::ACTIVITY_QUERY, "activity").await;
    GITHUB.activity_busy.store(false, Ordering::SeqCst);
    let Some(activity) = root.and_then(|r| GitHubActivity::parse(&r, now_ms())) else { return };
    {
        let mut cache = GITHUB.cache.lock().unwrap();
        if GITHUB.generation.load(Ordering::SeqCst) != generation {
            return;
        }
        cache.activity = Some(activity);
    }
    emit_github(app);
}

fn wake_github_pulse() {
    if !GITHUB.pulse_busy.load(Ordering::SeqCst) {
        GITHUB.pulse_wake.notify_one();
    }
}

/// The card was opened: fetch now if what it shows is older than the Mac's
/// limits (1 min for the pulse, 5 min for the activity grid). No-op while a
/// request is already in flight.
pub fn github_refresh_if_stale(section: &str) {
    let now = now_ms();
    let cache = GITHUB.cache.lock().unwrap();
    match section {
        "pulse" => {
            let fetched = cache.pulse.as_ref().map(|p| p.fetched_at);
            if !GITHUB.pulse_busy.load(Ordering::SeqCst) && github::is_stale(fetched, now, 60) {
                GITHUB.pulse_wake.notify_one();
            }
        }
        "activity" => {
            let fetched = cache.activity.as_ref().map(|a| a.fetched_at);
            if !GITHUB.activity_busy.load(Ordering::SeqCst) && github::is_stale(fetched, now, 300) {
                GITHUB.activity_wake.notify_one();
            }
        }
        _ => {}
    }
}

/// The GitHub token was saved or removed: forget what the old one fetched, drop
/// its requests still in flight, and fetch again right away.
pub fn github_token_changed(app: &AppHandle) {
    GITHUB.generation.fetch_add(1, Ordering::SeqCst);
    let has_token = secrets::get("github-token").is_some();
    {
        let mut cache = GITHUB.cache.lock().unwrap();
        cache.pulse = None;
        cache.activity = None;
        if !has_token {
            cache.stats = None;
        }
    }
    emit_github(app);
    GITHUB.pulse_wake.notify_one();
    GITHUB.activity_wake.notify_one();
    if has_token && !PAUSED.load(Ordering::Relaxed) && enabled(app, GITHUB_ID) {
        tauri::async_runtime::spawn(poll_github(app.clone()));
    }
}

/// Switching the pill off forgets the pulse, so switching it back on starts
/// silent instead of alerting on everything that changed in between.
pub fn settings_saved(app: &AppHandle, active_integrations: &[String]) {
    if active_integrations.iter().any(|id| id == GITHUB_ID) {
        return;
    }
    let had_data = {
        let mut cache = GITHUB.cache.lock().unwrap();
        let had = cache.pulse.is_some() || cache.activity.is_some();
        cache.pulse = None;
        cache.activity = None;
        had
    };
    if had_data {
        emit_github(app);
    }
}

// ── Vercel ────────────────────────────────────────────────────────────────────

async fn poll_vercel(app: AppHandle) {
    let Some(token) = secrets::get("vercel-token") else { return };
    let response = client()
        .get("https://api.vercel.com/v6/deployments?limit=5")
        .header("Authorization", format!("Bearer {token}"))
        .header("Accept", "application/json")
        .send()
        .await;
    let Ok(response) = response else { return };
    if !response.status().is_success() {
        emit(&app, IntegrationUpdate {
            id: "integration_vercel",
            data: json!({}),
            error: Some(status_error(response.status().as_u16(), &crate::i18n::t("Token lacks access"))),
            event: None,
        });
        return;
    }
    let json: Value = response.json().await.unwrap_or(json!({}));
    let terminal = ["READY", "ERROR", "CANCELED"];
    let deployments: Vec<Value> = json
        .get("deployments")
        .and_then(Value::as_array)
        .map(|list| {
            list.iter()
                .filter_map(|d| {
                    let state = d.get("state")?.as_str()?;
                    if !terminal.contains(&state) {
                        return None;
                    }
                    let meta = d.get("meta");
                    let pick = |keys: [&str; 3]| {
                        meta.and_then(|m| keys.iter().find_map(|k| m.get(*k).and_then(Value::as_str)))
                            .map(str::to_string)
                    };
                    Some(json!({
                        "id": d.get("uid")?.as_str()?,
                        "projectName": d.get("name")?.as_str()?,
                        "url": d.get("url").and_then(Value::as_str).unwrap_or(""),
                        "state": state,
                        "createdAt": d.get("createdAt").and_then(Value::as_f64).unwrap_or(0.0),
                        "commitMessage": pick(["githubCommitMessage", "gitlabCommitMessage", "bitbucketCommitMessage"]),
                        "branch": pick(["githubCommitRef", "gitlabCommitRef", "bitbucketBranch"]),
                    }))
                })
                .collect()
        })
        .unwrap_or_default();

    let event = deployments.first().and_then(|latest| {
        let id = latest.get("id")?.as_str()?;
        if !is_new("vercel", id) {
            return None;
        }
        let success = latest.get("state")?.as_str()? == "READY";
        Some(IntegrationEvent {
            success,
            label: latest.get("projectName")?.as_str()?.to_string(),
            detail: None,
        })
    });

    emit(&app, IntegrationUpdate {
        id: "integration_vercel",
        data: json!({ "deployments": deployments }),
        error: None,
        event,
    });
}

// ── Resend ────────────────────────────────────────────────────────────────────

async fn poll_resend(app: AppHandle) {
    let Some(key) = secrets::get("resend-api-key") else { return };
    let response = client()
        .get("https://api.resend.com/emails?limit=100")
        .header("Authorization", format!("Bearer {key}"))
        .header("Accept", "application/json")
        .send()
        .await;
    let Ok(response) = response else { return };
    if !response.status().is_success() {
        emit(&app, IntegrationUpdate {
            id: "integration_resend",
            data: json!({}),
            error: Some(status_error(response.status().as_u16(), &crate::i18n::t("Key lacks access"))),
            event: None,
        });
        return;
    }
    let json: Value = response.json().await.unwrap_or(json!({}));
    let total = json
        .get("total")
        .or_else(|| json.get("count"))
        .and_then(Value::as_i64);
    let emails: Vec<Value> = json
        .get("data")
        .and_then(Value::as_array)
        .map(|list| {
            list.iter()
                .take(5)
                .filter_map(|e| {
                    let to = match e.get("to") {
                        Some(Value::Array(a)) => a.clone(),
                        Some(Value::String(s)) => vec![Value::String(s.clone())],
                        _ => vec![],
                    };
                    Some(json!({
                        "id": e.get("id")?.as_str()?,
                        "to": to,
                        "subject": e.get("subject").and_then(Value::as_str).unwrap_or(""),
                        "createdAt": e.get("created_at").and_then(Value::as_str).unwrap_or(""),
                        "lastEvent": e.get("last_event").and_then(Value::as_str).unwrap_or(""),
                    }))
                })
                .collect()
        })
        .unwrap_or_default();

    emit(&app, IntegrationUpdate {
        id: "integration_resend",
        data: json!({ "emails": emails, "total": total }),
        error: None,
        event: None,
    });
}

// ── Notion ────────────────────────────────────────────────────────────────────

async fn poll_notion(app: AppHandle) {
    let Some(token) = secrets::get("notion-api-key") else { return };
    let response = client()
        .post("https://api.notion.com/v1/search")
        .header("Authorization", format!("Bearer {token}"))
        .header("Notion-Version", "2022-06-28")
        .header("Content-Type", "application/json")
        .json(&json!({
            "sort": { "direction": "descending", "timestamp": "last_edited_time" },
            "page_size": 3
        }))
        .send()
        .await;
    let Ok(response) = response else { return };
    if !response.status().is_success() {
        emit(&app, IntegrationUpdate {
            id: "integration_notion",
            data: json!({}),
            error: Some(status_error(response.status().as_u16(), &crate::i18n::t("Integration lacks access"))),
            event: None,
        });
        return;
    }
    let json: Value = response.json().await.unwrap_or(json!({}));
    let pages: Vec<Value> = json
        .get("results")
        .and_then(Value::as_array)
        .map(|list| list.iter().filter_map(parse_notion_page).collect())
        .unwrap_or_default();

    emit(&app, IntegrationUpdate {
        id: "integration_notion",
        data: json!({ "pages": pages }),
        error: None,
        event: None,
    });
}

fn parse_notion_page(obj: &Value) -> Option<Value> {
    let id = obj.get("id")?.as_str()?;
    let is_database = obj.get("object").and_then(Value::as_str) == Some("database");

    let mut title = "Untitled".to_string();
    if is_database {
        if let Some(text) = obj
            .get("title")
            .and_then(Value::as_array)
            .and_then(|a| a.first())
            .and_then(|t| t.get("plain_text"))
            .and_then(Value::as_str)
        {
            if !text.is_empty() {
                title = text.to_string();
            }
        }
    } else if let Some(props) = obj.get("properties").and_then(Value::as_object) {
        for prop in props.values() {
            if prop.get("type").and_then(Value::as_str) != Some("title") {
                continue;
            }
            if let Some(text) = prop
                .get("title")
                .and_then(Value::as_array)
                .and_then(|a| a.first())
                .and_then(|t| t.get("plain_text"))
                .and_then(Value::as_str)
            {
                if !text.is_empty() {
                    title = text.to_string();
                    break;
                }
            }
        }
    }

    let emoji = obj
        .get("icon")
        .filter(|i| i.get("type").and_then(Value::as_str) == Some("emoji"))
        .and_then(|i| i.get("emoji"))
        .and_then(Value::as_str);

    Some(json!({
        "id": id,
        "title": title,
        "emoji": emoji,
        "lastEditedAt": obj.get("last_edited_time").and_then(Value::as_str)?,
        "url": obj.get("url").and_then(Value::as_str).unwrap_or("https://notion.so"),
    }))
}

// ── Cal.com ───────────────────────────────────────────────────────────────────

async fn poll_calcom(app: AppHandle) {
    let Some(key) = secrets::get("calcom-api-key") else { return };
    let response = client()
        .get("https://api.cal.com/v2/bookings?status=upcoming")
        .header("Authorization", format!("Bearer {key}"))
        .header("cal-api-version", "2024-08-13")
        .send()
        .await;
    let Ok(response) = response else { return };
    if !response.status().is_success() {
        emit(&app, IntegrationUpdate {
            id: "integration_calcom",
            data: json!({}),
            error: Some(status_error(response.status().as_u16(), &crate::i18n::t("Key lacks access"))),
            event: None,
        });
        return;
    }
    let json: Value = response.json().await.unwrap_or(json!({}));
    let bookings: Vec<Value> = json
        .get("data")
        .and_then(Value::as_array)
        .map(|list| {
            list.iter()
                .filter_map(|b| {
                    let start = b
                        .get("start")
                        .or_else(|| b.get("startTime"))
                        .and_then(Value::as_str)?;
                    let attendee = b.get("attendees").and_then(Value::as_array).and_then(|a| a.first());
                    let notes = b
                        .get("responses")
                        .and_then(|r| r.get("notes"))
                        .and_then(|n| n.get("value"))
                        .and_then(Value::as_str)
                        .or_else(|| b.get("description").and_then(Value::as_str))
                        .filter(|s| !s.is_empty());
                    Some(json!({
                        "id": b.get("id").map(|v| v.to_string()).unwrap_or_default(),
                        "title": b.get("title").and_then(Value::as_str).unwrap_or("Meeting"),
                        "start": start,
                        "status": b.get("status").and_then(Value::as_str).unwrap_or("accepted"),
                        "attendeeName": attendee.and_then(|a| a.get("name")).and_then(Value::as_str),
                        "attendeeEmail": attendee.and_then(|a| a.get("email")).and_then(Value::as_str),
                        "attendeeNotes": notes,
                    }))
                })
                .collect()
        })
        .unwrap_or_default();

    emit(&app, IntegrationUpdate {
        id: "integration_calcom",
        data: json!({ "bookings": bookings }),
        error: None,
        event: None,
    });
}

// ── n8n ───────────────────────────────────────────────────────────────────────

async fn poll_n8n(app: AppHandle) {
    let (Some(key), Some(raw_base)) = (secrets::get("n8n-api-key"), secrets::get("n8n-url")) else {
        return;
    };
    let base = raw_base.trim_end_matches('/').to_string();
    let http = client();

    // Same two shapes as the Swift poller: the public API first, then /rest.
    let list_urls = [
        format!("{base}/api/v1/executions?limit=1&includeData=false"),
        format!("{base}/rest/executions?limit=1&includeData=false"),
    ];

    let mut items: Option<Vec<Value>> = None;
    for url in &list_urls {
        let Ok(response) = http.get(url).header("X-N8N-API-KEY", &key).header("Accept", "application/json").send().await
        else {
            continue;
        };
        if !response.status().is_success() {
            // Only the status: a self-hosted base URL can carry credentials.
            log::line(format!("n8n list HTTP {}", response.status()));
            continue;
        }
        let Ok(json) = response.json::<Value>().await else { continue };
        items = match &json {
            Value::Object(o) => o.get("data").and_then(Value::as_array).cloned(),
            Value::Array(a) => Some(a.clone()),
            _ => None,
        };
        if items.is_some() {
            break;
        }
    }

    let Some(first) = items.and_then(|list| list.into_iter().next()) else { return };
    let id = match first.get("id") {
        Some(Value::String(s)) => s.clone(),
        Some(Value::Number(n)) => n.to_string(),
        _ => return,
    };

    let status = first.get("status").and_then(Value::as_str).unwrap_or("");
    if !["success", "error", "crashed", "canceled", "failed"].contains(&status) {
        return;
    }
    if !is_new("n8n", &id) {
        return;
    }
    let success = status == "success";

    let detail_urls = [
        format!("{base}/api/v1/executions/{id}?includeData=true"),
        format!("{base}/api/v1/executions/{id}"),
        format!("{base}/rest/executions/{id}?includeData=true"),
        format!("{base}/rest/executions/{id}"),
    ];
    let mut name = crate::i18n::t("Workflow");
    let mut detail = None;
    for url in &detail_urls {
        let Ok(response) = http.get(url).header("X-N8N-API-KEY", &key).header("Accept", "application/json").send().await
        else {
            continue;
        };
        if !response.status().is_success() {
            continue;
        }
        let Ok(json) = response.json::<Value>().await else { continue };
        name = json
            .get("workflowData")
            .and_then(|w| w.get("name"))
            .and_then(Value::as_str)
            .or_else(|| json.get("name").and_then(Value::as_str))
            .map(str::to_string)
            .unwrap_or_else(|| crate::i18n::t("Workflow"));
        detail = n8n_detail(&json, success);
        break;
    }

    log::line(format!("n8n execution {id} {status} · {name}"));
    emit(&app, IntegrationUpdate {
        id: "integration_n8n",
        data: json!({ "workflow": name, "status": status }),
        error: None,
        event: Some(IntegrationEvent { success, label: name, detail }),
    });
}

fn n8n_detail(json: &Value, success: bool) -> Option<String> {
    let result = json.get("data")?.get("resultData")?;
    if !success {
        if let Some(error) = result.get("error") {
            let message = error.get("message").and_then(Value::as_str).unwrap_or("");
            if let Some(node) = error.get("node").and_then(|n| n.get("name")).and_then(Value::as_str) {
                if !node.is_empty() {
                    return Some(format!("{node}\n{message}"));
                }
            }
            return Some(message.to_string());
        }
        let runs = result.get("runData")?.as_object()?;
        for (node, value) in runs {
            if let Some(message) = value
                .as_array()
                .and_then(|a| a.first())
                .and_then(|r| r.get("error"))
                .and_then(|e| e.get("message"))
                .and_then(Value::as_str)
            {
                return Some(format!("{node}\n{message}"));
            }
        }
        return None;
    }

    let last_node = result.get("lastNodeExecuted")?.as_str()?;
    let items = result
        .get("runData")?
        .get(last_node)?
        .as_array()?
        .first()?
        .get("data")?
        .get("main")?
        .as_array()?
        .first()?
        .as_array()?;
    let count = items.len();
    let header = format!("→ {last_node} · {count} item{}", if count == 1 { "" } else { "s" });

    let fields = items
        .first()
        .and_then(|i| i.get("json"))
        .and_then(Value::as_object)
        .map(|obj| {
            obj.iter()
                .take(4)
                .map(|(k, v)| format!("{k}: {}", fmt_value(v)))
                .collect::<Vec<_>>()
                .join("\n")
        })
        .filter(|s| !s.is_empty());

    Some(match fields {
        Some(f) => format!("{header}\n{f}"),
        None => header,
    })
}

fn fmt_value(v: &Value) -> String {
    match v {
        Value::String(s) => s.chars().take(50).collect(),
        Value::Array(a) => format!("[{}]", a.len()),
        Value::Object(_) => "{…}".into(),
        other => other.to_string(),
    }
}

// ── Google Calendar (iCal Feed) ───────────────────────────────────────────────

#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct GCalEvent {
    pub id: String,
    pub title: String,
    pub start: String,
    pub end: Option<String>,
    pub start_epoch: i64,
    pub end_epoch: Option<i64>,
    pub is_all_day: bool,
    pub location: Option<String>,
    pub description: Option<String>,
    pub meet_url: Option<String>,
    pub status: String,
}

static LAST_GCAL_ALERT_UID: Mutex<Option<String>> = Mutex::new(None);

fn days_from_civil(mut y: i64, m: u32, d: u32) -> i64 {
    y -= if m <= 2 { 1 } else { 0 };
    let era = if y >= 0 { y } else { y - 399 } / 400;
    let yoe = (y - era * 400) as u32;
    let doy = (153 * (if m > 2 { m - 3 } else { m + 9 }) + 2) / 5 + d - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    era * 146097 + doe as i64 - 719468
}

fn civil_from_days(z: i64) -> (i64, u32, u32) {
    let z = z + 719468;
    let era = if z >= 0 { z } else { z - 146096 } / 146097;
    let doe = (z - era * 146097) as u32;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    let y = yoe as i64 + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let y = y + if m <= 2 { 1 } else { 0 };
    (y, m, d)
}

fn epoch_secs_to_ymd_hms(secs: i64) -> (i64, u32, u32, u32, u32, u32) {
    let days = secs.div_euclid(86400);
    let rem = secs.rem_euclid(86400) as u32;
    let (year, month, day) = civil_from_days(days);
    let hour = rem / 3600;
    let minute = (rem % 3600) / 60;
    let second = rem % 60;
    (year, month, day, hour, minute, second)
}

fn is_leap_year(year: i64) -> bool {
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}

fn days_in_month(year: i64, month: u32) -> u32 {
    match month {
        1 => 31,
        2 => if is_leap_year(year) { 29 } else { 28 },
        3 => 31,
        4 => 30,
        5 => 31,
        6 => 30,
        7 => 31,
        8 => 31,
        9 => 30,
        10 => 31,
        11 => 30,
        12 => 31,
        _ => 30,
    }
}

fn ymd_hms_to_epoch_secs(year: i64, month: u32, day: u32, hour: u32, minute: u32, second: u32) -> i64 {
    let days = days_from_civil(year, month, day);
    days * 86400 + (hour as i64) * 3600 + (minute as i64) * 60 + (second as i64)
}

fn unfold_ical(text: &str) -> String {
    let mut res = String::with_capacity(text.len());
    let mut chars = text.chars().peekable();
    while let Some(c) = chars.next() {
        if c == '\r' && chars.peek() == Some(&'\n') {
            chars.next();
            if chars.peek() == Some(&' ') || chars.peek() == Some(&'\t') {
                chars.next();
                continue;
            }
            res.push('\n');
        } else if c == '\n' {
            if chars.peek() == Some(&' ') || chars.peek() == Some(&'\t') {
                chars.next();
                continue;
            }
            res.push('\n');
        } else {
            res.push(c);
        }
    }
    res
}

fn unescape_ical_value(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars().peekable();
    while let Some(c) = chars.next() {
        if c == '\\' {
            match chars.next() {
                Some('n') | Some('N') => out.push('\n'),
                Some('\\') => out.push('\\'),
                Some(';') => out.push(';'),
                Some(',') => out.push(','),
                Some(other) => {
                    out.push('\\');
                    out.push(other);
                }
                None => out.push('\\'),
            }
        } else {
            out.push(c);
        }
    }
    out
}

fn parse_ical_date(val: &str) -> Option<(i64, String, bool)> {
    let clean = val.trim();
    if clean.len() >= 15 && clean.contains('T') {
        let (d_part, t_part) = clean.split_once('T')?;
        if d_part.len() < 8 || t_part.len() < 6 { return None; }
        let year: i64 = d_part[0..4].parse().ok()?;
        let month: u32 = d_part[4..6].parse().ok()?;
        let day: u32 = d_part[6..8].parse().ok()?;
        let hour: u32 = t_part[0..2].parse().ok()?;
        let minute: u32 = t_part[2..4].parse().ok()?;
        let second: u32 = t_part[4..6].parse().ok()?;

        let epoch = ymd_hms_to_epoch_secs(year, month, day, hour, minute, second);
        let iso = format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", year, month, day, hour, minute, second);
        Some((epoch, iso, false))
    } else if clean.len() >= 8 {
        let year: i64 = clean[0..4].parse().ok()?;
        let month: u32 = clean[4..6].parse().ok()?;
        let day: u32 = clean[6..8].parse().ok()?;

        let epoch = ymd_hms_to_epoch_secs(year, month, day, 0, 0, 0);
        let iso = format!("{:04}-{:02}-{:02}", year, month, day);
        Some((epoch, iso, true))
    } else {
        None
    }
}

fn extract_meet_url(text: &str) -> Option<String> {
    for word in text.split_whitespace() {
        let clean = word.trim_matches(|c: char| c == '<' || c == '>' || c == '(' || c == ')' || c == '"' || c == '\'');
        if clean.starts_with("https://meet.google.com/")
            || clean.contains(".zoom.us/j/")
            || clean.starts_with("https://teams.microsoft.com/")
            || clean.contains(".webex.com/")
        {
            return Some(clean.to_string());
        }
    }
    None
}

struct ParsedRRule {
    freq: String,
    interval: u32,
    until_epoch: Option<i64>,
    byday: Vec<u32>, // 0 = Sun, 1 = Mon, ..., 6 = Sat
}

fn parse_rrule(rrule_str: &str) -> ParsedRRule {
    let mut freq = String::new();
    let mut interval: u32 = 1;
    let mut until_epoch: Option<i64> = None;
    let mut byday = Vec::new();

    for part in rrule_str.split(';') {
        let Some((k, v)) = part.split_once('=') else { continue };
        match k.trim().to_uppercase().as_str() {
            "FREQ" => freq = v.trim().to_uppercase(),
            "INTERVAL" => interval = v.trim().parse().unwrap_or(1).max(1),
            "UNTIL" => {
                if let Some((ep, _, _)) = parse_ical_date(v.trim()) {
                    until_epoch = Some(ep);
                }
            }
            "BYDAY" => {
                for day_str in v.split(',') {
                    let d = day_str.trim().to_uppercase();
                    let d_code = match d.as_str() {
                        "SU" => Some(0),
                        "MO" => Some(1),
                        "TU" => Some(2),
                        "WE" => Some(3),
                        "TH" => Some(4),
                        "FR" => Some(5),
                        "SA" => Some(6),
                        _ => None,
                    };
                    if let Some(c) = d_code {
                        byday.push(c);
                    }
                }
            }
            _ => {}
        }
    }

    ParsedRRule { freq, interval, until_epoch, byday }
}

pub fn parse_gcal_ical_window(raw: &str, window_start: i64, window_end: i64) -> Vec<GCalEvent> {
    let unfolded = unfold_ical(raw);
    let mut events = Vec::new();
    let mut in_event = false;

    let mut uid = String::new();
    let mut summary = String::new();
    let mut dtstart = String::new();
    let mut dtend = String::new();
    let mut location = String::new();
    let mut description = String::new();
    let mut url = String::new();
    let mut status = "CONFIRMED".to_string();
    let mut rrule = String::new();

    for line in unfolded.lines() {
        let line = line.trim();
        if line == "BEGIN:VEVENT" {
            in_event = true;
            uid.clear();
            summary.clear();
            dtstart.clear();
            dtend.clear();
            location.clear();
            description.clear();
            url.clear();
            status = "CONFIRMED".to_string();
            rrule.clear();
            continue;
        }
        if line == "END:VEVENT" {
            if in_event {
                if status.eq_ignore_ascii_case("CANCELLED") {
                    in_event = false;
                    continue;
                }
                if let Some((start_epoch, start_iso, is_all_day)) = parse_ical_date(&dtstart) {
                    let (end_epoch, end_iso) = parse_ical_date(&dtend)
                        .map(|(ep, iso, _)| (Some(ep), Some(iso)))
                        .unwrap_or((None, None));

                    let duration = end_epoch.map(|e| (e - start_epoch).max(0)).unwrap_or(if is_all_day { 86400 } else { 0 });

                    let desc_opt = if description.is_empty() { None } else { Some(description.clone()) };
                    let loc_opt = if location.is_empty() { None } else { Some(location.clone()) };
                    let title = if summary.is_empty() { "Event".to_string() } else { summary.clone() };

                    let meet_url = extract_meet_url(&url)
                        .or_else(|| loc_opt.as_deref().and_then(extract_meet_url))
                        .or_else(|| desc_opt.as_deref().and_then(extract_meet_url));

                    let event_uid = if uid.is_empty() { format!("event-{}", start_epoch) } else { uid.clone() };

                    if rrule.is_empty() {
                        // Single event
                        let effective_end = end_epoch.unwrap_or(start_epoch + duration);
                        if effective_end >= window_start && start_epoch <= window_end {
                            events.push(GCalEvent {
                                id: event_uid,
                                title,
                                start: start_iso,
                                end: end_iso,
                                start_epoch,
                                end_epoch,
                                is_all_day,
                                location: loc_opt,
                                description: desc_opt,
                                meet_url,
                                status: status.clone(),
                            });
                        }
                    } else {
                        // Expand recurrence (RRULE)
                        let parsed = parse_rrule(&rrule);
                        let (s_year, s_month, s_day, s_hour, s_min, s_sec) = epoch_secs_to_ymd_hms(start_epoch);

                        match parsed.freq.as_str() {
                            "YEARLY" => {
                                let (win_y1, _, _) = civil_from_days(window_start.div_euclid(86400));
                                let (win_y2, _, _) = civil_from_days(window_end.div_euclid(86400));
                                for y in win_y1.max(s_year)..=win_y2 {
                                    if (y - s_year) % (parsed.interval as i64) == 0 {
                                        let d = s_day.min(days_in_month(y, s_month));
                                        let inst_start = ymd_hms_to_epoch_secs(y, s_month, d, s_hour, s_min, s_sec);
                                        let inst_end = inst_start + duration;
                                        if inst_end >= window_start && inst_start <= window_end {
                                            if parsed.until_epoch.map(|u| inst_start <= u).unwrap_or(true) {
                                                let (iso_start, iso_end) = if is_all_day {
                                                    (format!("{:04}-{:02}-{:02}", y, s_month, d), Some(format!("{:04}-{:02}-{:02}", y, s_month, d)))
                                                } else {
                                                    let (ey, em, ed, eh, emin, es) = epoch_secs_to_ymd_hms(inst_end);
                                                    (format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", y, s_month, d, s_hour, s_min, s_sec),
                                                     Some(format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", ey, em, ed, eh, emin, es)))
                                                };
                                                events.push(GCalEvent {
                                                    id: format!("{}_{}", event_uid, inst_start),
                                                    title: title.clone(),
                                                    start: iso_start,
                                                    end: iso_end,
                                                    start_epoch: inst_start,
                                                    end_epoch: Some(inst_end),
                                                    is_all_day,
                                                    location: loc_opt.clone(),
                                                    description: desc_opt.clone(),
                                                    meet_url: meet_url.clone(),
                                                    status: status.clone(),
                                                });
                                            }
                                        }
                                    }
                                }
                            }
                            "MONTHLY" => {
                                let (win_y1, win_m1, _) = civil_from_days(window_start.div_euclid(86400));
                                let (win_y2, win_m2, _) = civil_from_days(window_end.div_euclid(86400));
                                let start_m_idx = s_year * 12 + (s_month - 1) as i64;
                                let win_m1_idx = win_y1 * 12 + (win_m1 - 1) as i64;
                                let win_m2_idx = win_y2 * 12 + (win_m2 - 1) as i64;
                                for m_idx in win_m1_idx.max(start_m_idx)..=win_m2_idx {
                                    if (m_idx - start_m_idx) % (parsed.interval as i64) == 0 {
                                        let y = m_idx.div_euclid(12);
                                        let m = m_idx.rem_euclid(12) as u32 + 1;
                                        let d = s_day.min(days_in_month(y, m));
                                        let inst_start = ymd_hms_to_epoch_secs(y, m, d, s_hour, s_min, s_sec);
                                        let inst_end = inst_start + duration;
                                        if inst_end >= window_start && inst_start <= window_end {
                                            if parsed.until_epoch.map(|u| inst_start <= u).unwrap_or(true) {
                                                let (iso_start, iso_end) = if is_all_day {
                                                    (format!("{:04}-{:02}-{:02}", y, m, d), Some(format!("{:04}-{:02}-{:02}", y, m, d)))
                                                } else {
                                                    let (ey, em, ed, eh, emin, es) = epoch_secs_to_ymd_hms(inst_end);
                                                    (format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", y, m, d, s_hour, s_min, s_sec),
                                                     Some(format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", ey, em, ed, eh, emin, es)))
                                                };
                                                events.push(GCalEvent {
                                                    id: format!("{}_{}", event_uid, inst_start),
                                                    title: title.clone(),
                                                    start: iso_start,
                                                    end: iso_end,
                                                    start_epoch: inst_start,
                                                    end_epoch: Some(inst_end),
                                                    is_all_day,
                                                    location: loc_opt.clone(),
                                                    description: desc_opt.clone(),
                                                    meet_url: meet_url.clone(),
                                                    status: status.clone(),
                                                });
                                            }
                                        }
                                    }
                                }
                            }
                            "WEEKLY" => {
                                let time_of_day = (s_hour as i64) * 3600 + (s_min as i64) * 60 + (s_sec as i64);
                                let win_d1 = window_start.div_euclid(86400);
                                let win_d2 = window_end.div_euclid(86400);
                                let start_d = start_epoch.div_euclid(86400);

                                if parsed.byday.is_empty() {
                                    let step_secs = (parsed.interval as i64) * 7 * 86400;
                                    let mut cur = start_epoch;
                                    if cur < window_start - step_secs {
                                        let skip = (window_start - cur).div_euclid(step_secs);
                                        cur += skip * step_secs;
                                    }
                                    while cur <= window_end {
                                        let inst_end = cur + duration;
                                        if inst_end >= window_start && cur <= window_end {
                                            if parsed.until_epoch.map(|u| cur <= u).unwrap_or(true) {
                                                let (y, m, d, h, min, s) = epoch_secs_to_ymd_hms(cur);
                                                let (ey, em, ed, eh, emin, es) = epoch_secs_to_ymd_hms(inst_end);
                                                let (iso_start, iso_end) = if is_all_day {
                                                    (format!("{:04}-{:02}-{:02}", y, m, d), Some(format!("{:04}-{:02}-{:02}", y, m, d)))
                                                } else {
                                                    (format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", y, m, d, h, min, s),
                                                     Some(format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", ey, em, ed, eh, emin, es)))
                                                };
                                                events.push(GCalEvent {
                                                    id: format!("{}_{}", event_uid, cur),
                                                    title: title.clone(),
                                                    start: iso_start,
                                                    end: iso_end,
                                                    start_epoch: cur,
                                                    end_epoch: Some(inst_end),
                                                    is_all_day,
                                                    location: loc_opt.clone(),
                                                    description: desc_opt.clone(),
                                                    meet_url: meet_url.clone(),
                                                    status: status.clone(),
                                                });
                                            }
                                        }
                                        cur += step_secs;
                                    }
                                } else {
                                    for d_idx in win_d1.max(start_d)..=win_d2 {
                                        let wday = ((d_idx + 4).rem_euclid(7)) as u32;
                                        if parsed.byday.contains(&wday) {
                                            let inst_start = d_idx * 86400 + time_of_day;
                                            let inst_end = inst_start + duration;
                                            let week_diff = (d_idx - start_d).div_euclid(7);
                                            if week_diff.rem_euclid(parsed.interval as i64) == 0 && inst_start >= start_epoch {
                                                if inst_end >= window_start && inst_start <= window_end {
                                                    if parsed.until_epoch.map(|u| inst_start <= u).unwrap_or(true) {
                                                        let (y, m, d, h, min, s) = epoch_secs_to_ymd_hms(inst_start);
                                                        let (ey, em, ed, eh, emin, es) = epoch_secs_to_ymd_hms(inst_end);
                                                        let (iso_start, iso_end) = if is_all_day {
                                                            (format!("{:04}-{:02}-{:02}", y, m, d), Some(format!("{:04}-{:02}-{:02}", y, m, d)))
                                                        } else {
                                                            (format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", y, m, d, h, min, s),
                                                             Some(format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", ey, em, ed, eh, emin, es)))
                                                        };
                                                        events.push(GCalEvent {
                                                            id: format!("{}_{}", event_uid, inst_start),
                                                            title: title.clone(),
                                                            start: iso_start,
                                                            end: iso_end,
                                                            start_epoch: inst_start,
                                                            end_epoch: Some(inst_end),
                                                            is_all_day,
                                                            location: loc_opt.clone(),
                                                            description: desc_opt.clone(),
                                                            meet_url: meet_url.clone(),
                                                            status: status.clone(),
                                                        });
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            "DAILY" | _ => {
                                let step_secs = (parsed.interval as i64) * 86400;
                                let mut cur = start_epoch;
                                if cur < window_start - step_secs {
                                    let skip = (window_start - cur).div_euclid(step_secs);
                                    cur += skip * step_secs;
                                }
                                while cur <= window_end {
                                    let inst_end = cur + duration;
                                    if inst_end >= window_start && cur <= window_end {
                                        if parsed.until_epoch.map(|u| cur <= u).unwrap_or(true) {
                                            let (y, m, d, h, min, s) = epoch_secs_to_ymd_hms(cur);
                                            let (ey, em, ed, eh, emin, es) = epoch_secs_to_ymd_hms(inst_end);
                                            let (iso_start, iso_end) = if is_all_day {
                                                (format!("{:04}-{:02}-{:02}", y, m, d), Some(format!("{:04}-{:02}-{:02}", y, m, d)))
                                            } else {
                                                (format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", y, m, d, h, min, s),
                                                 Some(format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", ey, em, ed, eh, emin, es)))
                                            };
                                            events.push(GCalEvent {
                                                id: format!("{}_{}", event_uid, cur),
                                                title: title.clone(),
                                                start: iso_start,
                                                end: iso_end,
                                                start_epoch: cur,
                                                end_epoch: Some(inst_end),
                                                is_all_day,
                                                location: loc_opt.clone(),
                                                description: desc_opt.clone(),
                                                meet_url: meet_url.clone(),
                                                status: status.clone(),
                                            });
                                        }
                                    }
                                    cur += step_secs;
                                }
                            }
                        }
                    }
                }
            }
            in_event = false;
            continue;
        }
        if !in_event {
            continue;
        }

        let Some((key_part, val_part)) = line.split_once(':') else { continue };
        let key_name = key_part.split(';').next().unwrap_or(key_part).to_uppercase();
        let value = unescape_ical_value(val_part);

        match key_name.as_str() {
            "UID" => uid = value,
            "SUMMARY" => summary = value,
            "DTSTART" => dtstart = val_part.to_string(),
            "DTEND" => dtend = val_part.to_string(),
            "LOCATION" => location = value,
            "DESCRIPTION" => description = value,
            "URL" => url = value,
            "STATUS" => status = value,
            "RRULE" => rrule = val_part.to_string(),
            _ => {}
        }
    }

    events
}

pub fn parse_gcal_ical(raw: &str) -> Vec<GCalEvent> {
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0);
    parse_gcal_ical_window(raw, now - 86400, now + 35 * 86400)
}

async fn poll_gcal(app: AppHandle) {
    let Some(raw_url) = secrets::get("gcal-url") else { return };
    let url = raw_url.trim();
    if url.is_empty() { return; }

    let fetch_url = if url.starts_with("webcal://") {
        url.replacen("webcal://", "https://", 1)
    } else {
        url.to_string()
    };

    let response = client().get(&fetch_url).send().await;
    let Ok(response) = response else { return };
    if !response.status().is_success() {
        emit(&app, IntegrationUpdate {
            id: "integration_gcal",
            data: json!({}),
            error: Some(status_error(response.status().as_u16(), &crate::i18n::t("Calendar feed unavailable"))),
            event: None,
        });
        return;
    }

    let text = response.text().await.unwrap_or_default();
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0);

    // Keep upcoming or ongoing events (ended less than 15 mins ago or within next 30 days)
    let window_start = now - 900;
    let window_end = now + 30 * 86400;

    let mut events = parse_gcal_ical_window(&text, window_start, window_end);

    events.sort_by_key(|e| e.start_epoch);
    events.truncate(25);

    let mut reminder_event: Option<IntegrationEvent> = None;
    if let Some(next) = events.iter().find(|e| !e.is_all_day && e.start_epoch >= now - 60 && e.start_epoch <= now + 300) {
        let mut last_alert = LAST_GCAL_ALERT_UID.lock().unwrap();
        let already_alerted = last_alert.as_ref().map(|s| s == &next.id).unwrap_or(false);
        if !already_alerted {
            *last_alert = Some(next.id.clone());
            let mins = (next.start_epoch - now).max(0) / 60;
            let time_str = if mins == 0 {
                crate::i18n::t("Starting now")
            } else {
                crate::i18n::tf("in {m} min", &[("m", &mins.to_string())])
            };
            reminder_event = Some(IntegrationEvent {
                success: true,
                label: next.title.clone(),
                detail: Some(format!("📅 {time_str}")),
            });
        }
    }

    let next_event = events.first().cloned();

    emit(&app, IntegrationUpdate {
        id: "integration_gcal",
        data: json!({
            "events": events,
            "nextEvent": next_event,
            "total": events.len()
        }),
        error: None,
        event: reminder_event,
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn github_card_data_sends_only_what_is_known() {
        let mut cache = GitHubCache::default();
        assert_eq!(github_card_data(&cache), json!({}));
        cache.stats = Some((12, 3400));
        assert_eq!(github_card_data(&cache), json!({ "totalRepos": 12, "totalStars": 3400 }));
        cache.activity = Some(GitHubActivity { total: 5, weeks: vec![], fetched_at: 9 });
        let data = github_card_data(&cache);
        assert_eq!(data["totalRepos"], json!(12));
        assert_eq!(data["activity"], json!({ "total": 5, "weeks": [], "fetchedAt": 9 }));
        assert!(data.get("pulse").is_none());
    }

    #[test]
    fn gcal_ical_parsing_and_meet_links() {
        let sample = r#"BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Google Inc//Google Calendar 70.9054//EN
BEGIN:VEVENT
UID:test-123@google.com
SUMMARY:Project Kickoff Meeting
DTSTART:20261010T143000Z
DTEND:20261010T153000Z
LOCATION:Google Meet
DESCRIPTION:Join meeting at https://meet.google.com/abc-defg-hij
STATUS:CONFIRMED
END:VEVENT
BEGIN:VEVENT
UID:allday-456@google.com
SUMMARY:Team Offsite
DTSTART;VALUE=DATE:20261012
STATUS:CONFIRMED
END:VEVENT
END:VCALENDAR"#;

        let events = parse_gcal_ical(sample);
        assert_eq!(events.len(), 2);
        assert_eq!(events[0].id, "test-123@google.com");
        assert_eq!(events[0].title, "Project Kickoff Meeting");
        assert_eq!(events[0].is_all_day, false);
        assert_eq!(events[0].meet_url.as_deref(), Some("https://meet.google.com/abc-defg-hij"));
        assert_eq!(events[1].id, "allday-456@google.com");
        assert_eq!(events[1].title, "Team Offsite");
        assert_eq!(events[1].is_all_day, true);
    }

    #[test]
    fn gcal_rrule_expansion_and_window_filter() {
        let sample = r#"BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:anniversary@google.com
SUMMARY:2 years of togetherness...
DTSTART;VALUE=DATE:20241014
RRULE:FREQ=YEARLY
STATUS:CONFIRMED
END:VEVENT
BEGIN:VEVENT
UID:far-future@google.com
SUMMARY:Trip to Mars
DTSTART:20280101T000000Z
STATUS:CONFIRMED
END:VEVENT
BEGIN:VEVENT
UID:weekly-sync@google.com
SUMMARY:Weekly Standup
DTSTART:20261005T090000Z
DTEND:20261005T100000Z
RRULE:FREQ=WEEKLY;BYDAY=MO
STATUS:CONFIRMED
END:VEVENT
END:VCALENDAR"#;

        // Window: Oct 10, 2026 to Nov 10, 2026
        let win_start = ymd_hms_to_epoch_secs(2026, 10, 10, 0, 0, 0);
        let win_end = ymd_hms_to_epoch_secs(2026, 11, 10, 0, 0, 0);

        let mut events = parse_gcal_ical_window(sample, win_start, win_end);
        events.sort_by_key(|e| e.start_epoch);

        // Trip to Mars in 2028 is filtered out.
        // Anniversary from 2024 recurs on 2026-10-14.
        // Weekly standup on Mondays recurs on Oct 12, 19, 26, Nov 2, 9.
        assert!(!events.iter().any(|e| e.title == "Trip to Mars"));

        let anniversary = events.iter().find(|e| e.title == "2 years of togetherness...").expect("Anniversary expanded");
        assert_eq!(anniversary.start, "2026-10-14");
        assert_eq!(anniversary.is_all_day, true);

        let standups: Vec<_> = events.iter().filter(|e| e.title == "Weekly Standup").collect();
        assert_eq!(standups.len(), 5); // 12 Oct, 19 Oct, 26 Oct, 2 Nov, 9 Nov
        assert_eq!(standups[0].start, "2026-10-12T09:00:00Z");
    }
}
