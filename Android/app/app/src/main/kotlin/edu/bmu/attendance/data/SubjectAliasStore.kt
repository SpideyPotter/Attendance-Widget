package edu.bmu.attendance.data

import android.content.Context
import org.json.JSONObject
import java.io.File

class SubjectAliasStore private constructor(
    private val file: File,
) {
    fun displayLabel(subject: Subject): String {
        val aliases = loadPayload().aliases
        val custom = aliases[subject.code]?.trim().orEmpty()
        if (custom.isNotEmpty()) return custom
        return defaultLabel(subject)
    }

    fun alias(code: String): String? = loadPayload().aliases[code]

    fun allAliases(): Map<String, String> = loadPayload().aliases

    fun setAlias(code: String, label: String): Boolean {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return clearAlias(code)
        if (trimmed.length > MAX_ALIAS_LENGTH) throw SubjectAliasStoreError.LabelTooLong
        val payload = loadPayload()
        payload.aliases[code] = trimmed
        savePayload(payload)
        return true
    }

    fun clearAlias(code: String): Boolean {
        val payload = loadPayload()
        if (payload.aliases.remove(code) == null) return false
        savePayload(payload)
        return true
    }

    fun resetAll() {
        savePayload(Payload())
    }

    private fun loadPayload(): Payload {
        if (!file.exists()) return Payload()
        return runCatching {
            val json = JSONObject(file.readText())
            val aliases = mutableMapOf<String, String>()
            val objectAliases = json.optJSONObject("aliases") ?: return Payload()
            objectAliases.keys().forEach { key ->
                objectAliases.optString(key, "").takeIf { it.isNotEmpty() }?.let { aliases[key] = it }
            }
            Payload(aliases)
        }.getOrDefault(Payload())
    }

    private fun savePayload(payload: Payload) {
        file.parentFile?.mkdirs()
        val json = JSONObject().put(
            "aliases",
            JSONObject().apply {
                payload.aliases.forEach { (code, label) -> put(code, label) }
            },
        )
        file.writeText(json.toString())
    }

    private data class Payload(
        val aliases: MutableMap<String, String> = mutableMapOf(),
    )

    companion object {
        const val MAX_ALIAS_LENGTH = 12
        private const val FILE_NAME = "subject_aliases.json"

        @Volatile private var instance: SubjectAliasStore? = null

        fun get(context: Context): SubjectAliasStore {
            return instance ?: synchronized(this) {
                instance ?: SubjectAliasStore(
                    File(context.applicationContext.filesDir, FILE_NAME),
                ).also { instance = it }
            }
        }

        fun defaultLabel(subject: Subject): String =
            subject.abbreviation.ifEmpty { subject.code }
    }
}

sealed class SubjectAliasStoreError : Exception() {
    object LabelTooLong : SubjectAliasStoreError()
    object StorageUnavailable : SubjectAliasStoreError()
}
