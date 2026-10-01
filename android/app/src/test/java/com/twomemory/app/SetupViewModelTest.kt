package com.twomemory.app

import com.twomemory.network.BootstrapResultDto
import com.twomemory.network.CoupleViewDto
import com.twomemory.network.MemberViewDto
import com.twomemory.network.PairResultDto
import com.twomemory.network.PairingTokenResultDto
import com.twomemory.network.ProfileViewDto
import com.twomemory.network.SessionApi
import com.twomemory.network.SpaceViewDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM-level evidence for the binding screen: she names herself before the
 * space lets her in, and that name is what the server stores for her.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private companion object {
        val COUPLE: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010")
        val OWNER: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val PARTNER: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    }

    private class FakeSessionApi : SessionApi {
        var pairedWith: String? = null
        var renamedTo: String? = null

        override suspend fun bootstrap(baseUrl: String, secret: String, displayName: String) =
            BootstrapResultDto(OWNER, COUPLE, "owner-token")

        override suspend fun pairingToken(baseUrl: String, bearer: String, coupleId: UUID) =
            PairingTokenResultDto(CoupleViewDto(coupleId), "pairing-token")

        override suspend fun pair(baseUrl: String, pairingToken: String, displayName: String): PairResultDto {
            pairedWith = displayName
            return PairResultDto(CoupleViewDto(COUPLE), "partner-token", PARTNER)
        }

        override suspend fun readSpace(baseUrl: String, bearer: String, coupleId: UUID) =
            SpaceViewDto(COUPLE, listOf(MemberViewDto(OWNER, ProfileViewDto("小满"))))

        override suspend fun updateOwnProfile(
            baseUrl: String,
            bearer: String,
            coupleId: UUID,
            userId: UUID,
            displayName: String,
        ): ProfileViewDto {
            renamedTo = displayName
            return ProfileViewDto(displayName)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun thePartnerCannotBindUntilSheHasNamedHerself() {
        val viewModel = SetupViewModel(FakeSessionApi())
        viewModel.updateMode(SetupViewModel.Mode.PARTNER_DEVICE)
        viewModel.updatePairingToken("a".repeat(43))
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.updateDisplayName("阿屿")
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun pairingCarriesTheNameSheTyped() = runTest {
        val api = FakeSessionApi()
        val viewModel = SetupViewModel(api)
        viewModel.updateMode(SetupViewModel.Mode.PARTNER_DEVICE)
        viewModel.updatePairingToken("a".repeat(43))
        viewModel.updateDisplayName("  阿屿  ")

        viewModel.pair()

        assertEquals("阿屿", api.pairedWith)
        assertEquals(SetupViewModel.Phase.BOUND, viewModel.state.value.phase)
        assertEquals(PARTNER, assertNotNull(viewModel.state.value.boundSession).userId)
    }
}
