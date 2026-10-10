// Mochi Welfare & Wellness system: intelligent proactive comments for breaks,
// hydration, posture, late night coding, and wellness quotes.

import { State } from "./state";
import { N_, t } from "../i18n/i18n";
import type { BotEmoteName } from "./layout";

export type WelfareCategory = "break" | "hydrate" | "posture" | "night" | "morning" | "celebrate";

export interface WelfareItem {
  category: WelfareCategory;
  categoryLabel: string;
  quote: string;
  tip: string;
  emote: BotEmoteName;
  color: string;
}

export interface WelfarePrompt extends WelfareItem {
  id: string;
  timestamp: number;
}

export const WELFARE_QUOTES: readonly WelfareItem[] = [
  // ── Breaks & Rest ──
  {
    category: "break",
    categoryLabel: N_("Break Time"),
    quote: N_("Time for a breather!"),
    tip: N_("You've been working hard. Stretch your arms and give your eyes a quick rest."),
    emote: "happy",
    color: "#22c55e",
  },
  {
    category: "break",
    categoryLabel: N_("Break Time"),
    quote: N_("Have a break, it's been a long session!"),
    tip: N_("Step away from the screen for a few minutes. Your brain will thank you."),
    emote: "yawn",
    color: "#22c55e",
  },
  {
    category: "break",
    categoryLabel: N_("Break Time"),
    quote: N_("Coding break time!"),
    tip: N_("Stand up, stretch your legs, and take a couple of deep breaths."),
    emote: "wink",
    color: "#22c55e",
  },

  // ── Hydration ──
  {
    category: "hydrate",
    categoryLabel: N_("Hydration"),
    quote: N_("Hydration check!"),
    tip: N_("Grab a fresh glass of water. Stay refreshed while you build."),
    emote: "love",
    color: "#38bdf8",
  },
  {
    category: "hydrate",
    categoryLabel: N_("Hydration"),
    quote: N_("Time to drink some water!"),
    tip: N_("A quick sip of water keeps your focus sharp and your energy high."),
    emote: "happy",
    color: "#38bdf8",
  },

  // ── Posture & Eye Care ──
  {
    category: "posture",
    categoryLabel: N_("Posture & Eyes"),
    quote: N_("Posture check!"),
    tip: N_("Roll your shoulders back, straighten your spine, and relax your neck."),
    emote: "proud",
    color: "#a855f7",
  },
  {
    category: "posture",
    categoryLabel: N_("Posture & Eyes"),
    quote: N_("20-20-20 eye rest!"),
    tip: N_("Look at an object 20 feet away for 20 seconds to ease eye strain."),
    emote: "wink",
    color: "#a855f7",
  },

  // ── Late Night ──
  {
    category: "night",
    categoryLabel: N_("Night Owl"),
    quote: N_("Burning the midnight oil?"),
    tip: N_("It's getting late! Your code will still be here tomorrow — get some rest soon."),
    emote: "yawn",
    color: "#f59e0b",
  },
  {
    category: "night",
    categoryLabel: N_("Night Owl"),
    quote: N_("Time to wind down!"),
    tip: N_("Great progress tonight. Remember that good sleep makes great code."),
    emote: "yawn",
    color: "#f59e0b",
  },

  // ── Early Morning ──
  {
    category: "morning",
    categoryLabel: N_("Good Morning"),
    quote: N_("Good morning, builder!"),
    tip: N_("Ready to create something amazing today? Wishing you smooth coding."),
    emote: "happy",
    color: "#eab308",
  },
  {
    category: "morning",
    categoryLabel: N_("Good Morning"),
    quote: N_("Rise and shine!"),
    tip: N_("Fresh morning, fresh ideas. Let's make today productive and fun."),
    emote: "proud",
    color: "#eab308",
  },

  // ── Task Completion / Celebration ──
  {
    category: "celebrate",
    categoryLabel: N_("Well Done"),
    quote: N_("Mission accomplished!"),
    tip: N_("Great work wrapping that up! Take a moment to enjoy the win."),
    emote: "proud",
    color: "#ec4899",
  },
];

