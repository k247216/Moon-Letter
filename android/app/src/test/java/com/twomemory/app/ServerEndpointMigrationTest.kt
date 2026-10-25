package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.twomemory.model.EntryMode
import com.twomemory.network.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerEndpointMigrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val target = ServerEndpointMigration.PUBLIC_ORIGIN
    private fun seed(): SyncSession.Session {
        SyncSession.save(context, "migration-test-only", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:18088")
        return SyncSession.load(context)!!
    }
    private fun api(session: SyncSession.Session, wrongSpace: Boolean = false, wrongMember: Boolean = false): SessionApi =
        object : SessionApi by RetrofitSessionApi.create() {
            override suspend fun readSpace(baseUrl: String, bearer: String, coupleId: UUID): SpaceViewDto {
                check(baseUrl == target && bearer == session.token && coupleId == session.coupleId)
                return SpaceViewDto(if (wrongSpace) UUID.randomUUID() else coupleId,
                    listOf(MemberViewDto(if (wrongMember) UUID.randomUUID() else session.userId, ProfileViewDto("本人"))))
            }
        }
    @Test fun verifiedSwitchKeepsIdentityDatabaseDraftsAndDailyDismissal() = runBlocking {
        val before = seed()
        val scope = "${before.baseUrl}|${before.coupleId}|${before.userId}"
        val id = UUID.randomUUID().toString()
        DraftLibrary(context, scope).save(id, EntryMode.PERSONAL, DraftStore.Draft("未上传的草稿", "保存这句话"))
        val card = OccasionCard(UUID.randomUUID().toString(), before.coupleId.toString(), before.userId.toString(),
            "BIRTHDAY", "生日", "", LocalDate.now().toString(), true, null, null, "")
        OccasionCloud.store(context, before).apply { replace(listOf(card)); dismiss(card, LocalDate.now()) }
        ServerEndpointMigration.migrate(context, target, api(before))
        val after = SyncSession.load(context)!!
        assertEquals(target, after.baseUrl)
        assertEquals(before.databaseName, after.databaseName)
        assertEquals(before.token, after.token)
        assertEquals(before.coupleId, after.coupleId)
        assertEquals(before.userId, after.userId)
        assertEquals("未上传的草稿", DraftLibrary(context, "${after.storageOrigin}|${after.coupleId}|${after.userId}").load(id)!!.draft.title)
        assertEquals(1, OccasionCloud.store(context, after).cards().size)
        assertTrue(OccasionCloud.store(context, after).due(after.userId.toString(), LocalDate.now()).isEmpty())
        ServerEndpointMigration.migrate(context, target, api(before))
        assertEquals(after, SyncSession.load(context))
    }
    @Test fun unverifiedSpacesMembersAndNetworkErrorsNeverChangeBinding() = runBlocking {
        val before = seed()
        for (remote in listOf(api(before, wrongSpace = true), api(before, wrongMember = true),
            object : SessionApi by api(before) {
                override suspend fun readSpace(baseUrl: String, bearer: String, coupleId: UUID): SpaceViewDto = throw SetupHttpException(401, "test")
            })) {
            assertTrue(runCatching { ServerEndpointMigration.migrate(context, target, remote) }.isFailure)
            assertEquals(before, SyncSession.load(context))
        }
    }
    @Test fun arbitraryDomainOrHttpDestinationIsRejectedBeforeAnyCredentialLeaves() = runBlocking {
        val before = seed()
        val noNetwork = object : SessionApi by api(before) {
            override suspend fun readSpace(baseUrl: String, bearer: String, coupleId: UUID): SpaceViewDto = error("Credentials must not leave")
        }
        for (destination in listOf(target.replaceFirst("https:", "http:"), "https://evil.example", "$target@evil.example", "$target:443/path")) {
            val failure = runCatching { ServerEndpointMigration.migrate(context, destination, noNetwork) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(before, SyncSession.load(context))
        }
    }
    @Test fun failedDiskCommitRestoresMemoryAndRetryPersistsVerifiedEndpoint() = runBlocking {
        val before = seed()
        assertTrue(runCatching {
            ServerEndpointMigration.migrate(context, target, api(before), commit = { editor ->
                editor.apply() // Android updates memory even when the disk write fails.
                false
            })
        }.isFailure)
        assertEquals(before, SyncSession.load(context))
        ServerEndpointMigration.migrate(context, target, api(before))
        assertEquals(target, SyncSession.load(context)!!.baseUrl)
        assertEquals(before.storageOrigin, SyncSession.load(context)!!.storageOrigin)
    }
    @Test fun foregroundWaitingForTransitionResolvesNewTransportInsideLock() = runBlocking {
        seed()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = async(start = CoroutineStart.UNDISPATCHED) {
            SyncSession.withPausedForeground { entered.complete(Unit); release.await()
                SyncSession.sessionPrefs(context).edit().putString("baseUrl", target).commit()
            }
        }
        entered.await()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            SyncSession.withCurrentForegroundSession(context) { it.baseUrl }
        }
        release.complete(Unit); holder.await()
        assertEquals(target, waiting.await())
    }
    @Test fun staleBindingRequestCannotOverwriteCompletedTransition() = runBlocking {
        val before = seed()
        ServerEndpointMigration.migrate(context, target, api(before))
        val after = SyncSession.load(context)
        assertFalse(SyncSession.saveIfCurrent(context, before, "other-token", UUID.randomUUID(), UUID.randomUUID(), before.baseUrl))
        assertEquals(after, SyncSession.load(context))
    }
}
