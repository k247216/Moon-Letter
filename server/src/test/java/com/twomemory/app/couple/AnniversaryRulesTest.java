package com.twomemory.app.couple;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnniversaryRulesTest {

    @Test
    void normalizesTitleAndDefaultsToYearly() {
        AnniversaryService.NormalizedAnniversary normalized = AnniversaryService.normalize(
                new SaveAnniversaryRequest("  相遇日  ", "gregorian", 10, 6, null, 2026, null));

        assertThat(normalized.title()).isEqualTo("相遇日");
        assertThat(normalized.calendarType()).isEqualTo(CalendarType.GREGORIAN);
        assertThat(normalized.repeatsYearly()).isTrue();
    }

    @Test
    void acceptsLeapDayAsARecurringGregorianRule() {
        AnniversaryService.NormalizedAnniversary normalized = AnniversaryService.normalize(
                new SaveAnniversaryRequest("纪念日", "GREGORIAN", 2, 29, false, 2024, true));

        assertThat(normalized.day()).isEqualTo(29);
    }

    @Test
    void rejectsInvalidMonthAndLunarFlagCombinations() {
        assertThatThrownBy(() -> AnniversaryService.normalize(
                new SaveAnniversaryRequest("纪念日", "GREGORIAN", 13, 1, false, null, true)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> AnniversaryService.normalize(
                new SaveAnniversaryRequest("纪念日", "GREGORIAN", 10, 6, true, null, true)))
                .isInstanceOf(ValidationException.class);
    }
}
