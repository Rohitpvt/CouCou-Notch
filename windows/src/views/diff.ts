// Diff & Live Code Inspector — port of DiffCardView / Live Inspector.
// Displays file diffs with line numbers, syntax highlighting, language badge,
// relative path, blinking cursor, and command/terminal inspector.

import { h, svg } from "./dom";
import { ICONS } from "./icons";
import { fileName, type DiffKind, type FileDiff } from "../core/diff";
import { N_, tl } from "../i18n/i18n";
import type { AgentTask } from "../core/state";

const STRINGS = {
  back: N_("Back"),
  open: N_("Open in VS Code"),
  tooLarge: N_("Diff too large"),
  noChanges: N_("No changes"),
};

const SYMBOLS: Record<DiffKind, string> = { added: "+", removed: "−", context: " " };

export interface DiffCardHooks {
  dismiss?(): void;
  /** ↗ — opens the file in the editor. */
  open(path: string): void;
}

export function detectLanguage(path: string): { label: string; color: string } {
  const clean = path.replace(/[\\/]+$/, "");
  const ext = clean.split(".").pop()?.toLowerCase() || "";
  switch (ext) {
    case "ts":
    case "tsx":
      return { label: "TS", color: "#3178c6" };
    case "js":
    case "jsx":
    case "mjs":
    case "cjs":
      return { label: "JS", color: "#f7df1e" };
    case "rs":
      return { label: "RS", color: "#dea584" };
    case "py":
      return { label: "PY", color: "#3572a5" };
    case "json":
      return { label: "JSON", color: "#cb3837" };
    case "html":
      return { label: "HTML", color: "#e34c26" };
    case "css":
    case "scss":
    case "less":
      return { label: "CSS", color: "#563d7c" };
    case "md":
    case "markdown":
      return { label: "MD", color: "#0891b2" };
    case "sh":
    case "bash":
    case "zsh":
    case "ps1":
    case "bat":
    case "cmd":
      return { label: "SH", color: "#4ade80" };
    case "go":
      return { label: "GO", color: "#00add8" };
    case "c":
      return { label: "C", color: "#64748b" };
    case "cpp":
    case "cc":
    case "cxx":
    case "hpp":
    case "h":
      return { label: "C++", color: "#f34b7d" };
    case "swift":
      return { label: "SWIFT", color: "#f05138" };
    case "java":
    case "kt":
      return { label: "JAVA", color: "#b07219" };
    case "sql":
      return { label: "SQL", color: "#e38c00" };
    case "yaml":
    case "yml":
    case "toml":
      return { label: "CONFIG", color: "#8b5cf6" };
    default:
      return { label: ext ? ext.toUpperCase().slice(0, 4) : "CODE", color: "#6366f1" };
  }
}

/** Pretty relative path for inspector display. */
export function formatRelativePath(path: string): string {
  const norm = path.replace(/\\/g, "/");
  const idx = norm.indexOf("/src/");
  if (idx >= 0) return norm.slice(idx + 1);
  const parts = norm.split("/").filter(Boolean);
  if (parts.length > 2) return parts.slice(-2).join("/");
  return norm;
}

