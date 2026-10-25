package com.twomemory.app

import android.content.Context
import android.content.SharedPreferences
import com.twomemory.network.AnniversaryViewDto
import com.twomemory.network.RetrofitSessionApi
import com.twomemory.network.SaveAnniversaryRequestDto
import com.twomemory.app.notifications.IndependentAnniversaryReminder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import org.json.JSONObject

internal data class IndependentAnniversaryDraft(
    val title: String = "我们的纪念日",
    val year: Int? = null,
    val month: Int = 9,
    val day: Int = 24,
    val reminderTime: String = "09:00",
)

internal data class IndependentAnniversaryState(
    val confirmed: AnniversaryViewDto? = null,
    val draft: IndependentAnniversaryDraft = IndependentAnniversaryDraft(),
    val saving: Boolean = false,
    val error: String? = null,
)

internal fun independentAnniversaryValidation(draft: IndependentAnniversaryDraft, today: LocalDate): String? {
    val title = draft.title.trim()
    if (title.isEmpty() || title.codePointCount(0, title.length) > 80) return "名称需为 1–80 个字符"
    val year = draft.year ?: return "请选择原始年份"
    if (year !in 1..32767 || year > today.year) return "原始年份不能晚于今年"
    val date = runCatching { LocalDate.of(year, draft.month, draft.day) }.getOrNull() ?: return "请选择有效公历日期"
    if (date > today) return "原始日期不能晚于今天"
    if (runCatching { LocalTime.parse(draft.reminderTime) }.isFailure) return "请选择有效提醒时间"
    return null
}

