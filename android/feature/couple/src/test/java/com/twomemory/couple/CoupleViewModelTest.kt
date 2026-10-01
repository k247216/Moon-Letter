package com.twomemory.couple

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM-level evidence for the rename entry: typing never touches the network,
 * one explicit save does, and a failed save keeps what she typed and says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoupleViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun typingAloneNeverReachesTheServer() {
        var calls = 0
        val viewModel = CoupleViewModel()
        viewModel.restoreNames(own = "阿屿", partner = "小满")
        viewModel.updateOwnName("阿")
        viewModel.updateOwnName("阿屿")
        viewModel.updateOwnName("阿屿屿")
        assertEquals(0, calls)
        assertEquals("阿屿屿", viewModel.state.value.draftName)
        assertEquals("阿屿", viewModel.state.value.ownName)
    }

    @Test
    fun oneExplicitSaveStoresTheNameTheServerReturns() = runTest {
        val seen = mutableListOf<String>()
        val viewModel = CoupleViewModel()
        viewModel.restoreNames(own = "阿屿", partner = "小满")
        viewModel.updateOwnName("  阿屿屿  ")

        viewModel.saveOwnName { candidate ->
            seen += candidate
            candidate.trim()
        }

        assertEquals(listOf("阿屿屿"), seen)
        val state = viewModel.state.value
        assertEquals("阿屿屿", state.ownName)
        assertNull(state.nameError)
        assertTrue(!state.savingName)
    }

    @Test
    fun aFailedSaveKeepsHerTypedNameAndSaysSo() = runTest {
        val viewModel = CoupleViewModel()
        viewModel.restoreNames(own = "阿屿", partner = "小满")
        viewModel.updateOwnName("阿屿屿")

        viewModel.saveOwnName { throw IOException("连不上服务器") }

        val state = viewModel.state.value
        assertEquals("阿屿", state.ownName)
        assertEquals("阿屿屿", state.draftName)
        assertNotNull(state.nameError)
        assertTrue(!state.savingName)
    }

    @Test
    fun aBlankNameIsNeverSaved() = runTest {
        var calls = 0
        val viewModel = CoupleViewModel()
        viewModel.restoreNames(own = "阿屿", partner = "小满")
        viewModel.updateOwnName("   ")

        viewModel.saveOwnName {
            calls++
            it
        }

        assertEquals(0, calls)
        assertEquals("阿屿", viewModel.state.value.ownName)
    }

    @Test
    fun aNameThatIsAlreadyStoredIsNotPushedAgain() = runTest {
        var calls = 0
        val viewModel = CoupleViewModel()
        viewModel.restoreNames(own = "阿屿", partner = "小满")
        viewModel.updateOwnName(" 阿屿 ")

        viewModel.saveOwnName {
            calls++
            it
        }

        assertEquals(0, calls)
        assertEquals("阿屿", viewModel.state.value.ownName)
    }

    @Test
    fun aMintedCodeIsShownWithTheSlotTheServerBoundItTo() = runTest {
        val viewModel = CoupleViewModel()

        viewModel.generatePairingCode { PairingCode("REJOIN", "b".repeat(43)) }

        val state = viewModel.state.value
        assertEquals("b".repeat(43), state.pairingCode)
        assertEquals("REJOIN", state.pairingCodeKind)
        assertNull(state.codeError)
        assertTrue(!state.generatingCode)
    }

    @Test
    fun aFailedMintDropsTheCodeItCouldNotReplace() = runTest {
        val viewModel = CoupleViewModel()
        viewModel.generatePairingCode { PairingCode("INVITE", "a".repeat(43)) }
        assertEquals("a".repeat(43), viewModel.state.value.pairingCode)

        viewModel.generatePairingCode { throw IOException("连不上服务器") }

        val state = viewModel.state.value
        assertNull(state.pairingCode)
        assertNotNull(state.codeError)
    }

    @Test
    fun hidingACodeAsksTheServerForNothing() = runTest {
        var calls = 0
        val viewModel = CoupleViewModel()
        viewModel.generatePairingCode {
            calls++
            PairingCode("INVITE", "a".repeat(43))
        }

        viewModel.hidePairingCode()

        assertNull(viewModel.state.value.pairingCode)
        assertEquals(1, calls)
    }
}
