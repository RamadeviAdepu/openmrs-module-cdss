package org.bahmni.module.fhircdss.api.util;

import org.hl7.fhir.r4.model.Timing;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FrequencyTest {

    private void assertResolved(String frequencyText, int expectedCount, int expectedPeriod, String expectedUnit) {
        assertResolved(frequencyText, expectedCount, expectedPeriod, expectedUnit, null);
    }

    private void assertResolved(String frequencyText, int expectedCount, int expectedPeriod, String expectedUnit, Timing.EventTiming expectedWhen) {
        Frequency.FrequencyValue value = Frequency.resolveFrequency(frequencyText);
        assertEquals(expectedCount, value.getFrequencyCount());
        assertEquals(expectedPeriod, value.getPeriodCount());
        assertEquals(expectedUnit, value.getPeriodUnit());
        assertEquals(expectedWhen, value.getWhen());
    }

    @Test
    public void shouldResolveAllExistingEnumFrequenciesToTheirOriginalValues() {
        for (Frequency frequency : Frequency.values()) {
            assertResolved(frequency.getFrequencyText(), frequency.getFrequencyCount(), frequency.getPeriodCount(), frequency.getPeriodUnit());
        }
    }

    @Test
    public void shouldResolveUnsupportedEnvironmentFrequencies() {
        assertResolved("Every 30 minutes", 1, 30, "min");
        assertResolved("Four times a day / Every 6 hours", 4, 1, "d");
        assertResolved("In Morning", 1, 1, "d", Timing.EventTiming.MORN);
        assertResolved("In Afternoon", 1, 1, "d", Timing.EventTiming.AFT);
        assertResolved("Nocte (At Night)", 1, 1, "d", Timing.EventTiming.NIGHT);
        assertResolved("STAT (Immediately)", 1, 1, "d");
        assertResolved("Three times a day / Every 8 hours", 3, 1, "d");
        assertResolved("Three times a week", 3, 1, "wk");
        assertResolved("Twice a day / Every 12 hours", 2, 1, "d");
    }

    @Test
    public void shouldResolveFuturePatternFrequencies() {
        assertResolved("Every 30 minutes", 1, 30, "min");
        assertResolved("Every 90 minutes", 1, 90, "min");
        assertResolved("Every 2 hours", 1, 2, "h");
        assertResolved("Every 3 days", 1, 3, "d");
        assertResolved("Every 2 weeks", 1, 2, "wk");
        assertResolved("Every 4 months", 1, 4, "mo");
        assertResolved("Every 1 year", 1, 1, "a");
        assertResolved("Seven times a day", 7, 1, "d");
        assertResolved("Three times a week", 3, 1, "wk");
        assertResolved("Four times a week", 4, 1, "wk");
        assertResolved("Seven days a week", 7, 1, "wk");
        assertResolved("Five times a day / Every 4 hours", 5, 1, "d");
    }

    @Test
    public void shouldHandleNamingVariations() {
        assertResolved("  SEVEN TIMES   A DAY  ", 7, 1, "d");
        assertResolved("every    30   minutes", 1, 30, "min");
        assertResolved(" Twice a day ", 2, 1, "d");
        assertResolved("in   morning", 1, 1, "d", Timing.EventTiming.MORN);
        assertResolved("  Nocte (At Night)  ", 1, 1, "d", Timing.EventTiming.NIGHT);
        assertResolved("stat", 1, 1, "d");
    }

    @Test
    public void shouldReturnNullForNullAndEmptyInput() {
        assertNull(Frequency.resolveFrequency(null));
        assertNull(Frequency.resolveFrequency(""));
        assertNull(Frequency.resolveFrequency("   "));
    }

    @Test
    public void shouldReturnNullForUnknownFrequencyWithoutThrowing() {
        assertNull(Frequency.resolveFrequency("Some Future Frequency"));
        assertNull(Frequency.resolveFrequency("With breakfast"));
        assertNull(Frequency.resolveFrequency("As directed"));
    }
}