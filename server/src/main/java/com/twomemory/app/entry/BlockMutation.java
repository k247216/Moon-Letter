package com.twomemory.app.entry;

import java.util.UUID;

public record BlockMutation(UUID blockId, BlockType type, long orderKey, UUID authorId,
                            String payload, UUID assetId, boolean deleted) {
}
