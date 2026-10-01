package com.twomemory.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.database.AppDatabase
import com.twomemory.database.LocalEntryWriter
import com.twomemory.database.RoomSyncStore
import com.twomemory.model.BlockType
import com.twomemory.model.LocalBlockCommand
import com.twomemory.model.LocalEntryCommand
import com.twomemory.model.EntryMode
import com.twomemory.model.PendingOperation
import com.twomemory.network.PushResult
import com.twomemory.network.RetrofitCoupleDiaryApi
import com.twomemory.sync.SyncEngine
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.io.File
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Task 11 gate: the personal-text two-device vertical slice, on the JVM per
 * the Task 8 harness decision — TWO independent REAL Room databases, REAL
 * Retrofit HTTP against a REAL Spring Boot server process with REAL
 * PostgreSQL. No fake server, no in-memory store.
 *
 * Requires the server executable jar: `mvn -DskipTests package` in `server/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwoDeviceRecordingLoopTest {

    private lateinit var context: Context
    private lateinit var databaseA: AppDatabase
    private lateinit var databaseB: AppDatabase

    private val baseUrl = "http://127.0.0.1:18080"
    private val bootstrapSecret = "task11-slice-secret"
    private val jsonMediaType = "application/json".toMediaType()

    companion object {
        private const val ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres"
        private const val DB_USER = "moon_letter"
        private const val DB_PASSWORD = "moon_letter_dev_only"
        private const val TEST_DB = "moon_letter_slice_test"
        private const val BASE_URL = "http://127.0.0.1:18080"
        private const val BOOTSTRAP_SECRET = "task11-slice-secret"

        @JvmStatic
        private var serverProcess: Process? = null

        @BeforeClass
        @JvmStatic
        fun startRealServer() {
            DriverManager.getConnection(ADMIN_URL, DB_USER, DB_PASSWORD).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("DROP DATABASE IF EXISTS $TEST_DB WITH (FORCE)")
                    statement.execute("CREATE DATABASE $TEST_DB")
                }
            }
            val serverDir = File("../../server/target")
            val jar = serverDir.listFiles { file ->
                file.name.endsWith(".jar") && !file.name.contains("original")
            }?.firstOrNull() ?: error("server jar missing; run `mvn -DskipTests package` in server/ first")
            val java = (System.getenv("JAVA_HOME") ?: error("JAVA_HOME not set")) + "\\bin\\java.exe"
            serverProcess = ProcessBuilder(
                java, "-jar", jar.absolutePath,
                "--server.port=18080",
                "--spring.datasource.url=jdbc:postgresql://localhost:5432/$TEST_DB",
                "--spring.datasource.username=$DB_USER",
                "--spring.datasource.password=$DB_PASSWORD",
                "--moon-letter.bootstrap.secret=$BOOTSTRAP_SECRET",
            ).redirectOutput(ProcessBuilder.Redirect.appendTo(File("../../server/target/slice-server.log")))
                .redirectErrorStream(true)
                .start()
            val client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build()
            var up = false
            repeat(60) {
                runCatching {
                    client.newCall(Request.Builder().url("$BASE_URL/actuator/health").build()).execute()
                }.getOrNull()?.use { response ->
                    if (response.isSuccessful) up = true
                }
                if (!up) Thread.sleep(1000)
            }
            check(up) { "real server did not become healthy; see server/target/slice-server.log" }
        }

        @AfterClass
        @JvmStatic
        fun stopRealServer() {
            serverProcess?.destroy()
            serverProcess?.waitFor()
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseA = Room.databaseBuilder(context, AppDatabase::class.java, "slice-device-a.db").build()
        databaseB = Room.databaseBuilder(context, AppDatabase::class.java, "slice-device-b.db").build()
    }

    @After
    fun tearDown() {
        databaseA.close()
        databaseB.close()
        context.getDatabasePath("slice-device-a.db").deleteRecursively()
        context.getDatabasePath("slice-device-b.db").deleteRecursively()
    }

    private fun call(method: String, path: String, json: String?, token: String?): String {
        val builder = Request.Builder().url(baseUrl + path)
        val body = json?.toRequestBody(jsonMediaType)
        when (method) {
            "POST" -> builder.post(body ?: "".toRequestBody(jsonMediaType))
            else -> builder.get()
        }
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (json != null) builder.header("Content-Type", "application/json")
        val secret = if (path == "/api/v1/bootstrap") bootstrapSecret else null
        if (secret != null) builder.header("X-Bootstrap-Secret", secret)
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
            .newCall(builder.build()).execute().use { response ->
                val text = response.body?.string() ?: ""
                check(response.code == 200 || response.code == 201) {
                    "$method $path -> ${response.code}: $text"
                }
                return text
            }
    }

    private fun api(token: String) = RetrofitCoupleDiaryApi.create(baseUrl) { token }

    private fun command(coupleId: UUID, authorId: UUID, body: String) = LocalEntryCommand(
        coupleId = coupleId,
        authorId = authorId,
        mode = EntryMode.PERSONAL,
        occurredAt = Instant.parse("2026-09-30T12:18:00Z"),
        occurredTimezone = "Asia/Shanghai",
        title = "傍晚散步",
        blocks = listOf(
            LocalBlockCommand(
                type = BlockType.TEXT,
                orderKey = 0,
                payload = JSONObject().put("text", body).toString(),
                authorId = authorId,
            ),
        ),
    )

    @Test
    fun twoDevicesRecordAndSeeEachOthersEntries() = runBlocking {
        // 1. Device A bootstraps the installation.
        val boot = JSONObject(call("POST", "/api/v1/bootstrap",
            JSONObject().put("displayName", "小满").toString(), null))
        val tokenA = boot.getString("token")
        val coupleId = UUID.fromString(boot.getString("coupleId"))
        val userIdA = UUID.fromString(boot.getString("userId"))

        // 2. Device B pairs and receives its own session.
        val pairingToken = JSONObject(
            call("POST", "/api/v1/couple/$coupleId/pairing-token", "{}", tokenA),
        ).getString("pairingToken")
        val pairBody = JSONObject(
            call("POST", "/api/v1/couple/pair",
                JSONObject().put("token", pairingToken).toString(), null),
        )
        val tokenB = pairBody.getString("deviceToken")
        val userIdB = UUID.fromString(pairBody.getString("userId"))

        // 3. Device A writes OFFLINE: Room + outbox commit, nothing synced yet.
        val storeA = RoomSyncStore(databaseA)
        val entryIdA = LocalEntryWriter(databaseA).save(command(coupleId, userIdA, "A 的一天：暴雨"))
        val pendingA = storeA.pendingOperations(1).single()
        val beforePush = SyncEngine(api(tokenB), RoomSyncStore(databaseB), coupleId).pullAll()
        assertEquals(0, beforePush.pulled)
        assertNull(databaseB.entryDao().findEntry(entryIdA.toString()))

        // 4. A reconnects and pushes.
        assertEquals(1, SyncEngine(api(tokenA), storeA, coupleId).pushPending().applied)

        // 5. A duplicate timeout retry hits server idempotency: replayed, one effect.
        val replay = api(tokenA).push(pendingA)
        assertEquals(PushResult.Status.APPLIED, replay.status)
        assertTrue("replayed response expected", replay.replayed)

        // 6. Device B pulls and exposes the exact entry from its own database.
        val pullB = SyncEngine(api(tokenB), RoomSyncStore(databaseB), coupleId).pullAll()
        assertTrue("pull failed: ${pullB.failed}", pullB.pulled >= 1)
        val seenByB = databaseB.entryDao().findEntry(entryIdA.toString())
        assertNotNull(seenByB)
        assertEquals("傍晚散步", seenByB!!.title)
        val seenByBBlocks = databaseB.entryDao().blocks(entryIdA.toString())
        assertEquals(1, seenByBBlocks.size)

        // 7. Reverse reconnect: B writes offline, pushes; A pulls and sees it.
        val entryIdB = LocalEntryWriter(databaseB).save(command(coupleId, userIdB, "B 的一天：台风天"))
        assertEquals(1, SyncEngine(api(tokenB), RoomSyncStore(databaseB), coupleId).pushPending().applied)
        SyncEngine(api(tokenA), storeA, coupleId).pullAll()
        assertNotNull(databaseA.entryDao().findEntry(entryIdB.toString()))

        // 8. App-process recreation: closing and reopening Room keeps everything.
        databaseA.close()
        databaseA = Room.databaseBuilder(context, AppDatabase::class.java, "slice-device-a.db").build()
        assertNotNull(databaseA.entryDao().findEntry(entryIdA.toString()))
        assertNotNull(databaseA.entryDao().findEntry(entryIdB.toString()))
        assertEquals(2, databaseA.entryDao().timelineSnapshot().size)
    }
}
