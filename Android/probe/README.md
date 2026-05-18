# Maitri Probe

Tiny Kotlin/JVM Gradle app that proves the **OkHttp + JSON** approach works
end-to-end against Maitri before we commit it to the Android widget.

It does four HTTP calls:

1. `GET /loginPage.htm` — seeds `JSESSIONID`.
2. `POST /j_spring_security_check` — submits `j_username` (full email) +
   `j_password`. Auth-failure is detected via the redirect URL.
3. `GET /stu_getTermsOfStudentForCourceFile.json` — lists all enrolled
   semesters; we pick the one with the highest `semesterId`.
4. `GET /stu_getSubjectOnChangeWithSemId1.json?termId=…&...` — returns the
   attendance JSON for that term.

…then prints a clean table with per-subject percentages and an overall
total.

See [`../FLOW.md`](../FLOW.md) for the full HTTP contract.

## Prerequisites

You need **JDK 21** on your `PATH`. Easiest is to reuse Android Studio's
bundled JDK (the `run.sh` script already auto-detects it on `/Volumes/APPS/`
and `/Applications/`):

```bash
export JAVA_HOME="/Volumes/APPS/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

Alternatives if you don't have Studio handy: `brew install --cask temurin@21`
or `sdk install java 21.0.4-tem` via SDKMAN.

The Gradle wrapper (`gradlew`, `gradle/wrapper/`) is already checked in — no
bootstrap step needed.

## Run the probe

The fastest path:

```bash
cd Android/probe
MAITRI_USERNAME="you@bmu.edu.in" MAITRI_PASSWORD="yourpassword" ./run.sh
```

Or via Gradle directly (after exporting `JAVA_HOME` as above):

```bash
cd Android/probe
MAITRI_USERNAME="you@bmu.edu.in" MAITRI_PASSWORD="yourpassword" \
  ./gradlew run -q --console=plain
```

Expected output (numbers will differ):

```
→ GET login page (seed JSESSIONID)… ok (JSESSIONID=905D7750…)
→ POST /j_spring_security_check… ok (landed on https://maitri.bmu.edu.in/home.htm)
→ GET terms list… ok (6 term(s) found)
→ Current term: VI (semId=6, Jan 19, 2026 → Jun 12, 2026)
→ GET attendance for VI… ok (8 subject(s))

+---------+------------------------------------------+---------+--------+
| Code    | Subject                                  | Count   | %      |
+---------+------------------------------------------+---------+--------+
| CSE3727 | Artificial Intelligence in Electric Mob… | 45/46   | 97.83  |
| ...                                                                   |
+---------+------------------------------------------+---------+--------+
Overall: 227 / 258 = 87.98%
```

If anything fails, the probe prints which step broke and why — that's the
signal we'll use to decide if any tweaks are needed before scaffolding the
real Android app.

## What this validates (and what it doesn't)

✅ Validates:
- Plain HTTP login (no JS / no captcha / no CSRF) works.
- Spring Security session cookies survive across requests via OkHttp's CookieJar.
- The attendance HTML structure we documented in `FLOW.md` matches reality.
- Login-failure detection (URL-based) works for wrong credentials.

❌ Does **not** cover:
- Anything Android-specific (Glance widget, EncryptedSharedPreferences, WorkManager).
- Persisting credentials (we read from env vars on purpose — never commit creds).
- Retry/backoff for flaky networks (the real app will add this).

Once this prints a clean table, we'll move on to scaffolding the actual widget app.
