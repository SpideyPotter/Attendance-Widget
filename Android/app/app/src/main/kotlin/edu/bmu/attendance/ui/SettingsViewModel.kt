package edu.bmu.attendance.ui

import android.content.Context
import edu.bmu.attendance.data.AttendanceRepository
import edu.bmu.attendance.data.AttendanceSnapshot
import edu.bmu.attendance.data.Credentials
import edu.bmu.attendance.data.MaitriError
import edu.bmu.attendance.data.SnapshotStore
import edu.bmu.attendance.data.Subject
import edu.bmu.attendance.data.SubjectAliasStore
import edu.bmu.attendance.data.SubjectAliasStoreError
import edu.bmu.attendance.data.ThemeStore
import edu.bmu.attendance.ui.theme.AppThemeId
import edu.bmu.attendance.widget.AttendanceWidget
import edu.bmu.attendance.widget.CompactAttendanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll

enum class SettingsStatus { IDLE, BUSY, OK, FAILED }

data class SettingsState(
    val username: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val status: SettingsStatus = SettingsStatus.IDLE,
    val statusText: String = "Enter your Maitri credentials to refresh the widget.",
    val lastSnapshot: AttendanceSnapshot? = null,
    val hasSavedCredentials: Boolean = false,
) {
    val isBusy: Boolean get() = status == SettingsStatus.BUSY
}

