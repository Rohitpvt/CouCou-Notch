// Tests for Mochi Welfare & Wellness system (src/core/welfare.ts)

import { test } from "node:test";
import assert from "node:assert/strict";
import { State, DEFAULT_SETTINGS } from "../src/core/state.ts";
import { WelfareManager, WELFARE_QUOTES, welfareManager } from "../src/core/welfare.ts";

test("WELFARE_QUOTES catalog contains valid items across all categories", () => {
  assert.ok(WELFARE_QUOTES.length > 5, "Catalog should have ample quotes");
  const categories = new Set(WELFARE_QUOTES.map((q) => q.category));
  assert.ok(categories.has("break"), "Should have break quotes");
  assert.ok(categories.has("hydrate"), "Should have hydrate quotes");
  assert.ok(categories.has("posture"), "Should have posture quotes");
  assert.ok(categories.has("night"), "Should have night quotes");
  assert.ok(categories.has("morning"), "Should have morning quotes");
  assert.ok(categories.has("celebrate"), "Should have celebrate quotes");

  for (const item of WELFARE_QUOTES) {
    assert.ok(typeof item.quote === "string" && item.quote.length > 0, "Item has quote");
    assert.ok(typeof item.tip === "string" && item.tip.length > 0, "Item has tip");
    assert.ok(typeof item.categoryLabel === "string" && item.categoryLabel.length > 0, "Item has categoryLabel");
    assert.ok(typeof item.color === "string" && item.color.startsWith("#"), "Item has hex color");
    assert.ok(typeof item.emote === "string" && item.emote.length > 0, "Item has emote");
  }
});

test("createPrompt returns properly formatted prompt with id and timestamp", () => {
  const manager = new WelfareManager();
  const prompt = manager.createPrompt("hydrate");
  assert.ok(prompt.id.startsWith("welfare-"), "Prompt has welfare prefix id");
  assert.equal(prompt.category, "hydrate");
  assert.ok(prompt.quote.length > 0);
  assert.ok(prompt.tip.length > 0);
  assert.ok(prompt.timestamp > 0);
});

test("snooze suppresses welfare check until snooze period has elapsed", () => {
  const manager = new WelfareManager();
  State.settings = {
    ...DEFAULT_SETTINGS,
    welfareReminders: true,
    welfareIntervalMinutes: 20,
    welfareBreakReminders: true,
    welfareHydrationReminders: true,
    welfarePostureReminders: true,
  };

  const baseTime = new Date(2026, 9, 10, 14, 0, 0).getTime();
  // Initialize last prompt time into the past
  manager["lastPromptTime"] = baseTime - 30 * 60 * 1000;
  manager["lastActivityCheck"] = baseTime;

  // Snooze for 15 minutes
  manager.snooze(15, baseTime);

  // Check 5 minutes later (still snoozed)
  const duringSnooze = manager.check(baseTime + 5 * 60 * 1000);
  assert.equal(duringSnooze, null, "Should return null during snooze");

  // Check 16 minutes later (snooze expired)
  const afterSnooze = manager.check(baseTime + 16 * 60 * 1000);
  assert.ok(afterSnooze !== null, "Should trigger welfare prompt after snooze expires");
});

test("check respects welfareReminders disabled toggle", () => {
  const manager = new WelfareManager();
  State.settings = {
    ...DEFAULT_SETTINGS,
    welfareReminders: false,
  };
  manager["lastPromptTime"] = Date.now() - 60 * 60 * 1000;
  const prompt = manager.check(Date.now());
  assert.equal(prompt, null, "Should return null when welfareReminders is false");
});

test("check suppresses welfare prompts during active approvals or modal states", () => {
  const manager = new WelfareManager();
  State.settings = {
    ...DEFAULT_SETTINGS,
    welfareReminders: true,
    welfareIntervalMinutes: 20,
  };
  manager["lastPromptTime"] = Date.now() - 60 * 60 * 1000;

  // Simulate pending approval
  State.pendingApproval = { requestId: "req-1", sessionId: "s-1", pillId: "agent_antigravity", tool: "bash", command: "npm test" };
  assert.equal(manager.check(Date.now()), null, "Suppressed during pending approval");
  State.pendingApproval = null;

  // Simulate prompt view open
  State.view = "prompt";
  assert.equal(manager.check(Date.now()), null, "Suppressed during prompt view");
  State.view = "overview";
});

test("check detects continuous active session and triggers break reminder", () => {
  const manager = new WelfareManager();
  State.settings = {
    ...DEFAULT_SETTINGS,
    welfareReminders: true,
    welfareIntervalMinutes: 30,
    welfareBreakReminders: true,
  };

  const dayTime = new Date(2026, 9, 10, 14, 30, 0).getTime();
  manager["lastPromptTime"] = dayTime - 35 * 60 * 1000;
  manager["continuousActiveMs"] = 35 * 60 * 1000; // 35 minutes continuous active time
  manager["lastActivityCheck"] = dayTime;

  const prompt = manager.check(dayTime);
  assert.ok(prompt !== null, "Prompt should be generated");
  assert.equal(prompt.category, "break", "Should prioritize break prompt for long continuous session");
});

test("check detects late night hours and triggers night owl alert", () => {
  const manager = new WelfareManager();
  State.settings = {
    ...DEFAULT_SETTINGS,
    welfareReminders: true,
    welfareIntervalMinutes: 20,
    welfareNightOwlAlerts: true,
  };

  // Create date at 23:30 (11:30 PM)
  const lateNightDate = new Date(2026, 9, 10, 23, 30, 0);
  const now = lateNightDate.getTime();
  manager["lastPromptTime"] = now - 30 * 60 * 1000;
  manager["lastActivityCheck"] = now;

  const prompt = manager.check(now);
  assert.ok(prompt !== null, "Prompt should be generated");
  assert.equal(prompt.category, "night", "Should trigger night alert late at night");
});

test("singleton welfareManager instance is exported and functional", () => {
  assert.ok(welfareManager instanceof WelfareManager);
  assert.ok(typeof welfareManager.snooze === "function");
  assert.ok(typeof welfareManager.check === "function");
});
