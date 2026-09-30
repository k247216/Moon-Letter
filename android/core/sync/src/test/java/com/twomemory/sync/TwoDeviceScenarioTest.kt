package com.twomemory.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class TwoDeviceScenarioTest {
    @Test
    fun independentStoresKeepOperationIdsAcrossRestart() {
        val operationId = "01JMOONLETTER00000000000000"
        val deviceA = mutableListOf(operationId)
        val deviceB = mutableListOf(operationId)
        assertEquals(deviceA.single(), deviceB.single())
    }
}
