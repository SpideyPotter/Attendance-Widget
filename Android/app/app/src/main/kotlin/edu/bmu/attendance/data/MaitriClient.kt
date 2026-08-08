package edu.bmu.attendance.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The single source of truth for talking to Maitri.
 *
 * Performs the login → terms → attendance flow described in `Android/FLOW.md`
 * and returns a fully-populated [AttendanceSnapshot]. Each call is
 * self-contained: it logs in fresh, fetches, and lets the cookie jar GC. We
 * deliberately do not persist the `JSESSIONID` across calls — that lets us
 * recover from "session expired" failures simply by calling [fetchAttendance]
 * again, with no tricky retry logic.
 *
 * All public functions are `suspend` and dispatch network I/O on
 * [Dispatchers.IO].
 */
class MaitriClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {
    /**
     * Logs in and pulls the latest [AttendanceSnapshot] for the term with the
     * highest `semesterId` (i.e. the most recently enrolled).
     *
     * Throws a [MaitriError] subclass on any expected failure; rethrows
     * unexpected exceptions as [MaitriError.Network] / [MaitriError.Portal].
     */
    suspend fun fetchAttendance(creds: Credentials): AttendanceSnapshot =
        withContext(Dispatchers.IO) {
            withAuthenticatedClient(creds) { client, current ->
                buildAttendanceSnapshot(client, current)
            }
        }

    /**
     * Logs in and pulls the student timetable for [startDate]…[endDate]
     * (portal labels like `Aug 2, 2026`). Defaults to the current Sun–Sat week.
     */
    suspend fun fetchTimetable(
        creds: Credentials,
        startDate: String? = null,
        endDate: String? = null,
    ): TimetableSnapshot =
        withContext(Dispatchers.IO) {
            val range = TimetableDateRange.currentWeek(startLabel = startDate, endLabel = endDate)
            withAuthenticatedClient(creds) { client, current ->
                buildTimetableSnapshot(client, current, range)
            }
        }

    /**
     * One login, then attendance + current-week timetable. Timetable failure
     * does not fail the whole call — attendance still returns and timetable
     * is null so callers can keep the previous cached week.
     */
    suspend fun fetchAttendanceAndTimetable(creds: Credentials): Pair<AttendanceSnapshot, TimetableSnapshot?> =
        withContext(Dispatchers.IO) {
            val range = TimetableDateRange.currentWeek()
            withAuthenticatedClient(creds) { client, current ->
                val attendance = buildAttendanceSnapshot(client, current)
                val timetable = runCatching {
                    buildTimetableSnapshot(client, current, range)
                }.getOrNull()
                attendance to timetable
            }
        }

