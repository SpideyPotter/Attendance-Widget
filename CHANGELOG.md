## [Unreleased]

### Android
- Morning “today’s classes” local notification (opt-in in Account; default 7:00 AM)

# Changelog

All notable releases of BMU Attendance are listed here.

## [1.1.1] — 2026-08-08

- Release CI: create `build/` before staging the Android APK
- Release CI: ship unsigned macOS zip without requiring provisioning profiles

## [1.1.0] — 2026-08-08

### Android
- Weekly timetable with Today / Weekly / Attendance navigation
- User-editable venue overrides when Maitri leaves classroom blank
- Transparent overall-% home-screen widget
- Labels editing moved under Account; overlay back no longer quits the app
- Production hardening for peer distribution: filter PROJECT subjects from overall %, Glance/WorkManager R8 keep rules, safer credential storage (encrypted only), login only after a successful refresh, partial refresh when timetable fails, shared widget refresh lifecycle, backup excludes for local caches

### Shared / iOS
- Timetable models, date-range helper, and Maitri JSON client support (shared with tests)
- macOS app target scaffolding and packaging updates

## [1.0.1] — 2026-05

- Android release APK on version tags and CI assemble
- Align Android app versioning; allow attaching APKs to existing releases
- Fix R8 release build for Tink / encrypted preferences

## [1.0.0] — 2026-05-12

- Initial native macOS app packaging and GitHub release workflow
- Document native apps in the top-level README
