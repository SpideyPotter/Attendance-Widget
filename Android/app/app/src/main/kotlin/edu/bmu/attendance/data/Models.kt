package edu.bmu.attendance.data

/** A semester / term entry as returned by Maitri. */
data class Term(
    val semesterId: Int,
    val name: String,
    val startDate: String,
    val endDate: String,
    val subjectwiseStudentIds: String,
    val subejctwiseBatchIds: String, // sic — Maitri's API spelling, kept verbatim
    val batchsemesterCapacity: Int,
)

/** One subject's attendance record. */
data class Subject(
    val code: String,
    val name: String,
    val present: Int,
    val absent: Int,
    val afterCapping: Double,
) {
    val total: Int get() = present + absent
    val percentage: Double get() = if (total == 0) 0.0 else present * 100.0 / total

    /**
     * Compact display name derived from [name], mirroring the macOS plugin's
     * `get_abbreviation` (see `MacOS/attendance.1h.py`).
     *
     * Rules:
     * - Split on spaces.
     * - For each word, if it contains a hyphen (e.g. `Course-II`), process
     *   each part separately and re-join with `-`.
     * - Roman numerals `I`..`V` are preserved verbatim (uppercased).
     * - Otherwise, take the first character of any word that starts with an
     *   uppercase letter; lowercase / connector words ("and", "of", "in") are
     *   skipped.
     *
     * Examples:
     * - `"Artificial Intelligence in Electric Mobility"` → `"AIEM"`
     * - `"Cryptography"` → `"C"`
     * - `"Project-III"` → `"P-III"`
     * - `"Theory of Computation"` → `"TC"`
     */
    val abbreviation: String get() = makeAbbreviation(name)
}

private val ROMAN_NUMERAL = Regex("^(I{1,3}|IV|V)$", RegexOption.IGNORE_CASE)

private fun makeAbbreviation(courseName: String): String {
    if (courseName.isBlank()) return ""
    val out = StringBuilder()
    for (word in courseName.split(' ')) {
        if (word.isEmpty()) continue
        if ('-' in word) {
            val parts = word.split('-')
                .mapNotNull { abbreviatePart(it.trim()) }
            if (parts.isNotEmpty()) {
                out.append(parts.joinToString("-"))
            }
        } else {
            abbreviatePart(word.trim())?.let { out.append(it) }
        }
    }
    return out.toString()
}

/** Returns the abbreviation contribution of [part], or null to skip it. */
private fun abbreviatePart(part: String): String? {
    if (part.isEmpty()) return null
    if (ROMAN_NUMERAL.matches(part)) return part.uppercase()
    val first = part.first()
    return if (first.isUpperCase()) first.toString() else null
}

/** Everything the widget needs to render, captured at one moment. */
data class AttendanceSnapshot(
    val termName: String,
    val termSemesterId: Int,
    val subjects: List<Subject>,
    val fetchedAtMillis: Long,
) {
    val totalPresent: Int get() = subjects.sumOf { it.present }
    val totalDelivered: Int get() = subjects.sumOf { it.total }
    val overallPercentage: Double
        get() = if (totalDelivered == 0) 0.0 else totalPresent * 100.0 / totalDelivered
}

/** User-supplied Maitri credentials. */
data class Credentials(
    val username: String,
    val password: String,
) {
    /**
     * Maitri requires the full email as `j_username` — bare local part
     * is silently rejected.
     */
    val isValid: Boolean
        get() = username.contains('@') && password.isNotBlank()
}

/** Errors surfaced by [MaitriClient]; mapped to user-readable strings in the UI. */
sealed class MaitriError(message: String) : Exception(message) {
    /** Credentials were rejected by Spring Security (`/login.htm?failure=true`). */
    object InvalidCredentials : MaitriError("Invalid username or password")
    /** Username is missing the `@…` part. */
    object UsernameNotEmail : MaitriError("Username must be the full email (e.g. you@bmu.edu.in)")
    /** Generic network / TLS failure. */
    class Network(cause: Throwable) :
        MaitriError("Network error: ${cause.message ?: cause::class.simpleName}")
    /** Portal returned something unexpected (e.g. layout changed, maintenance mode). */
    class Portal(message: String) : MaitriError(message)
    /** Account has no enrolled terms. */
    object NoTerms : MaitriError("No enrolled terms found for this account")
}
