package com.twomemory.app

import com.twomemory.database.OutboxOperationEntity
import com.twomemory.designsystem.MoonLetterRecordStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailDeliveryStatusTest {
    private fun operation(action: String) = OutboxOperationEntity("op", "couple", "entry", action, "{}", 0)
    @Test fun creatingAPrivateDraftDoesNotEnableResponses() {
        assertEquals(MoonLetterRecordStatus.DRAFT, detailDeliveryStatus("DRAFT", listOf(operation("CREATE_ENTRY"))))
    }
    @Test fun anExplicitPublishInTheQueueKeepsResponsesAvailable() {
        assertEquals(MoonLetterRecordStatus.PENDING_SYNC, detailDeliveryStatus("DRAFT", listOf(operation("CREATE_ENTRY"), operation("PUBLISH_ENTRY"))))
    }
    @Test fun publishedWithAnEmptyQueueIsConfirmed() {
        assertEquals(MoonLetterRecordStatus.SYNCED, detailDeliveryStatus("PUBLISHED", emptyList()))
    }
}
