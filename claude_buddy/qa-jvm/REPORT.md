# qa-jvm report
Run: `./run.sh` (kotlinc 2.4.0 embeddable from ~/.gradle, android-36 stubs). Result: 517 assertions, 0 failed (`PROBE=1 ./run.sh` prints ad-hoc probes).

## Covered
- QuickIntent (ru/uz/en): flashlight, volume (dir/percent/max/mute), media, alarms (absolute, am/pm, 12am/12pm, midnight wrap, relative, rollover, next-occurrence), timers, number words 0..99 in 3 languages, open app, call, nav, quiet, move/size, time/date/battery, ~50 negatives, normalize().
- Contacts: NameText norm/key/lev, Cyrillic<->Latin, family words, typos, ties, starred/times bonus, dedupe, number helpers.
- AppResolver: aliases/labels/typos/not-installed on a fake app list.

## Fixed
- QuickIntent: "alarm 13 pm" produced an alarm at 13:00; now null.
- Refactors for testability (no behaviour change): `Contacts.rank(rows, spoken, max)` and `AppResolver.resolveIn(apps, spoken)` extracted as internal pure functions; Row/AppEntry made internal.

## Remaining (minor)
- "call 5" / "открой 5 ютуб" yield call to "5" / open_app "5 ютуб" (single-digit call target not rejected). Cloud/Actions should tolerate.

## Bugs in other files
- None found (Tr.kt only compiled, not tested).
