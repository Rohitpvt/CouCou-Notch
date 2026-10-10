// Settings → Agents: one section for every agent other than Claude Code, all
// going through the same steps — preview the exact diff, click to confirm, and
// only then does agents.rs back the file up and write it.

import { Bridge, type AgentHookStatus } from "../core/bridge";
import { clear, h } from "../views/dom";
import { renderDiff, statusDot } from "./parts";
import { t } from "../i18n/i18n";

/** In the current language (src/i18n); the settings window redraws on a change. */
const TEXT = {
  get title() { return t("Agents"); },
  get intro() {
    return t("Show other coding agents in the island. Cocoa adds its entries to each agent's own config: you see the exact change and where the backup goes before anything is written, and uninstalling removes only what Cocoa added.");
  },
  get none() { return t("Cocoa could not list the agents."); },
  get approvals() { return t("Sessions, and Allow / Deny from the island"); },
  get displayOnly() { return t("Sessions — approvals stay in the agent"); },
  get install() { return t("Install…"); },
  get reinstall() { return t("Reinstall…"); },
  get uninstall() { return t("Uninstall…"); },
  get relayMissing() { return t("The relay isn't installed yet. Restart Cocoa."); },
  previewInstall: (name: string) => t("This is exactly what changes for {name}. Nothing else is touched.", { name }),
  get previewRemove() { return t("This removes Cocoa's entries only. Everything else stays."); },
  backup: (to: string) => (to ? t("Backup → {path}", { path: to }) : t("No existing file — nothing to back up.")),
  get confirmInstall() { return t("Back up and write"); },
  get confirmRemove() { return t("Back up and remove"); },
  get cancel() { return t("Cancel"); },
  get back() { return t("Back"); },
  done: (backups: string, note: string) => {
    const saved = backups
      ? t("Done. Previous version saved as {paths}.", { paths: backups.split("\n").join(", ") })
      : t("Done.");
    return `${saved} ${note}`;
  },
  failed: (err: unknown) => t("Could not write: {error}", { error: errorText(err) }),
};

function errorText(err: unknown): string {
  return String(err).replace(/^Error:\s*/, "");
}

import type { Settings } from "../core/state";

function toggle(on: boolean, onChange: (v: boolean) => void): HTMLElement {
  const el = h("button", { class: on ? "switch on" : "switch", "aria-pressed": on });
  el.addEventListener("click", () => {
    const next = !el.classList.contains("on");
    el.classList.toggle("on", next);
    onChange(next);
  });
  return el;
}

export function agentsSection(
  list: AgentHookStatus[] | null,
  settings?: Settings,
  onSave?: () => void,
): HTMLElement {
  const blocks = h("div", { style: "display:flex;flex-direction:column;gap:14px" });
  if (!list || list.length === 0) {
    blocks.append(h("div", { class: "hint", text: TEXT.none }));
  }
  for (const status of list ?? []) blocks.append(agentBlock(status));

  const options: HTMLElement[] = [];
  if (settings && onSave) {
    options.push(
      h("div", { class: "row", style: "margin-top:12px" },
        h("label", { text: t("Live Code Diff Inspector") }),
        toggle(settings.enableLiveDiffs !== false, (v) => {
          settings.enableLiveDiffs = v;
          onSave();
        }),
        h("span", { class: "hint", text: t("Inspect file changes and additions as agents work") }),
      ),
      h("div", { class: "row" },
        h("label", { text: t("Auto-expand on activity") }),
        toggle(settings.autoExpandOnAgentTask !== false, (v) => {
          settings.autoExpandOnAgentTask = v;
          onSave();
        }),
        h("span", { class: "hint", text: t("Expand notch when coding agents perform actions") }),
      ),
    );
  }

  return h(
    "section",
    {},
    h("h2", {}, h("span", { text: TEXT.title })),
    h("div", { class: "hint", text: TEXT.intro }),
    blocks,
    ...options,
  );
}

function agentBlock(initial: AgentHookStatus): HTMLElement {
  let status = initial;
  const head = h("div", { class: "row" });
  const body = h("div", { style: "display:flex;flex-direction:column;gap:10px" });
  const block = h("div", { style: "display:flex;flex-direction:column;gap:8px" }, head, body);

  async function refresh() {
    const fresh = (await Bridge.agentHooksList())?.find((a) => a.id === status.id);
    if (fresh) status = fresh;
    draw();
  }

  function draw() {
    clear(head);
    clear(body);
    const install = h("button", {
      class: "primary",
      text: status.installed ? TEXT.reinstall : TEXT.install,
      onclick: () => void showPreview(true),
    });
    // A hook pointing at a relay that isn't there would break the agent's hooks.
    if (!status.hookReady) {
      install.disabled = true;
      install.title = TEXT.relayMissing;
    }
    head.append(
      h("div", { style: "display:flex;align-items:center;gap:8px;min-width:132px" },
        statusDot(status.installed),
        h("span", { style: "font-size:12.5px", text: status.name }),
      ),
      h("span", { class: "hint", text: status.approvals ? TEXT.approvals : TEXT.displayOnly }),
      h("span", { class: "spacer" }),
      install,
    );
    if (status.installed) {
      head.append(h("button", {
        class: "danger",
        text: TEXT.uninstall,
        onclick: () => void showPreview(false),
      }));
    }
    body.append(h("span", { class: "path", text: status.path }));
  }

  async function showPreview(install: boolean) {
    let plan;
    try {
      plan = await Bridge.agentHooksPreview(status.id, install);
    } catch (err) {
      // An unreadable or unexpected config stops here rather than being
      // treated as empty and written over.
      clear(body);
      body.append(
        h("div", { class: "notice err", text: errorText(err) }),
        h("div", { class: "row" }, h("button", { text: TEXT.back, onclick: draw })),
      );
      return;
    }
    clear(body);
    body.append(
      h("div", { class: "hint", text: install ? TEXT.previewInstall(status.name) : TEXT.previewRemove }),
      renderDiff(plan.diff),
      h("div", { class: "row" }, h("span", { class: "path", text: TEXT.backup(plan.backup) })),
    );
    const confirm = h("button", {
      class: install ? "primary" : "danger",
      text: install ? TEXT.confirmInstall : TEXT.confirmRemove,
    });
    confirm.addEventListener("click", async () => {
      confirm.disabled = true;
      try {
        const backups = await Bridge.agentHooksApply(status.id, install, plan.fingerprint);
        clear(body);
        body.append(h("div", { class: "notice ok", text: TEXT.done(backups, install ? status.note : "") }));
        window.setTimeout(() => void refresh(), 2600);
      } catch (err) {
        confirm.disabled = false;
        body.append(h("div", { class: "notice err", text: TEXT.failed(err) }));
      }
    });
    body.append(h("div", { class: "row" }, confirm, h("button", { text: TEXT.cancel, onclick: draw })));
  }

  draw();
  return block;
}
