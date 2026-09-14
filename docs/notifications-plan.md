# Notifications plan

Local (on-device) notifications for BMU Attendance. No server push; everything is scheduled from cached attendance and timetable after a successful refresh.

## Goals

1. **Morning briefing** — first-of-day local notification listing today’s classes (Android).
2. **Class reminders** — alert before the next lecture so students can leave on time.
3. **Low-attendance alerts** — warn when a subject (or overall %) drops under a configurable threshold.
4. **User control** — opt-in, per-type toggles, lead time / threshold, and clear on logout.
5. **No portal spam** — reuse existing refresh + caches; never add a separate polling loop for notifications alone.

## Non-goals (v1)

- Remote / FCM / APNs push from a backend.
- Per-subject reminder mute lists (can follow later).
- Exact “don’t miss this class or you fall below 75%” academic-rule engine.
- macOS menu-bar / Focus-mode deep integration beyond standard `UNUserNotificationCenter`.
- Shipping Apple class reminders before timetable is persisted on iOS (see phases).

## Notification types

| Type | Trigger | Content | Default |
| --- | --- | --- | --- |
| **Morning briefing (shipped first)** | Local alarm at configured time (default **7:00**) | Title + list of today’s classes (or “no classes”) | Off until enabled in Account |
| Class reminder | `leadMinutes` before a cached session start | Subject label (+ room if known) | Off until user enables; lead **10** min |
| Low attendance (subject) | After refresh, subject % &lt; threshold and previously ≥ threshold (or first time under) | Course label + percentage | Off; threshold **70** (matches palette red) |
| Low attendance (overall) | Same edge detection on overall % | Overall % | Off; same threshold |

Use **edge detection** for attendance alerts (crossed below threshold), not “still below” on every refresh, so the existing 15‑minute background refresh does not spam.

## Platform reality

| Platform | Attendance cache | Timetable cache / UI | Background refresh | Class reminders ready? |
| --- | --- | --- | --- | --- |
| **Android** | Yes | Yes (`TimetableStore`, Today / Next) | `WorkManager` ~15 min | Yes — best v1 target |
| **iOS** | Yes | Models + `fetchTimetable` only; **not** in repo / UI | `BGAppRefreshTask` | After timetable persistence |
| **macOS** | Yes (shared UI) | Same Apple gap | Foreground / activation only | Same as iOS; expect weaker schedule freshness |

Android already has reminder-relevant helpers in `TimetableQueries` (`nextSession`, `minutesUntilStart`, venue-aware labels). Apple needs a port of that plus a `TimetableStore` in the App Group before class reminders make sense.

## Architecture

```text
Refresh success (app / widget / worker / BG task)
        │
        ├─► SnapshotStore / TimetableStore (unchanged)
        │
        └─► NotificationScheduler.resync(prefs, snapshot, timetable)
                 │
                 ├─ cancel stale pending IDs
                 ├─ schedule upcoming class locals (next N sessions / rest of week)
                 └─ maybe post low-attendance locals (deduped)
```

### Shared concepts (per platform store)

- `classRemindersEnabled: Bool`
- `classReminderLeadMinutes: Int` (options: 5 / 10 / 15 / 30)
- `lowAttendanceEnabled: Bool`
- `lowAttendanceThreshold: Int` (default 70; clamp 50…90)
- `lastAlertedSubjectKeys: Set` / overall flag for dedupe (reset when % recovers above threshold)

Mirror Android’s `ThemeStore` pattern (`NotificationPrefsStore`). On Apple, prefer App Group `UserDefaults` so a future widget extension can read prefs if needed.

### Android scheduling

- **Class reminders:** prefer `AlarmManager` (`setAndAllowWhileIdle` / exact when permitted) or one-shot `WorkManager` with a precise delay per session. Periodic 15‑minute work alone is too coarse for “10 minutes before.”
- Reschedule the upcoming window after every successful timetable save (`AttendanceRepository` refresh path and `RefreshWorker` success).
- Manifest: `POST_NOTIFICATIONS` (API 33+); request at first enable in Account. Add exact-alarm permission only if product insists on precise alarms on API 31+; otherwise document best-effort inexact + short-window reschedule from refresh.
- Notification channels: `class_reminders`, `attendance_alerts`.
- Clear pending + prefs side-effects in `clearCredentials` / `clearCaches`.

### Apple scheduling

- `UNUserNotificationCenter` calendar or time-interval triggers from cached sessions.
- Request authorization when the user turns a toggle on.
- Reschedule from `SettingsViewModel.performRefresh` and `BackgroundRefreshScheduler.handle`.
- macOS: same APIs; do not assume BG fetch keeps schedules fresh — reschedule on `.active`.