/** Basic syntax coloring for code strings */
export function formatCodeHtml(text: string): string {
  let escaped = text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");

  // Keywords
  escaped = escaped.replace(
    /\b(import|export|from|function|return|const|let|var|if|else|for|while|class|type|interface|enum|async|await|pub|fn|struct|impl|use|mut|self|match|def|lambda)\b/g,
    '<span class="syn-kw">$1</span>',
  );
  // Strings
  escaped = escaped.replace(/(['"`])(.*?)\1/g, '<span class="syn-str">$1$2$1</span>');
  // Numbers
  escaped = escaped.replace(/\b(\d+(\.\d+)?)\b/g, '<span class="syn-num">$1</span>');
  return escaped;
}

/**
 * Builds the full Live Code & Tool Inspector shown in the right pane of Image 2.
 */
export function buildLiveCodeInspector(
  task: AgentTask,
  diff: FileDiff | null,
  activeStepText: string | null,
  hooks: DiffCardHooks,
): HTMLElement {
  const container = h("div", { class: "live-inspector" });

  // 1. Determine mode: Diff mode vs Terminal / Command mode vs File read preview
  let filePath = diff?.path || "";
  let isTerminal = false;
  let terminalCommands: string[] = [];

  const active = activeStepText || task.steps.at(-1) || "";
  if (!diff) {
    if (active.startsWith("Runs") || active.startsWith("Bash") || active.startsWith("run_command") || active.startsWith("PowerShell")) {
      isTerminal = true;
      const cmd = active.includes("·") ? active.split("·")[1].trim() : active;
      terminalCommands = [cmd];
      // Collect other recent run commands
      for (const s of task.steps.slice(-4)) {
        if (s !== active && (s.startsWith("Runs") || s.startsWith("Bash") || s.startsWith("run_command"))) {
          const c = s.includes("·") ? s.split("·")[1].trim() : s;
          if (c && !terminalCommands.includes(c)) terminalCommands.unshift(c);
        }
      }
    } else if (active.includes("·")) {
      filePath = active.split("·")[1].trim();
    }
  }

  // 2. Build Header
  const head = h("div", { class: "inspector-header" });
  if (isTerminal) {
    const badge = h("span", { class: "file-chip-badge", style: "background:#22c55e", text: "SH" });
    const name = h("span", { class: "file-chip-name", text: "terminal" });
    const dot = h("span", { class: "file-chip-dot", text: "•" });
    const pathLabel = h("span", { class: "inspector-path", text: task.sessionCwd ? formatRelativePath(task.sessionCwd) : task.name });
    head.append(badge, name, dot, pathLabel);
  } else {
    const file = filePath ? fileName(filePath) : "session.ts";
    const lang = detectLanguage(file);
    const badge = h("span", { class: "file-chip-badge", style: `background:${lang.color}`, text: lang.label });
    if (lang.label === "JS") badge.style.color = "#000000";
    const name = h("span", { class: "file-chip-name", text: file });
    const dot = h("span", { class: "file-chip-dot", text: "•" });
    const relPath = filePath ? formatRelativePath(filePath) : `src/${file}`;
    const pathLabel = h("span", { class: "inspector-path", text: relPath });
    const openBtn = h(
      "button",
      {
        class: "icon-btn",
        title: tl(STRINGS.open),
        onclick: (e) => {
          e.stopPropagation();
          if (filePath) hooks.open(filePath);
        },
      },
      svg(ICONS.arrowUpRight, 8),
    );
    head.append(badge, name, dot, pathLabel, openBtn);
  }
  container.append(head);

  // 3. Build Body
  const body = h("div", { class: "inspector-code-area" });

  if (isTerminal) {
    const termBox = h("div", { class: "terminal-inspector-body" });
    for (let i = 0; i < terminalCommands.length; i++) {
      const cmd = terminalCommands[i];
      const isLatest = i === terminalCommands.length - 1;
      const row = h(
        "div",
        { class: "terminal-line" },
        h("span", { class: "terminal-prompt", text: ">_" }),
        h("span", { class: "terminal-cmd", text: ` ${cmd}` }),
        isLatest && task.state === "working" ? h("span", { class: "terminal-block-cursor" }) : null,
      );
      termBox.append(row);
    }
    if (task.finalLine) {
      const outRow = h("div", { class: "terminal-line out", style: "color:#94a3b8;margin-top:6px" },
        h("span", { text: `✓ ${task.finalLine}` }),
      );
      termBox.append(outRow);
    }
    body.append(termBox);
  } else if (diff && diff.hunks.length > 0) {
    const lines = diff.hunks.flatMap((h) => h.lines);
    const frag = document.createDocumentFragment();

    let curLine = diff.hunks[0]?.newStart || diff.hunks[0]?.origStart || 10;
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const lineNo = line.newLine > 0 ? line.newLine : line.origLine > 0 ? line.origLine : curLine++;
      const row = h("div", { class: `diff-line-row ${line.kind}` });
      const linenoCol = h("span", { class: "diff-gutter-lineno", text: String(lineNo) });
      const symCol = h("span", { class: "diff-gutter-sym", text: SYMBOLS[line.kind] });
      const txtCol = h("span", { class: "diff-line-content" });
      txtCol.innerHTML = formatCodeHtml(line.text);

      // Add cursor to the latest added line if session is active
      if (line.kind === "added" && i === lines.length - 1 && task.state === "working") {
        txtCol.append(h("span", { class: "blinking-cursor" }));
      }

      row.append(linenoCol, symCol, txtCol);
      frag.append(row);
    }
    body.append(frag);
  } else if (diff?.tooLarge) {
    body.append(h("div", { class: "diff-note", text: tl(STRINGS.tooLarge) }));
  } else {
    // Fallback: render formatted placeholder / active file lines
    const sampleRows = h("div", { class: "diff-sample-rows" });
    const defaultLines = [
      { no: 10, kind: "context", text: "import { Item } from './types'" },
      { no: 11, kind: "context", text: "" },
      { no: 12, kind: "removed", text: "const TVA = 0.196" },
      { no: 12, kind: "added", text: "const TVA" },
      { no: 13, kind: "context", text: "" },
      { no: 14, kind: "context", text: "export function total(items: Item[]) {" },
      { no: 15, kind: "context", text: "  const sum = items.reduce((s, i) => s + i.price, 0)" },
      { no: 16, kind: "context", text: "  return sum * (1 + TVA)" },
      { no: 17, kind: "context", text: "}" },
    ];
    for (const item of defaultLines) {
      const row = h("div", { class: `diff-line-row ${item.kind}` });
      const linenoCol = h("span", { class: "diff-gutter-lineno", text: String(item.no) });
      const symCol = h("span", { class: "diff-gutter-sym", text: item.kind === "added" ? "+" : item.kind === "removed" ? "−" : " " });
      const txtCol = h("span", { class: "diff-line-content" });
      txtCol.innerHTML = formatCodeHtml(item.text);
      if (item.kind === "added" && task.state === "working") {
        txtCol.append(h("span", { class: "blinking-cursor" }));
      }
      row.append(linenoCol, symCol, txtCol);
      sampleRows.append(row);
    }
    body.append(sampleRows);
  }

  container.append(body);
  return container;
}

export function buildDiffCard(diff: FileDiff, hooks: DiffCardHooks): HTMLElement {
  const head = h(
    "div",
    { class: "diff-head" },
    hooks.dismiss
      ? h(
          "button",
          { class: "diff-back", title: tl(STRINGS.back), onclick: () => hooks.dismiss?.() },
          svg(ICONS.chevronLeft, 9, { stroke: 2.4 }),
          h("b", { text: fileName(diff.path) }),
        )
      : h("b", { text: fileName(diff.path) }),
    h("span", { class: "grow" }),
    diff.added > 0 ? h("span", { class: "tick-count plus", text: `+${diff.added}` }) : null,
    diff.removed > 0 ? h("span", { class: "tick-count minus", text: `−${diff.removed}` }) : null,
    h(
      "button",
      { class: "icon-btn", title: tl(STRINGS.open), onclick: () => hooks.open(diff.path) },
      svg(ICONS.arrowUpRight, 8),
    ),
  );

  const lines = diff.hunks.flatMap((hunk) => hunk.lines);
  let content: HTMLElement;
  if (diff.tooLarge) {
    content = h("div", { class: "diff-note", text: tl(STRINGS.tooLarge) });
  } else if (lines.length === 0) {
    content = h("div", { class: "diff-note", text: tl(STRINGS.noChanges) });
  } else {
    content = h("div", { class: "diff-lines" });
    const rows = document.createDocumentFragment();
    for (const line of lines) {
      rows.append(
        h(
          "div",
          { class: `diff-line ${line.kind}` },
          h("span", { class: "sym", text: SYMBOLS[line.kind] }),
          h("span", { class: "txt", text: line.text }),
        ),
      );
    }
    content.append(rows);
  }

  return h("div", { class: "diff-card" }, head, content);
}
