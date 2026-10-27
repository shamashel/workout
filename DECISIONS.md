# Spec decisions

Sections 2, 3, and 5 of the workout plan are the contract. Where the plan is ambiguous or two sentences disagree, the engine takes the more conservative reading and the tests lock it. This file is the record of those readings.

## Week 2 RIR “2–3”

Dumbbell week 2 is labeled **2–3**. The numeric gate stays **3**, the same as week 1.

- Too hard when average RIR is under `target − 1`, so under 2.
- An earned increase still needs average RIR of at least 2 (`max(1, target − 1)`).
- The target never rises inside a working block: week 1 gate 3, week 2 gate 3, week 3 gate 2.
- Trap-bar deadlift (B1) stays labeled **3** in week 2, matching its own column in the plan.
- Deload is a separate mode at **4+**, not a within-block raise.

A slot’s RIR floor only raises a prescribed target that would otherwise sit under the floor. It does not add a second “too hard” rule. Logged RIR can be below the floor; that is too hard only through the formula above.

## 10% miss drop

The miss paragraph says a 10% drop when set 1 is RIR 0. The trap-bar example logs RIR 1 / 0 / 0 and drops 135 lb to 120 lb, which is a 10% cut. **Any set at RIR 0** takes the 10% drop. Otherwise the second miss is 5%. Both cuts round **down** onto an available weight. 135 × 0.9 = 121.5 lands on 120 because the barbell list includes 5 lb steps (see rounding).

Miss 1 repeats the same weight and the same **prescribed** target reps, not the reps that were just logged. Miss 2 changes the weight only and resets `miss_count`. A success clears `miss_count`. If the cut would fall under the lightest available weight, the weight is held and the miss count still resets.

## Rounding and available weights

- Percentage cuts use the largest available weight at or under the raw value. If the raw value is under every plate, the lightest available weight is used.
- Deload load uses nearest available weight. A tie picks the **smaller** weight, so a deload never rounds up.
- Default dumbbells are 5–150 lb in 5 lb steps (2.5 lb steps when micro-plates are on).
- Default barbell weights are 45–545 lb in **5 lb** steps even when the increase increment is 10 lb. Micro-plates off means the increase is +10 lb, not that 120 lb is removed from the rack. That is what makes the worked example’s 120 lb loadable.
- Lat pulldown stack defaults to 10–200 lb in 5 lb steps, increment 5 lb.
- One session adds at most one increment. If that exact next weight is not in the available list, the engine holds. It does not jump to a heavier plate that happens to be listed.
- The weight used for a decision is the **lightest** set logged in the session.

## 10% rule and carries

If the next increment is more than 10% of the current weight (exactly 10% is allowed), the engine extends reps instead of loading. The extension is `rep_hi + 3`, capped at 15, and it is armed once per weight. Carries are included: the 15-rep cap is a rep cap, so a carry extends time by **15 seconds** (three 5-second steps) rather than shrinking a 45-second carry down to 15. When the sets are already at that extended cap, the next earned session adds the weight and resets targets to the bottom of the range.

## Too easy, plateau, ceiling

- Too easy (average RIR at least `target + 2`, every set in range, not every set at the top) may add one increment at most once per 14 days, and only when that jump is at most 10% and this is not the third unchanged session. Targets become `rep_lo + 1`. If the bump is blocked, the session falls through to ordinary in-range progression. Carries do not take a too-easy weight bump; they add time first.
- Three sessions with the same reps and the same weight show the recovery prompt and keep the weight. That prompt does **not** cancel an increase that was already earned at the top of the range. A too-easy bump is itself an increase, so the “weight stays” line is not shown on top of it.
- The micro-plate prompt compares the **full** step (5 lb dumbbell, 10 lb bar or machine when micro-plates are on) against 5% of the weight. It appears only when micro-plates are on and the configured increment is smaller than that full step. The next earned increase then uses 2.5 lb (dumbbell) or 5 lb (bar).
- Six working sessions with no heavier weight and no extra total reps prompt a swap and list the slot’s substitutes.
- Goblet squat and pause-goblet squat, on the heaviest dumbbell you own, with every set at the top for three sessions, prompt the ceiling (extend toward 15, then a pause or tempo second, then a step-up or split squat). The app does not invent a heavier dumbbell and does not swap the exercise by itself.

## Deload

- Working sets become `ceil(n_sets × 0.6)` (3 → 2). Carries use `min(2, n_sets)`.
- Loaded slots use about 90% of the working weight, nearest available, ties down. Targets are `rep_lo`, and the reason notes the range `rep_lo` to `rep_lo + 2`. RIR is 4+.
- Carries keep the same load. Time is about 70% of the top of the range, rounded to 5 seconds (farmer 45 s → 30 s, suitcase 30 s → 20 s).
- Logging a deload does not change working weight, targets, or `miss_count`. Misses reset when the deload week is completed at check-in.
- After week 3, the working decision stays the working plan (so the goblet example still says “next 12 / 12 / 12”). The deload for the following week is a second line. If week 3 earns an increase, the deload is 90% of the **new** working weight and the next block starts there. The goblet example does not increase in week 3, so its deload is 45 lb from 50 lb.
- The trap-bar miss example is a separate scenario from the week 1–3 progression. The deload row in that example is 90% of 135 lb, about 120 lb, not a deload of an already-reduced bar.

## Early deload and skip

Immediate triggers, applied as soon as they are known:

- Two or more slots with a second consecutive miss this week.
- Session readiness of 2 or lower on two sessions in the week. Readiness is collected on the finish dialog so this can fire; the weekly check-in still asks for it too.
- Any pain flag of 2 or 3 in the last 7 days.

At the weekly check-in, also:

- Performance score 4 (the week did not match last week).
- Weekly readiness of 2 or lower. The plan names both a weekly score and a two-session rule. Collecting both, and treating one low weekly score as enough, is the stricter reading.

An early deload sets the clock to week 4. A bad check-in at the end of a deload keeps another deload week and still resets misses. A clean deload check-in starts the next block at the pre-deload working weights.

Skip-deload is offered only when the setting is on, **each** of the last two weeks (not their average) has performance ≤ 2 and soreness ≤ 2, and there was no pain flag above 0. The clock does not move until the user answers. Skipping is logged and starts the next block.

## Pain

- Flag 1: the suggestion produced from this session does not increase load. Other rules still run. The block is consumed on that prescription, so a single mild flag does not keep blocking later weeks.
- Flag 2: no load increase for two sessions (this one and the next), a substitute prompt at `round_down(0.85 × weight)`, and an early deload. Another flag of 2 or more on that slot within the prior 14 days adds the doctor/physio prompt. The current session is not counted twice; the repository only passes earlier dates.
- Flag 3: state is unchanged, there is no suggestion, the session stops, and the screen shows the medical message with no coaching. The log is still stored, and an early deload is recorded for the next time the app opens. Unlogged slots are not treated as misses.
- Accepting a substitute sets the name, drops the weight to about 85% (rounded down), resets targets to the bottom of the range, clears the miss count, and blocks load increases for two sessions. The slot keeps its original RIR column, so a trap-bar substitute stays on the trap-bar schedule. That hinge is the more conservative one.

## Same-day trap bar and carries

If a slot that uses the trap-bar RIR column (B1, including after a substitute) increases load, a carry logged later the same day does not also increase load. Time can still extend. Day evaluation is in slot order so the deadlift is decided first.

## Carries

Reps are seconds. Load increases only when every set is at the top time, posture was marked clean, every grip RIR is at least 1, and the RIR gate passes. An untapped posture box is a miss. Otherwise, when every set is at least the bottom of the range, the next time is `min(last + 5 seconds, cap)`.

## Year 1 seeding

- C4 (DB step-up) is seeded at **2** sets, the low end of “2–3”.
- Optional bodyweight slots (A5, B5, C5) do not track load.
- Unilateral slots ask for the weaker side as one number.
- Dead hangs are not a slot.
- Only Year 1 progression runs. Years 2–4 are editable notes. Year 1 re-lock can change the name, rep range, and set count; a blank name keeps the current name. Changing those numbers does not wipe logs.

## Week 0 and history

Week 0 practice passes are counted (capped at 2) and are not fed to the engine. Starting weights are entered by hand; a weight of 0 means unset.

History “estimated reps” is the hardest set’s reps plus its RIR (a stand-in for reps to failure), plotted with the performed weight, a deload marker, and the pain flag.

## Overrides, backup, Health Connect

An override stores suggested weight and reps, chosen weight and reps, and the time. The next prescription uses the chosen numbers.

Backup is a user-triggered JSON file through the system picker. It is a version-1 dump of the local tables. Nothing is uploaded.

Health Connect writes an exercise session only: start, end, and type (strength, pool swim, or open-water swim). Sets and reps stay in the app. The only health permission requested is `WRITE_EXERCISE`. The manifest strips `INTERNET`. If Health Connect is missing, out of date, or the write is denied, the session remains in the app and the screen says so.

The safety line, shown on first launch and in settings, is exactly: “Not medical advice. Check with your doctor before ramping up.”

## Settings equipment

The settings screen edits dumbbells as a lightest weight, a heaviest weight, and a step, plus individual bells added or removed. The barbell is a bar, the plates on hand, and a heaviest load. The lat stack is a lightest weight, a heaviest weight, and a step. What gets stored, and what the engine reads, is still the expanded list of loads. The default bar (45 lb, plates 45 / 25 / 10 / 5 / 2.5, up to 545) is the same 5 lb grid as before, so a 10% cut from 135 still lands on 120. A saved list that is not a plate combination is kept as-is until the lifter chooses plates.

## Tooling

Compose BOM `2026.09.00` pulls Compose 1.12, which requires compileSdk 37 and Android Gradle Plugin 9.1. This app stays on compileSdk 36 (Android 16) and AGP 8.11.1, so it uses Compose BOM `2026.06.01`.

## Release signing

Android will only upgrade an install when the new APK is signed by the same certificate and its `versionCode` is higher. A debug keystore generated on each GitHub runner would fail that check, so release builds use one stable key.

The default key is `app/signing/trades-prep-debug.keystore`, alias `trades-prep`, password `trades-prep`. It is committed on purpose. The password is not a secret: anyone with the repo can sign an APK as this app. That matches a personal-use app with no accounts. The key is not used for anything else.

Optional repository secrets replace that key when all four are set: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. If only some are set, the build fails instead of silently mixing keys. Switching from the committed key to a private one (or back) changes the certificate, so the phone will not upgrade. Uninstall first, after exporting a backup.

Tagged releases set `versionCode` from `vMAJOR.MINOR.PATCH` as `MAJOR * 1000000 + MINOR * 1000 + PATCH` (`v0.1.0` is 1000, `v0.1.1` is 1001). A later tag therefore installs over an earlier one. Manual workflow builds stay at `versionCode` 1 so they do not block a later tag; they also will not install over a tag that is already on the phone.