## Settings UI

Add a **Notifications** grouped section on Account (Android: after Appearance; iOS/macOS: after intro, before credentials).

Suggested controls:

1. Master copy: “Alerts stay on this device. They use your cached timetable and attendance.”
2. Toggle: Class reminders → stepper/picker for lead time (enabled only when on).
3. Toggle: Low attendance → threshold picker.
4. System permission status + “Open system settings” when denied.

No cards in the hero/home path; keep this inside Account only.

## Content rules

- Prefer **course labels** from `SubjectAliasStore` when set; else short subject name.
- Class body: `Starts at 9:00 AM` + room when venue override or portal room is useful (skip `-` / blank).
- Attendance body: `CSE3727 is at 68%` / `Overall attendance is at 72%`.
- Tap opens the app to Today (Android) or Home (Apple); deep-link to a specific subject is optional later.
- Quiet hours: **out of scope for v1** (can add later as a single local window).

## Deduping & edge cases

| Case | Behavior |
| --- | --- |
| Timetable refresh replaces the week | Cancel all class reminder IDs, reschedule from new cache |
| Session already started | Do not schedule |
| Lead time already passed | Skip or fire immediately only if &gt; 0 minutes remaining and user just enabled — prefer skip |
| No timetable cached | Class toggle stays on but schedules nothing until next successful week fetch |
| Subject recovers above threshold | Clear that subject from “already alerted” |
| `PROJECT` subjects | Exclude from overall and from subject alerts (same as UI %) |
| Logout / forget credentials | Cancel all pending notifications |
| Rate-limited refresh (no network) | Still allow reschedule from **existing** cache when prefs change |

## Implementation phases

### Phase 1 — Android morning briefing (implemented)

1. `NotificationPrefsStore` + Account toggle for “Today’s classes”
2. `TodayBriefingScheduler` / `TodayBriefingReceiver` (AlarmManager + boot reschedule)
3. Body built from cached timetable via `TimetableQueries.sessionsForDate`
4. Resync after refresh success and on logout cancel

### Phase 1b — Android foundation (remaining)

1. `NotificationPrefsStore` + Account UI toggles / pickers.
2. Notification channels + runtime permission gate.
3. `NotificationScheduler` hooked after successful refresh and on prefs change.
4. Class reminders for the cached week (cap pending count, e.g. next 20 sessions).
5. Low-attendance edge alerts from snapshot.
6. Unit tests for: lead-time fire times, threshold edge detection, PROJECT exclusion, cancel-on-clear.
7. Docs: short “Notifications” subsection in `Android/README.md`.

### Phase 2 — Apple attendance alerts (no timetable prerequisite)

1. Prefs + Account section (shared SwiftUI).
2. Low-attendance scheduling after snapshot save.
3. Align HomeView red threshold (**75**) with palette / alert default (**70**) when introducing the alert default — one product number.

### Phase 3 — Apple class reminders

1. Add `dateIso` (or equivalent) to `TimetableSession`; App Group `TimetableStore`.
2. Extend `AttendanceRepository` to fetch + persist timetable like Android (partial failure keeps prior week).
3. Port `TimetableQueries` into AttendanceCore (or shared helpers) + tests.
4. Schedule `UNUserNotification` class reminders; Today/Weekly UI can remain a later feature.

### Phase 4 — Polish (optional)

- Per-subject mute.
- “Only on class days” / quiet hours.
- Widget-triggered permission education.
- Attendance alert when a single absence would push under threshold (predictive).

## Test plan

- **Unit:** scheduler pure functions (given frozen `now`, sessions, prefs → expected pending requests / none).
- **Android instrumented / manual:** enable reminders → force refresh → verify pending alarms; deny permission → graceful UI; forget credentials → none pending.
- **iOS manual:** authorization prompt once; background refresh path still reschedules when the system runs BG fetch.
- **Regression:** refresh rate limit, widget update, and login success path unchanged when notifications are off.

## Rollout

- Ship behind normal release tags; feature defaults **off**.
- Changelog under Android / Shared as appropriate per phase.
- No new CI secrets; no backend.

## Open decisions (resolve before Phase 1 code)

1. **Exact alarms on Android:** exact (more reliable, more permission friction) vs inexact + refresh-driven reschedule (simpler). Recommendation: **inexact / `setAndAllowWhileIdle` first**; revisit if users report late reminders.
2. **Class reminder horizon:** rest of cached week vs “next class only.” Recommendation: **rest of cached week**, capped, so BG refresh gaps still cover tomorrow morning.
3. **Overall vs per-subject low alerts:** both toggles under one “Low attendance” master, or separate. Recommendation: **one master + alert on either overall or any subject**, with a single threshold.