/** Shared confirmation and unsubmitted local input have separate, account-scoped storage. */
internal class IndependentAnniversaryStore private constructor(
    private val context: Context,
    val binding: SyncSession.BindingSnapshot,
) {
    val scope: String = recordingScopeKey("${binding.session.storageOrigin}|${binding.session.coupleId}|${binding.session.userId}")
    private val prefs = preferences(context, binding)
    private val mutableState = MutableStateFlow(IndependentAnniversaryState(
        confirmed = readConfirmed(prefs), draft = readDraft(prefs),
    ))
    val state: StateFlow<IndependentAnniversaryState> = mutableState
    private var operation = 0L
    private var unpersistedDraft = false

    fun editDraft(draft: IndependentAnniversaryDraft) = synchronized(SyncSession.bindingLock) {
        if (!SyncSession.isCurrentBinding(context, binding)) return@synchronized
        mutableState.value = mutableState.value.copy(draft = draft, error = null)
        unpersistedDraft = !runCatching { prefs.edit().putString("draft", draftJson(draft).toString()).commit() }.getOrDefault(false)
        if (unpersistedDraft) mutableState.value = mutableState.value.copy(error = localWriteError)
    }

    fun validationError(today: LocalDate = LocalDate.now()): String? = independentAnniversaryValidation(state.value.draft, today)

    fun reloadConfirmed() = synchronized(SyncSession.bindingLock) {
        if (SyncSession.isCurrentBinding(context, binding))
            mutableState.value = mutableState.value.copy(confirmed = readConfirmed(prefs),
                draft = if (unpersistedDraft || prefs.contains("draft")) state.value.draft else readDraft(prefs))
    }

    /** The caller owns this listener, normally with DisposableEffect/onDispose. */
    fun observeConfirmed(): AutoCloseable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "confirmed") reloadConfirmed()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        reloadConfirmed()
        return AutoCloseable { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    suspend fun refresh(): Boolean {
        val job = currentCoroutineContext()
        val ticket: Long
        val revision: Long
        synchronized(SyncSession.bindingLock) {
            job.ensureActive()
            if (!SyncSession.isCurrentBinding(context, binding) || state.value.saving || activeWrite(context, binding) != null) return false
            ticket = ++operation
            revision = prefs.getLong("revision", 0)
        }
        return try {
            val session = binding.session
            val remote = RetrofitSessionApi.create().readAnniversary(session.baseUrl, session.token, session.coupleId)
            val accepted = synchronized(SyncSession.bindingLock) {
                job.ensureActive()
                if (ticket != operation || !SyncSession.isCurrentBinding(context, binding)) false
                else acceptConfirmed(context, binding, remote, revision).also { if (it) reloadConfirmed() }
            }
            if (accepted) runCatching { IndependentAnniversaryReminder.schedule(context, binding) }
            accepted
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }

    suspend fun save(today: LocalDate = LocalDate.now()): Boolean {
        val job = currentCoroutineContext()
        val draft: IndependentAnniversaryDraft
        val ticket: Long
        val owner = UUID.randomUUID()
        synchronized(SyncSession.bindingLock) {
            job.ensureActive()
            if (!SyncSession.isCurrentBinding(context, binding) || state.value.saving || activeWrite(context, binding) != null) return false
            val error = validationError(today)
            if (error != null) { mutableState.value = mutableState.value.copy(error = error); return false }
            draft = state.value.draft
            if (unpersistedDraft) {
                val saved = runCatching { prefs.edit().putString("draft", draftJson(draft).toString()).commit() }.getOrDefault(false)
                if (!saved) { mutableState.value = mutableState.value.copy(error = localWriteError); return false }
                unpersistedDraft = false
            }
            ticket = ++operation
            val epochWritten = runCatching { prefs.edit().putLong("revision", prefs.getLong("revision", 0) + 1).commit() }.getOrDefault(false)
            if (!epochWritten) {
                mutableState.value = mutableState.value.copy(error = "本机写入失败，尚未请求云端保存；当前输入保留，请重试")
                return false
            }
            writes[scope] = PendingWrite(binding, owner)
            mutableState.value = mutableState.value.copy(saving = true, error = null)
        }
        return try {
            val session = binding.session
            val remote = RetrofitSessionApi.create().saveAnniversary(session.baseUrl, session.token, session.coupleId,
                SaveAnniversaryRequestDto(draft.title.trim(), "GREGORIAN", draft.month, draft.day,
                    startYear = requireNotNull(draft.year), repeatsYearly = true))
            val accepted = synchronized(SyncSession.bindingLock) {
                job.ensureActive()
                if (ticket != operation || !SyncSession.isCurrentBinding(context, binding)) false else {
                    check(remote.coupleId == session.coupleId && remote.calendarType == "GREGORIAN" &&
                        remote.startYear == draft.year && remote.month == draft.month && remote.day == draft.day &&
                        remote.repeatsYearly && !remote.lunarLeapMonth) { "Unexpected anniversary acknowledgement" }
                    check(publishConfirmed(context, binding, remote, null, owner, draft.reminderTime, draft))
                    reloadConfirmed()
                    mutableState.value = mutableState.value.copy(error = if (unpersistedDraft) localWriteError else null)
                    true
                }
            }
            if (accepted) runCatching { IndependentAnniversaryReminder.schedule(context, binding) }.onFailure {
                synchronized(SyncSession.bindingLock) {
                    if (SyncSession.isCurrentBinding(context, binding))
                        mutableState.value = mutableState.value.copy(error = "日期已确认；本机提醒暂未排队，下次进入将重试")
                }
            }
            accepted
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            synchronized(SyncSession.bindingLock) {
                if (ticket == operation && SyncSession.isCurrentBinding(context, binding))
                    mutableState.value = mutableState.value.copy(error = if (unpersistedDraft) localWriteError
                        else "同步未确认；你的本机草稿已保留，请重试")
            }
            false
        } finally {
            synchronized(SyncSession.bindingLock) {
                if (writes[scope]?.owner == owner) {
                    // A cancelled/failed PUT also invalidates GETs begun while it was active.
                    // No persistent active-write flag can strand the next process.
                    try {
                        if (SyncSession.isCurrentBinding(context, binding))
                            runCatching { check(prefs.edit().putLong("revision", prefs.getLong("revision", 0) + 1).commit()) }
                    } finally {
                        writes.remove(scope)
                        if (ticket == operation) mutableState.value = mutableState.value.copy(saving = false)
                    }
                }
                if (ticket == operation) mutableState.value = mutableState.value.copy(saving = false)
            }
        }
    }

    companion object {
        private const val localWriteError = "本机保存未成功；输入暂留当前页面，离开可能丢失，请重试"
        private data class PendingWrite(val binding: SyncSession.BindingSnapshot, val owner: UUID)
        // Access only under bindingLock. Distinct Store instances coordinate on the same scope.
        private val writes = mutableMapOf<String, PendingWrite>()

        private fun activeWrite(context: Context, binding: SyncSession.BindingSnapshot): PendingWrite? {
            val scope = scopeOf(binding.session)
            val active = writes[scope] ?: return null
            if (!SyncSession.isCurrentBinding(context, active.binding)) {
                writes.remove(scope)
                return null
            }
            return active
        }

        fun store(context: Context, binding: SyncSession.BindingSnapshot): IndependentAnniversaryStore =
            IndependentAnniversaryStore(context.applicationContext, binding)

        internal fun preferences(context: Context, binding: SyncSession.BindingSnapshot): SharedPreferences =
            context.getSharedPreferences("independent_anniversary_${scopeOf(binding.session)}", Context.MODE_PRIVATE)

        internal fun scopeOf(session: SyncSession.Session): String =
            recordingScopeKey("${session.storageOrigin}|${session.coupleId}|${session.userId}")

        fun confirmedRevision(context: Context, binding: SyncSession.BindingSnapshot): Long =
            synchronized(SyncSession.bindingLock) { preferences(context, binding).getLong("revision", 0) }

        fun currentRevision(context: Context, binding: SyncSession.BindingSnapshot): Long = confirmedRevision(context, binding)

        /** Pass a revision captured before GET to reject an older GET arriving after a save. */
        fun acceptConfirmed(context: Context, binding: SyncSession.BindingSnapshot, remote: AnniversaryViewDto?,
            expectedRevision: Long? = null): Boolean =
            publishConfirmed(context, binding, remote, expectedRevision, null, null)

        private fun publishConfirmed(context: Context, binding: SyncSession.BindingSnapshot, remote: AnniversaryViewDto?,
            expectedRevision: Long?, owner: UUID?, reminderTime: String?, submittedDraft: IndependentAnniversaryDraft? = null): Boolean = synchronized(SyncSession.bindingLock) {
            if (!SyncSession.isCurrentBinding(context, binding) || (remote != null && remote.coupleId != binding.session.coupleId))
                return@synchronized false
            val active = activeWrite(context, binding)
            if (active != null && active.owner != owner) return@synchronized false
            if (owner != null && active?.owner != owner) return@synchronized false
            val prefs = preferences(context, binding)
            val revision = prefs.getLong("revision", 0)
            if (expectedRevision != null && revision != expectedRevision) return@synchronized false
            val encoded = remote?.let { confirmedJson(it).toString() }
            if (prefs.getString("confirmed", null) == encoded && reminderTime == null) return@synchronized true
            val editor = prefs.edit().putLong("revision", revision + 1)
            if (reminderTime != null) editor.putString("reminderTime", reminderTime)
            if (submittedDraft != null && prefs.contains("draft") && readDraft(prefs) == submittedDraft) editor.remove("draft")
            if (encoded == null) editor.remove("confirmed") else editor.putString("confirmed", encoded)
            commitBinding(prefs, editor)
            true
        }

        internal fun readConfirmed(prefs: SharedPreferences): AnniversaryViewDto? = runCatching {
            val raw = prefs.getString("confirmed", null) ?: return null
            val json = JSONObject(raw)
            AnniversaryViewDto(UUID.fromString(json.getString("id")), UUID.fromString(json.getString("coupleId")),
                json.getString("title"), json.getString("calendarType"), json.getInt("month"), json.getInt("day"),
                json.getBoolean("lunarLeapMonth"), if (json.isNull("startYear")) null else json.getInt("startYear"),
                json.getBoolean("repeatsYearly"))
        }.getOrNull()

        private fun confirmedJson(value: AnniversaryViewDto) = JSONObject()
            .put("id", value.id).put("coupleId", value.coupleId).put("title", value.title)
            .put("calendarType", value.calendarType).put("month", value.month).put("day", value.day)
            .put("lunarLeapMonth", value.lunarLeapMonth).put("startYear", value.startYear ?: JSONObject.NULL)
            .put("repeatsYearly", value.repeatsYearly)

        private fun draftJson(value: IndependentAnniversaryDraft) = JSONObject().put("title", value.title)
            .put("year", value.year ?: JSONObject.NULL).put("month", value.month).put("day", value.day)
            .put("reminderTime", value.reminderTime)

        private fun readDraft(prefs: SharedPreferences): IndependentAnniversaryDraft = runCatching {
            val raw = prefs.getString("draft", null) ?: return confirmedDraft(prefs)
            val json = JSONObject(raw)
            IndependentAnniversaryDraft(json.getString("title"), if (json.isNull("year")) null else json.getInt("year"),
                json.getInt("month"), json.getInt("day"), json.getString("reminderTime"))
        }.getOrDefault(IndependentAnniversaryDraft())

        private fun confirmedDraft(prefs: SharedPreferences): IndependentAnniversaryDraft {
            val confirmed = readConfirmed(prefs) ?: return IndependentAnniversaryDraft()
            val year = confirmed.startYear ?: return IndependentAnniversaryDraft()
            if (confirmed.calendarType != "GREGORIAN" || confirmed.lunarLeapMonth || year !in 1..32767 ||
                runCatching { LocalDate.of(year, confirmed.month, confirmed.day) }.isFailure) return IndependentAnniversaryDraft()
            return IndependentAnniversaryDraft(confirmed.title, year, confirmed.month, confirmed.day,
                prefs.getString("reminderTime", "09:00") ?: "09:00")
        }
    }
}