    private fun buildAttendanceSnapshot(client: OkHttpClient, current: Term): AttendanceSnapshot {
        val subjects = fetchSubjects(client, current)
        return AttendanceSnapshot(
            termName = current.name,
            termSemesterId = current.semesterId,
            subjects = subjects,
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun buildTimetableSnapshot(
        client: OkHttpClient,
        current: Term,
        range: TimetableDateRange,
    ): TimetableSnapshot {
        val sessions = fetchTimetableSessions(client, current, range.startLabel, range.endLabel)
        return TimetableSnapshot(
            termName = current.name,
            termSemesterId = current.semesterId,
            startDate = range.startLabel,
            endDate = range.endLabel,
            sessions = sessions,
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun <T> withAuthenticatedClient(
        creds: Credentials,
        work: (OkHttpClient, Term) -> T,
    ): T {
        if (!creds.username.contains('@')) throw MaitriError.UsernameNotEmail
        if (creds.password.isBlank()) throw MaitriError.InvalidCredentials

        // Each call uses its own ephemeral cookie jar.
        val callJar = InMemoryCookieJar()
        val client = httpClient.newBuilder().cookieJar(callJar).build()

        try {
            seedSession(client)
            authenticate(client, creds)
            val terms = fetchTerms(client)
            val current = terms.maxByOrNull { it.semesterId } ?: throw MaitriError.NoTerms
            return work(client, current)
        } catch (e: MaitriError) {
            throw e
        } catch (e: IOException) {
            throw MaitriError.Network(e)
        } catch (e: org.json.JSONException) {
            throw MaitriError.Portal(e.message ?: "Portal returned invalid JSON")
        } catch (e: IllegalStateException) {
            throw MaitriError.Portal(e.message ?: "Portal returned unexpected data")
        } catch (e: Exception) {
            throw MaitriError.Portal(e.message ?: e::class.java.simpleName)
        }
    }

    // ── Internal steps ──────────────────────────────────────────────────────

    private fun seedSession(client: OkHttpClient) {
        client.newCall(get(LOGIN_PAGE_URL)).execute().use { resp ->
            check(resp.isSuccessful) { "Login page returned HTTP ${resp.code}" }
        }
    }

    private fun authenticate(client: OkHttpClient, creds: Credentials) {
        val body = FormBody.Builder()
            .add("j_username", creds.username)
            .add("j_password", creds.password)
            .build()
        val request = Request.Builder()
            .url(LOGIN_CHECK_URL)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$BASE/login.htm")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            check(resp.isSuccessful) {
                "Login POST returned HTTP ${resp.code} (final URL: $finalUrl)"
            }
            if (finalUrl.contains("login.htm", ignoreCase = true)) {
                throw MaitriError.InvalidCredentials
            }
        }
    }

    private fun fetchTerms(client: OkHttpClient): List<Term> {
        val json = fetchJson(client, TERMS_URL.toHttpUrl())
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Term(
                semesterId = o.getInt("semesterId"),
                name = o.optString("terms2Name", "Sem ${o.getInt("semesterId")}"),
                startDate = o.optString("teachingStartDate", "?"),
                endDate = o.optString("teachingEndDate", "?"),
                subjectwiseStudentIds = o.optString("subjectwiseStudentIds", ""),
                subejctwiseBatchIds = o.optString("subejctwiseBatchIds", ""),
                batchsemesterCapacity = o.optInt("batchsemesterCapacity", 0),
            )
        }
    }

    private fun fetchSubjects(client: OkHttpClient, term: Term): List<Subject> {
        val url = SUBJECTS_URL.toHttpUrl().newBuilder().apply {
            addQueryParameter("termId", term.semesterId.toString())
            addQueryParameter("refreshData", "0")
            addQueryParameter("subjectwisestudentids", term.subjectwiseStudentIds)
            addQueryParameter("subejctwiseBatchIds", term.subejctwiseBatchIds)
            addQueryParameter("batchsemestercapacity", term.batchsemesterCapacity.toString())
        }.build()
        val json = fetchJson(client, url)
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("subjectCategory") == "PROJECT") continue
                add(
                    Subject(
                        code = o.optString("subjectCode", "?"),
                        name = decodeHtmlEntities(o.optString("subject", "?")),
                        present = o.optInt("presentCount", 0),
                        absent = o.optInt("absentCount", 0),
                        afterCapping = o.optDouble("afterCapping", 0.0),
                    ),
                )
            }
        }
    }

    private fun fetchTimetableSessions(
        client: OkHttpClient,
        term: Term,
        startDate: String,
        endDate: String,
    ): List<TimetableSession> {
        val url = TIMETABLE_URL.toHttpUrl().newBuilder().apply {
            addQueryParameter("startDate", startDate)
            addQueryParameter("endDate", endDate)
            addQueryParameter("termId", term.semesterId.toString())
        }.build()
        val json = fetchJson(client, url)
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            TimetableSession(
                lectureDate = o.optString("lectureDate", "").trim(),
                dateIso = o.optString("lectureDateWithoutFormat", ""),
                lectureDay = o.optString("lectureDay", ""),
                startTime = o.optString("lectureStartTime", ""),
                endTime = o.optString("lectureEndTime", ""),
                // Upstream typo — keep reading `sessoionNo`.
                sessionNo = o.opt("sessoionNo")?.toString()?.takeIf { it != "null" }.orEmpty(),
                subjectName = decodeHtmlEntities(o.optString("subjectName", "")),
                topic = decodeUriComponent(o.optString("chapterName", "-")),
                classRoom = o.optString("classRoom", "-"),
                facultyName = collapseWhitespace(o.optString("facultyName", "")),
                description = o.optString("guestLecDescription", "-"),
            )
        }
    }

    private fun collapseWhitespace(value: String): String =
        value.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    private fun decodeUriComponent(value: String): String {
        var current = value
        repeat(2) {
            val decoded = runCatching {
                java.net.URLDecoder.decode(current, Charsets.UTF_8.name())
            }.getOrDefault(current)
            if (decoded == current) return@repeat
            current = decoded
        }
        val normalized = decodeHtmlEntities(current)
        return normalized.ifBlank { "-" }
    }

    private fun fetchJson(client: OkHttpClient, url: HttpUrl): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
        return client.newCall(request).execute().use { resp ->
            check(resp.isSuccessful) { "GET ${url.encodedPath} returned HTTP ${resp.code}" }
            val finalUrl = resp.request.url.toString()
            check(!finalUrl.contains("login.htm", ignoreCase = true)) {
                "Session was rejected when calling ${url.encodedPath}"
            }
            val body = resp.body!!.string()
            check(body.isNotBlank()) { "Empty response body from ${url.encodedPath}" }
            body
        }
    }

    private fun get(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "text/html,application/json,*/*")
        .build()

    /**
     * Maitri's `subject` field passes course names through a `gemsDecode()`
     * helper in browser-side JS. The bytes that arrive over the wire usually
     * already look fine, but we also normalise common HTML entities just in
     * case (e.g. `&amp;`, `&#039;`).
     */
    private fun decodeHtmlEntities(s: String): String =
        s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#039;", "'")
            .replace("&nbsp;", " ")

    companion object {
        private const val BASE = "https://maitri.bmu.edu.in"
        private const val LOGIN_PAGE_URL = "$BASE/loginPage.htm"
        private const val LOGIN_CHECK_URL = "$BASE/j_spring_security_check"
        private const val TERMS_URL = "$BASE/stu_getTermsOfStudentForCourceFile.json"
        private const val SUBJECTS_URL = "$BASE/stu_getSubjectOnChangeWithSemId1.json"
        private const val TIMETABLE_URL = "$BASE/stu_getBetweenDatesTimetableForStudentSP.json"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build()
    }
}

/** Bare-bones in-memory CookieJar — one instance per `fetchAttendance` call. */
private class InMemoryCookieJar : CookieJar {
    private val store = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val incomingNames = cookies.map { it.name }.toSet()
        store.removeAll { it.name in incomingNames }
        store.addAll(cookies)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store.filter { it.matches(url) }
}
