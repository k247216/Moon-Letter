package com.twomemory.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import com.twomemory.designsystem.TwoMemoryTheme
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Restore a recipient-less private plan after pairing, through the real library and HTTP save. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OccasionRecipientEditorTest {
    private val compose = createComposeRule()
    private val couple = UUID.randomUUID()
    private val author = UUID.randomUUID()
    private val partner = UUID.randomUUID()
    private val id = UUID.randomUUID().toString()
    private val saves = AtomicInteger()
    private val spaceReads = AtomicInteger()
    private val savedRequest = AtomicReference<String?>()
    private var paired = true
    private lateinit var server: HttpServer
    private val fixture = object : ExternalResource() {
        override fun before() {
            server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                val response = if (exchange.requestMethod == "PUT") {
                    saves.incrementAndGet()
                    val raw = exchange.requestBody.bufferedReader().readText(); savedRequest.set(raw)
                    JSONObject(raw).put("id", id).put("coupleId", couple.toString()).put("authorId", author.toString())
                        .put("updatedAt", "2026-10-10T00:00:00Z").toString()
                } else {
                    spaceReads.incrementAndGet()
                    val members = if (paired) """[{"userId":"$author","profile":{"displayName":"我"}},{"userId":"$partner","profile":{"displayName":"对方"}}]"""
                        else """[{"userId":"$author","profile":{"displayName":"我"}}]"""
                    """{"id":"$couple","members":$members}"""
                }
                val bytes = response.toByteArray(); exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            SyncSession.save(ApplicationProvider.getApplicationContext(), "recipient-test", couple, author,
                "http://127.0.0.1:${server.address.port}")
        }
        override fun after() { if (::server.isInitialized) server.stop(0) }
    }
    @get:org.junit.Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    private fun openPrivatePlan() {
        val card = OccasionCard(id, couple.toString(), author.toString(), "SURPRISE", "准备一份惊喜", "只写给你的话",
            "2027-05-09", false, null, null, "2026-10-10T00:00:00Z", "PRIVATE")
        compose.setContent { TwoMemoryTheme {
                OccasionLibrary(listOf(card), author.toString(), couple.toString(), {})
        } }
        compose.onNodeWithText("编辑").performScrollTo().performClick()
    }

    @Test fun boundPartnerMakesRestoredPrivatePlanShareableWithTheConfirmedRecipient() {
        openPrivatePlan()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("当天送给对方").fetchSemanticsNodes().any { node ->
                !node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            }
        }
        compose.onNodeWithText("当天送给对方").assertIsEnabled().performClick()
        compose.onNodeWithText("送给对方").performScrollTo().performClick()
        compose.waitUntil(10_000) { savedRequest.get() != null }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("心意已交给共同空间，会在指定日向对方展示").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, saves.get())
        val sent = JSONObject(savedRequest.get()!!)
        assertEquals("SHARED", sent.getString("visibility"))
        assertEquals(partner.toString(), sent.getString("recipientId"))
        assertEquals("只写给你的话", sent.getString("body"))
    }

    @Test fun openingPrivatePlanNeverPublishesItAutomatically() {
        openPrivatePlan()
        compose.waitUntil(10_000) { spaceReads.get() > 0 }
        compose.onNodeWithText("我的小计划").assertIsSelected()
        compose.onNodeWithText("收好我的小计划").performScrollTo().assertIsEnabled()
        assertEquals(0, saves.get())
    }
}
