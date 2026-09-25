/**
 * Minimal asserts for dvr-timeline helpers.
 * Run: node features/web/src/main/assets/web/js/ui/dvr-timeline.test.mjs
 */
import {
  dayBounds,
  dayKeyFromMs,
  timelineRangeForDay,
  isLiveEdgeWall,
  LIVE_EDGE_TIP_MS,
  availableTimelineDays,
} from "./dvr-timeline.js";

function assert(cond, msg) {
  if (!cond) throw new Error(msg || "assert failed");
}

const b = dayBounds("2026-03-15");
assert(b.endFull - b.start === 86_400_000, "day is 24h");
assert(dayKeyFromMs(b.start) === "2026-03-15", "dayKey matches start");
assert(b.scrubMax === b.endFull - 1, "historical scrubMax");

const segs = [
  { startUtcMs: b.start + 3_600_000, endUtcMs: b.start + 7_200_000 },
];
const r = timelineRangeForDay("2026-03-15", { segments: segs }, b.start + 10_000_000);
assert(r.segs.length === 1, "filters segments in day");
assert(r.isToday === false, "historical day");

const days = availableTimelineDays(segs);
assert(days.indexOf("2026-03-15") >= 0, "day listed");
assert(days[0] === dayKeyFromMs(Date.now()) || days.includes(dayKeyFromMs(Date.now())), "today included");

const today = dayKeyFromMs(Date.now());
const tr = timelineRangeForDay(today, { segments: segs }, Date.now());
assert(tr.isToday === true, "today flag");
assert(isLiveEdgeWall(tr.scrubMax, tr, LIVE_EDGE_TIP_MS), "live tip");
assert(!isLiveEdgeWall(tr.start, tr, LIVE_EDGE_TIP_MS), "day start not live");

console.log("dvr-timeline tests ok");