class SettingsViewModel(context: Context) {
    private val appContext = context.applicationContext
    private val repo = AttendanceRepository.get(appContext)
    private val credentialStore = edu.bmu.attendance.data.CredentialStore(appContext)
    private val snapshotStore = SnapshotStore(appContext)
    private val themeStore = ThemeStore.get(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    val themeId: StateFlow<AppThemeId> = themeStore.themeId

    init {
        loadInitial()
    }

    fun loadInitial() {
        val hasSavedCredentials = credentialStore.hasCredentials()
        val creds = credentialStore.load()
        val snapshot = repo.cachedSnapshot
        _state.update {
            it.copy(
                username = creds?.username.orEmpty(),
                password = creds?.password.orEmpty(),
                hasSavedCredentials = hasSavedCredentials,
                lastSnapshot = snapshot,
                status = if (hasSavedCredentials && snapshot != null) SettingsStatus.OK else SettingsStatus.IDLE,
                statusText = if (hasSavedCredentials && snapshot != null) {
                    "Attendance is ready. Refresh anytime from the home screen."
                } else {
                    "Enter your Maitri credentials to refresh the widget."
                },
            )
        }
    }

    fun updateUsername(value: String) {
        _state.update { it.copy(username = value) }
    }

    fun updatePassword(value: String) {
        _state.update { it.copy(password = value) }
    }

    fun toggleShowPassword() {
        _state.update { it.copy(showPassword = !it.showPassword) }
    }

    suspend fun saveAndRefresh() {
        val current = _state.value
        val creds = Credentials(current.username.trim(), current.password)
        if (!creds.isValid) {
            _state.update {
                it.copy(
                    status = SettingsStatus.FAILED,
                    statusText = "Username must be the full email and password can't be empty.",
                )
            }
            return
        }
        _state.update { it.copy(status = SettingsStatus.BUSY, statusText = "Saving and refreshing…") }
        credentialStore.save(creds)
        performRefresh(force = true, successPrefix = "Saved")
    }

    suspend fun refreshFromHome() {
        _state.update { it.copy(status = SettingsStatus.BUSY, statusText = "Refreshing…") }
        performRefresh(force = true, successPrefix = "Updated")
    }

    suspend fun testCredentials() {
        val current = _state.value
        _state.update { it.copy(status = SettingsStatus.BUSY, statusText = "Testing…") }
        val result = repo.testCredentials(Credentials(current.username.trim(), current.password))
        result.fold(
            onSuccess = { snap ->
                _state.update {
                    it.copy(
                        status = SettingsStatus.OK,
                        statusText = "Login OK · ${snap.termName} · ${snap.subjects.size} subjects · " +
                            "%.2f%%".format(snap.overallPercentage),
                    )
                }
            },
            onFailure = { error ->
                _state.update {
                    it.copy(
                        status = SettingsStatus.FAILED,
                        statusText = humanise(error),
                    )
                }
            },
        )
    }

    suspend fun clearCredentials() {
        credentialStore.clear()
        snapshotStore.clear()
        _state.update {
            it.copy(
                username = "",
                password = "",
                hasSavedCredentials = false,
                status = SettingsStatus.IDLE,
                statusText = "Credentials cleared.",
                lastSnapshot = null,
            )
        }
        reloadWidget()
    }

    fun setAppTheme(themeId: AppThemeId) {
        themeStore.setTheme(themeId)
        reloadWidget()
    }

    private suspend fun performRefresh(force: Boolean, successPrefix: String) {
        val result = if (force) repo.forceRefresh() else repo.refresh()
        when (result) {
            is AttendanceRepository.RefreshResult.Success -> {
                val snap = result.snapshot
                _state.update {
                    it.copy(
                        status = SettingsStatus.OK,
                        hasSavedCredentials = true,
                        statusText = "$successPrefix · ${snap.termName} · ${snap.subjects.size} subjects · " +
                            "%.2f%%".format(snap.overallPercentage),
                        lastSnapshot = snap,
                    )
                }
                reloadWidget()
            }
            is AttendanceRepository.RefreshResult.Failure ->
                _state.update {
                    it.copy(
                        status = SettingsStatus.FAILED,
                        statusText = humanise(result.error),
                    )
                }.also { reloadWidget() }
            AttendanceRepository.RefreshResult.MissingCredentials ->
                _state.update {
                    it.copy(status = SettingsStatus.FAILED, statusText = "No credentials saved.")
                }.also { reloadWidget() }
        }
    }

    private fun reloadWidget() {
        scope.launch(Dispatchers.IO) {
            AttendanceWidget().updateAll(appContext)
            CompactAttendanceWidget().updateAll(appContext)
        }
    }

    private fun humanise(error: Throwable): String = when (error) {
        is MaitriError.InvalidCredentials -> "Invalid username or password."
        is MaitriError.UsernameNotEmail -> "Username must be the full email (e.g. you@bmu.edu.in)."
        is MaitriError.NoTerms -> "Maitri returned no enrolled terms."
        is MaitriError.Network -> "Network error: ${error.message}"
        is MaitriError.Portal -> "Portal error: ${error.message}"
        else -> error.message ?: "Unknown error"
    }
}

data class LabelRow(
    val subject: Subject,
    val draft: String,
    val defaultLabel: String,
    val currentLabel: String,
) {
    val id: String get() = subject.code
}

class LabelsViewModel(context: Context) {
    private val appContext = context.applicationContext
    private val aliasStore = SubjectAliasStore.get(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _rows = MutableStateFlow<List<LabelRow>>(emptyList())
    val rows: StateFlow<List<LabelRow>> = _rows.asStateFlow()

    private val _statusText = MutableStateFlow("Labels stay on this device and are not sent to Maitri.")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _statusIsError = MutableStateFlow(false)
    val statusIsError: StateFlow<Boolean> = _statusIsError.asStateFlow()

    fun load(snapshot: AttendanceSnapshot?) {
        if (snapshot == null) {
            _rows.value = emptyList()
            _statusText.value = "Refresh attendance first to load subjects."
            _statusIsError.value = false
            return
        }
        _rows.value = snapshot.subjects.map { subject ->
            val defaultLabel = SubjectAliasStore.defaultLabel(subject)
            val current = aliasStore.displayLabel(subject)
            val draft = aliasStore.alias(subject.code).orEmpty()
            LabelRow(subject, draft, defaultLabel, current)
        }
        _statusText.value = "Labels stay on this device and are not sent to Maitri."
        _statusIsError.value = false
    }

    fun updateDraft(rowId: String, draft: String) {
        _rows.update { rows ->
            rows.map { row -> if (row.id == rowId) row.copy(draft = draft) else row }
        }
    }

    fun save(rowId: String) {
        val row = _rows.value.firstOrNull { it.id == rowId } ?: return
        try {
            aliasStore.setAlias(row.subject.code, row.draft)
            val current = aliasStore.displayLabel(row.subject)
            val draft = aliasStore.alias(row.subject.code).orEmpty()
            _rows.update { rows ->
                rows.map { item ->
                    if (item.id == rowId) item.copy(currentLabel = current, draft = draft) else item
                }
            }
            _statusText.value = "Saved label for ${row.subject.code}."
            _statusIsError.value = false
            reloadWidget()
        } catch (_: SubjectAliasStoreError.LabelTooLong) {
            _statusText.value = "Labels can be at most ${SubjectAliasStore.MAX_ALIAS_LENGTH} characters."
            _statusIsError.value = true
        } catch (_: Exception) {
            _statusText.value = "Could not save this label."
            _statusIsError.value = true
        }
    }

    fun reset(rowId: String) {
        val row = _rows.value.firstOrNull { it.id == rowId } ?: return
        try {
            aliasStore.clearAlias(row.subject.code)
            val current = aliasStore.displayLabel(row.subject)
            _rows.update { rows ->
                rows.map { item ->
                    if (item.id == rowId) item.copy(currentLabel = current, draft = "") else item
                }
            }
            _statusText.value = "Reset ${row.subject.code} to the default label."
            _statusIsError.value = false
            reloadWidget()
        } catch (_: Exception) {
            _statusText.value = "Could not reset this label."
            _statusIsError.value = true
        }
    }

    fun resetAll() {
        try {
            aliasStore.resetAll()
            _rows.update { rows ->
                rows.map { row ->
                    row.copy(
                        draft = "",
                        currentLabel = aliasStore.displayLabel(row.subject),
                    )
                }
            }
            _statusText.value = "All custom labels were cleared."
            _statusIsError.value = false
            reloadWidget()
        } catch (_: Exception) {
            _statusText.value = "Could not clear custom labels."
            _statusIsError.value = true
        }
    }

    private fun reloadWidget() {
        scope.launch(Dispatchers.IO) {
            AttendanceWidget().updateAll(appContext)
            CompactAttendanceWidget().updateAll(appContext)
        }
    }
}