export class WelfareManager {
  private lastPromptTime = Date.now();
  private snoozeUntil = 0;
  private continuousActiveMs = 0;
  private lastActivityCheck = Date.now();
  private lastQuoteIndex = -1;
  private lastCategory: WelfareCategory | null = null;
  private morningGreetedDate: string | null = null;

  snooze(minutes = 15, now = Date.now()) {
    this.snoozeUntil = now + minutes * 60 * 1000;
  }

  recordActivity(now = Date.now()) {
    const elapsed = now - this.lastActivityCheck;
    this.lastActivityCheck = now;
    // If idle for more than 5 minutes, reset continuous active streak
    if (elapsed > 5 * 60 * 1000) {
      this.continuousActiveMs = 0;
    } else {
      this.continuousActiveMs += elapsed;
    }
  }

  createPrompt(category?: WelfareCategory, now = Date.now()): WelfarePrompt {
    const quote = this.pickQuote(category);
    this.lastPromptTime = now;
    return {
      id: `welfare-${now}`,
      category: quote.category,
      categoryLabel: t(quote.categoryLabel),
      quote: t(quote.quote),
      tip: t(quote.tip),
      emote: quote.emote,
      color: quote.color,
      timestamp: now,
    };
  }

  pickQuote(requestedCategory?: WelfareCategory): WelfareItem {
    let pool = requestedCategory
      ? WELFARE_QUOTES.filter((q) => q.category === requestedCategory)
      : WELFARE_QUOTES;
    if (pool.length === 0) pool = WELFARE_QUOTES;

    let nextIdx = Math.floor(Math.random() * pool.length);
    if (pool.length > 1 && nextIdx === this.lastQuoteIndex) {
      nextIdx = (nextIdx + 1) % pool.length;
    }
    this.lastQuoteIndex = nextIdx;
    const chosen = pool[nextIdx];
    this.lastCategory = chosen.category;
    return chosen;
  }

  check(now = Date.now()): WelfarePrompt | null {
    if (!State.settings.welfareReminders) return null;
    if (now < this.snoozeUntil) return null;
    if (State.pendingApproval || State.view === "prompt") return null;

    this.recordActivity(now);

    const intervalMs = Math.max(15, State.settings.welfareIntervalMinutes || 45) * 60 * 1000;
    const timeSinceLastPrompt = now - this.lastPromptTime;

    if (timeSinceLastPrompt < intervalMs) return null;

    // 1. Long continuous active session -> Break reminder
    if (State.settings.welfareBreakReminders && this.continuousActiveMs >= intervalMs) {
      this.continuousActiveMs = 0;
      return this.createPrompt("break", now);
    }

    const currentHour = new Date(now).getHours();
    const todayStr = new Date(now).toDateString();

    // 2. Late night check (23:00 - 05:00)
    if (State.settings.welfareNightOwlAlerts && (currentHour >= 23 || currentHour < 5)) {
      if (this.lastCategory !== "night") {
        return this.createPrompt("night", now);
      }
    }

    // 3. Early morning check (06:00 - 09:30) once per day
    if (currentHour >= 6 && currentHour <= 9 && this.morningGreetedDate !== todayStr) {
      this.morningGreetedDate = todayStr;
      return this.createPrompt("morning", now);
    }

    // 4. Regular rotation between enabled welfare categories
    const candidates: WelfareCategory[] = [];
    if (State.settings.welfareBreakReminders) candidates.push("break");
    if (State.settings.welfareHydrationReminders) candidates.push("hydrate");
    if (State.settings.welfarePostureReminders) candidates.push("posture");

    if (candidates.length === 0) return null;

    const available = candidates.filter((c) => c !== this.lastCategory);
    const chosenCategory = available.length > 0
      ? available[Math.floor(Math.random() * available.length)]
      : candidates[Math.floor(Math.random() * candidates.length)];

    return this.createPrompt(chosenCategory, now);
  }
}

export const welfareManager = new WelfareManager();
