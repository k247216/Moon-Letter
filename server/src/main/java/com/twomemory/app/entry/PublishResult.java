package com.twomemory.app.entry;

/**
 * Outcome of publishing a draft: the reconstructed entry and the revision number
 * it was stored under. Public because the sync dispatcher names it from another
 * package for the PUBLISH_ENTRY operation.
 */
public record PublishResult(EntryView entry, int revisionNo) {
}
