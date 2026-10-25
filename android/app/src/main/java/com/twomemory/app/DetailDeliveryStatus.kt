package com.twomemory.app

import com.twomemory.database.OutboxOperationEntity
import com.twomemory.designsystem.MoonLetterRecordStatus
import com.twomemory.model.QueuedOperation
import com.twomemory.model.entrySyncPhase

/** Queue delivery is distinct from whether the author chose to publish. */
internal fun detailDeliveryStatus(state: String, operations: List<OutboxOperationEntity>): MoonLetterRecordStatus =
    if (state != "PUBLISHED" && operations.none { it.action == "PUBLISH_ENTRY" }) MoonLetterRecordStatus.DRAFT
    else recordStatusOf(entrySyncPhase(operations.map { QueuedOperation(it.state, it.attemptCount) }))
