@file:JvmName("MaitriProbe")

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Probe that proves the OkHttp + JSON approach works end-to-end against
 * Maitri before we bake it into the Android widget.
 *
 * See `Android/FLOW.md` for the full HTTP contract this exercises.
 *
 * Usage (interactive — recommended; password is read silently):
 *   ./run.sh
 *
 * Or with env vars:
 *   MAITRI_USERNAME="you@bmu.edu.in" MAITRI_PASSWORD="…" ./gradlew run -q
 */

private const val BASE = "https://maitri.bmu.edu.in"
private const val LOGIN_PAGE_URL = "$BASE/loginPage.htm"
private const val LOGIN_CHECK_URL = "$BASE/j_spring_security_check"
private const val TERMS_URL = "$BASE/stu_getTermsOfStudentForCourceFile.json"
private const val SUBJECTS_URL = "$BASE/stu_getSubjectOnChangeWithSemId1.json"

private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"

fun main() {
    val username = requireEnv("MAITRI_USERNAME").also(::validateUsername)
    val password = requireEnv("MAITRI_PASSWORD")

    val cookieJar = InMemoryCookieJar()
    val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // 1. Seed JSESSIONID by hitting the login page.
    progress("GET login page (seed JSESSIONID)") {
        client.newCall(get(LOGIN_PAGE_URL)).execute().use { resp ->
            require(resp.isSuccessful) { "Login page returned HTTP ${resp.code}" }
        }
        val sid = cookieJar.find("JSESSIONID")
            ?: error("No JSESSIONID was set by /loginPage.htm — Maitri may be down.")
        "JSESSIONID=${sid.value.take(8)}…"
    }

    // 2. Submit credentials. Spring Security redirects to /login.htm?failure=true
    //    when auth fails, so URL-based detection is reliable.
    progress("POST /j_spring_security_check") {
        val body = FormBody.Builder()
            .add("j_username", username)
            .add("j_password", password)
            .build()
        val request = Request.Builder()
            .url(LOGIN_CHECK_URL)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$BASE/login.htm")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            require(resp.isSuccessful) {
                "Login POST returned HTTP ${resp.code} (final URL: $finalUrl)"
            }
            require(!finalUrl.contains("login.htm", ignoreCase = true)) {
                "Login appears to have failed — redirected to $finalUrl. " +
                    "Common cause: Maitri requires the FULL EMAIL as username " +
                    "(e.g. you@bmu.edu.in), not just the local part."
            }
            "landed on $finalUrl"
        }
    }

    // 3. Fetch all terms (semesters) the student is enrolled in.
    val terms: List<Term> = progressValue("GET terms list") {
        val json = fetchJson(client, TERMS_URL.toHttpUrl())
        val arr = JSONArray(json)
        val list = (0 until arr.length()).map { Term.fromJson(arr.getJSONObject(it)) }
        ProgressResult(list, "${list.size} term(s) found")
    }

    // 4. Pick the latest semester (highest semesterId). For the widget we'll
    //    use the same heuristic, but we may also filter by `teachingEndDate`
    //    being in the future once we wire up date parsing.
    val current = terms.maxByOrNull { it.semesterId }
        ?: error("Terms list was empty — student may not yet have any enrollments.")
    println("→ Current term: ${current.name} (semId=${current.semesterId}, " +
        "${current.startDate} → ${current.endDate})")

    // 5. Fetch attendance JSON for that term.
    val subjects: List<Subject> = progressValue("GET attendance for ${current.name}") {
        val url = SUBJECTS_URL.toHttpUrl().newBuilder().apply {
            addQueryParameter("termId", current.semesterId.toString())
            addQueryParameter("refreshData", "0")
            addQueryParameter("subjectwisestudentids", current.subjectwiseStudentIds)
            addQueryParameter("subejctwiseBatchIds", current.subejctwiseBatchIds)
            addQueryParameter("batchsemestercapacity", current.batchsemesterCapacity.toString())
        }.build()
        val json = fetchJson(client, url)
        val arr = JSONArray(json)
        val list = (0 until arr.length()).map { Subject.fromJson(arr.getJSONObject(it)) }
        ProgressResult(list, "${list.size} subject(s)")
    }

    println()
    render(subjects)
}

// ─── HTTP helpers ────────────────────────────────────────────────────────────

private fun get(url: String): Request = Request.Builder()
    .url(url)
    .header("User-Agent", USER_AGENT)
    .header("Accept", "application/json, text/plain, */*")
    .build()

