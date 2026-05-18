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
                val subjects = fetchSubjects(client, current)
                AttendanceSnapshot(
                    termName = current.name,
                    termSemesterId = current.semesterId,
                    subjects = subjects,
                    fetchedAtMillis = System.currentTimeMillis(),
                )
            } catch (e: MaitriError) {
                throw e
            } catch (e: IOException) {
                throw MaitriError.Network(e)
            } catch (e: IllegalStateException) {
                // E.g. malformed JSON — surface as a portal error so we don't crash.
                throw MaitriError.Portal(e.message ?: "Portal returned unexpected data")
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
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Subject(
                code = o.optString("subjectCode", "?"),
                name = decodeHtmlEntities(o.optString("subject", "?")),
                present = o.optInt("presentCount", 0),
                absent = o.optInt("absentCount", 0),
                afterCapping = o.optDouble("afterCapping", 0.0),
            )
        }
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
