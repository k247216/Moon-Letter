package com.twomemory.app.entry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryServiceTest {

    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void textUsesUnicodeCodePointLimit() {
        assertThat(EntryService.validateText("  春天  ")).isEqualTo("春天");
        assertThatThrownBy(() -> EntryService.validateText("a".repeat(20_001)))
                .isInstanceOf(EntryValidationException.class);
    }

    @Test
    void blocksKeepStableOrderAndRejectDuplicates() {
        List<BlockMutation> sorted = EntryService.stableOrder(List.of(
                new BlockMutation(SECOND, BlockType.TEXT, 20, FIRST, "{\"text\":\"二\"}", null, false),
                new BlockMutation(FIRST, BlockType.TEXT, 10, SECOND, "{\"text\":\"一\"}", null, false)));
        assertThat(sorted).extracting(BlockMutation::orderKey).containsExactly(10L, 20L);
        assertThatThrownBy(() -> EntryService.stableOrder(List.of(
                new BlockMutation(FIRST, BlockType.TEXT, 10, FIRST, "{\"text\":\"一\"}", null, false),
                new BlockMutation(SECOND, BlockType.TEXT, 10, SECOND, "{\"text\":\"二\"}", null, false))))
                .isInstanceOf(EntryValidationException.class);
    }

    @Test
    void differentBlocksAutoMergeButSameBlockConflicts() {
        assertThat(EntryService.changedBlockIds(
                List.of(FIRST), List.of(SECOND))).doesNotContain(FIRST);
        assertThatThrownBy(() -> EntryService.requireNoOverlappingChanges(
                List.of(FIRST), List.of(FIRST)))
                .isInstanceOf(EntryConflict.class);
    }

    @Test
    void commentBodyUsesOneToTwoThousandCodePoints() {
        assertThat(CommentService.validateBody("  好呀  ")).isEqualTo("好呀");
        assertThatThrownBy(() -> CommentService.validateBody("x".repeat(2_001)))
                .isInstanceOf(EntryValidationException.class);
    }
}