private fun fetchJson(client: OkHttpClient, url: HttpUrl): String =
    client.newCall(
        Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("X-Requested-With", "XMLHttpRequest")
            .build(),
    ).execute().use { resp ->
        require(resp.isSuccessful) { "GET $url returned HTTP ${resp.code}" }
        val finalUrl = resp.request.url.toString()
        require(!finalUrl.contains("login.htm", ignoreCase = true)) {
            "Session was rejected when calling $url — redirected back to login."
        }
        val body = resp.body!!.string()
        require(body.isNotBlank()) { "Empty response body from $url" }
        body
    }

private fun requireEnv(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: error(
            "Environment variable $name is required. " +
                "Easiest path: run ./run.sh and let it prompt you.",
        )

private fun validateUsername(username: String) {
    // Maitri's Spring Security expects the full email. The bare local part
    // (e.g. "ravindrareddy.kota.23cse") is silently rejected as invalid.
    if (!username.contains("@")) {
        System.err.println(
            "⚠️  Warning: '$username' looks like just the local part of your email. " +
                "Maitri usually requires the full email (e.g. ${username}@bmu.edu.in). " +
                "If login fails, try again with the full address.",
        )
    }
}

// ─── Pretty progress output ──────────────────────────────────────────────────

private fun progress(label: String, block: () -> String) {
    print("→ $label… ")
    runCatching(block)
        .onFailure { println("FAILED"); throw it }
        .getOrThrow()
        .let { detail -> println(if (detail.isEmpty()) "ok" else "ok ($detail)") }
}

private data class ProgressResult<T>(val value: T, val detail: String)

private fun <T> progressValue(label: String, block: () -> ProgressResult<T>): T {
    print("→ $label… ")
    return runCatching(block)
        .onFailure { println("FAILED"); throw it }
        .getOrThrow()
        .also { result ->
            println(if (result.detail.isEmpty()) "ok" else "ok (${result.detail})")
        }
        .value
}

// ─── Domain types ────────────────────────────────────────────────────────────

private data class Term(
    val semesterId: Int,
    val name: String,
    val startDate: String,
    val endDate: String,
    val subjectwiseStudentIds: String,
    val subejctwiseBatchIds: String, // sic — Maitri's spelling
    val batchsemesterCapacity: Int,
) {
    companion object {
        fun fromJson(o: JSONObject) = Term(
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

private data class Subject(
    val code: String,
    val name: String,
    val present: Int,
    val absent: Int,
    val afterCapping: Double,
) {
    val total: Int get() = present + absent
    val percentage: Double get() = if (total == 0) 0.0 else present * 100.0 / total

    companion object {
        fun fromJson(o: JSONObject) = Subject(
            code = o.optString("subjectCode", "?"),
            name = o.optString("subject", "?"),
            present = o.optInt("presentCount", 0),
            absent = o.optInt("absentCount", 0),
            afterCapping = o.optDouble("afterCapping", 0.0),
        )
    }
}

// ─── Renderer ────────────────────────────────────────────────────────────────

private fun render(subjects: List<Subject>) {
    if (subjects.isEmpty()) {
        println("⚠️  No subjects found for this term.")
        return
    }

    val codeW = maxOf(4, subjects.maxOf { it.code.length })
    val nameW = subjects.maxOf { it.name.length }.coerceIn(20, 40)
    val countW = maxOf(7, subjects.maxOf { "${it.present}/${it.total}".length })

    val sep = "+" + "-".repeat(codeW + 2) +
        "+" + "-".repeat(nameW + 2) +
        "+" + "-".repeat(countW + 2) +
        "+" + "-".repeat(8) + "+"

    println(sep)
    println(
        "| ${"Code".padEnd(codeW)} | ${"Subject".padEnd(nameW)} | " +
            "${"Count".padEnd(countW)} | ${"%".padEnd(6)} |",
    )
    println(sep)

    var totalPresent = 0
    var totalDelivered = 0
    for (s in subjects) {
        val nameDisplay = if (s.name.length > nameW) s.name.take(nameW - 1) + "…" else s.name
        val count = "${s.present}/${s.total}"
        val pct = "%.2f".format(s.percentage)
        println(
            "| ${s.code.padEnd(codeW)} | ${nameDisplay.padEnd(nameW)} | " +
                "${count.padEnd(countW)} | ${pct.padEnd(6)} |",
        )
        totalPresent += s.present
        totalDelivered += s.total
    }
    println(sep)

    if (totalDelivered > 0) {
        val overall = totalPresent * 100.0 / totalDelivered
        println("Overall: $totalPresent / $totalDelivered = ${"%.2f".format(overall)}%")
    }
}

// ─── Cookie jar ──────────────────────────────────────────────────────────────

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

    @Synchronized
    fun find(name: String): Cookie? = store.firstOrNull { it.name == name }
}
