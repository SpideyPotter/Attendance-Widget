# Maitri HTTP Flow (Reference)

Reverse-engineered from the live portal during May 2026 with the standalone
`probe/` Kotlin app. This is the contract the Android client targets.

## Big picture

Maitri is a Spring Security web app where the **subject + attendance data is
loaded asynchronously via JSON endpoints**, not server-rendered. The legacy
Selenium scripts in this repo "work" only because Selenium reads
`driver.page_source`, which captures the DOM **after** JS has populated it.
For a native (non-browser) client we need to call the JSON endpoints directly,
which is faster and far more robust.

## Endpoints

| # | Method | URL | Purpose |
|---|--------|-----|---------|
| 1 | GET  | `https://maitri.bmu.edu.in/loginPage.htm` | 302s to `/login.htm;jsessionid=<id>`, sets `JSESSIONID` cookie. |
| 2 | POST | `https://maitri.bmu.edu.in/j_spring_security_check` | `application/x-www-form-urlencoded` body: `j_username=<full email>&j_password=<password>`. |
| 3 | GET  | `https://maitri.bmu.edu.in/stu_getTermsOfStudentForCourceFile.json` | List of all enrolled semesters with the IDs the next call needs. |
| 4 | GET  | `https://maitri.bmu.edu.in/stu_getSubjectOnChangeWithSemId1.json` | Subject + attendance JSON for one term. |

**No CSRF token, no captcha, no JS execution required.** The login page
literally serves `<input type="hidden" id="validateCaptcha" value="false"/>`.

## Step-by-step contract

### 1 — Seed session

```
GET https://maitri.bmu.edu.in/loginPage.htm
```

Response sets `JSESSIONID` (HttpOnly, Path=/). Carry this cookie through every
subsequent request. The redirect chain ends at `/login.htm;jsessionid=<id>` —
this is normal Spring URL-rewrite fallback.

### 2 — Authenticate

```
POST https://maitri.bmu.edu.in/j_spring_security_check
Content-Type: application/x-www-form-urlencoded
Cookie: JSESSIONID=...

j_username=<FULL EMAIL>&j_password=<password>
```

Critical: **`j_username` must be the full email** (`you@bmu.edu.in`), not the
bare local part. The bare username is silently rejected as
"Invalid User Name or Password.!!".

**Success / failure detection:**
- After redirects, the final URL **does not contain** `login.htm`
  (typically lands on `/home.htm`).
- Failure: final URL is `/login.htm?failure=true`.
- The HTML of a failed response also embeds:
  ```js
  window.gLoginStatus = { isError: true, message: `Invalid User Name or Password.!!`, ... }
  ```
  but URL-based detection is simpler and equally reliable.

### 3 — List enrolled terms

```
GET https://maitri.bmu.edu.in/stu_getTermsOfStudentForCourceFile.json
Cookie: JSESSIONID=...
Accept: application/json
```

Response: `Content-Type: text/plain;charset=ISO-8859-1` (yes, despite the
`.json` extension and JSON body). Body is a JSON array of term objects:

```json
[
  {
    "terms": "<option value=6 batchsemesterCapacity='1456' subjectwiseStudentIds='376297,...' subejctwiseBatchIds='20617,...'>VI</option>",
    "terms2": "<sha1 hash>",
    "terms2Name": "VI",
    "terms2Status": false,
    "teachingStartDate": "Jan 19, 2026",
    "teachingEndDate": "Jun 12, 2026",
    "batchsemesterCapacity": 1456,
    "semesterId": 6,
    "subjectwiseStudentIds": "376297,376329,376361,376393,376425,381435,381436,387918",
    "subejctwiseBatchIds": "20617,20633,20657,20681,20689,20889,20897,21660",
    "subjectStudentAndBatchIds": "..."
  },
  ...
]
```

The `terms` field contains rendered HTML for the dropdown — we ignore it and
read the structured fields directly.

**Picking the current term**: take the entry with the highest `semesterId`.
Fallback: filter where `today` ∈ (`teachingStartDate`, `teachingEndDate`).

Note the field name **`subejctwiseBatchIds`** — sic; that's a typo in the
upstream API, not in our code.

### 4 — Fetch attendance for a term

```
GET https://maitri.bmu.edu.in/stu_getSubjectOnChangeWithSemId1.json
       ?termId=<semesterId>
       &refreshData=0
       &subjectwisestudentids=<from term JSON, comma-sep>
       &subejctwiseBatchIds=<from term JSON, comma-sep>
       &batchsemestercapacity=<from term JSON, int>
Cookie: JSESSIONID=...
```

Response: JSON array, one entry per subject. Relevant fields:

| Field | Type | Notes |
|-------|------|-------|
| `subjectCode` | String | e.g. `CSE3727` |
| `subject` | String | Full course name (may contain HTML entities — `gemsDecode()` in JS, but Jsoup-style HTML decoding handles it) |
| `presentCount` | Number | Lectures attended |
| `absentCount` | Number | Lectures missed |
| `afterCapping` | Number | BMU-specific capped percentage; `0` when irrelevant |
| `subBatchId` | Number | Used to drill into per-class detail (out of scope for the widget) |
| `subjectCategory` | String | `"PROJECT"` rows are excluded by the JS UI; we mirror that |

`percentage = presentCount * 100 / (presentCount + absentCount)` (guard against
divide-by-zero when `total == 0`, e.g. for projects that haven't started).

## Cookie handling

A single `JSESSIONID` cookie carries the whole session. Persist it in an
OkHttp `CookieJar` for the duration of one fetch. We **do not** persist it
across app launches — the Android widget will re-login each refresh, using
credentials from `EncryptedSharedPreferences`. Keeping the session ephemeral
sidesteps the "session expired silently" failure mode.

## Headers we send

- `User-Agent`: a normal Chrome/Android UA. Maitri shows a "this is not Mozilla
  Firefox" banner otherwise but functionally accepts any UA.
- `Referer: https://maitri.bmu.edu.in/login.htm` on the auth POST (mimics the
  real browser form).
- `Accept: application/json, text/plain, */*` and
  `X-Requested-With: XMLHttpRequest` on the JSON endpoints — matches what
  jQuery's `$.ajax` sends.

## Failure modes

| Symptom | Likely cause | Action |
|---------|--------------|--------|
| Step 2 lands on `/login.htm?failure=true` | Wrong creds, or username wasn't the full email | Surface "Invalid credentials". The probe also warns when `j_username` lacks `@`. |
| Step 3 or 4 redirected to `/login.htm` | Session expired | Re-run from step 1. |
| Step 4 returns body with size 0 | Wrong HTTP verb (must be GET, not POST) or required param missing | Already encoded in the probe; double-check param names. |
| Empty terms list | Account has no enrollments yet | Show "No enrolled terms" in the widget. |
| Empty subjects list | Term has no subjects yet (registration not finalised) | Show "No subjects this term". |
| Network / TLS errors | Off-campus restrictions, or maitri is down | Show last-known-good attendance + "Network error" banner. |
