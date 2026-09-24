package org.bahmni.module.fhircdss.api.util;

import org.hl7.fhir.r4.model.Timing;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum Frequency {
    IMMEDIATELY("Immediately", 1, 1, "d"),
    ONCE_A_DAY("Once a day", 1, 1, "d"),
    TWICE_A_DAY("Twice a day", 2, 1, "d"),
    THRICE_A_DAY("Thrice a day", 3, 1, "d"),
    FOUR_TIMES_A_DAY("Four times a day", 4, 1, "d"),
    EVERY_HOUR("Every Hour", 1, 1, "h"),
    EVERY_2_HOURS("Every 2 hours", 1, 2, "h"),
    EVERY_3_HOURS("Every 3 hours", 1, 3, "h"),
    EVERY_4_HOURS("Every 4 hours", 1, 4, "h"),
    EVERY_6_HOURS("Every 6 hours", 1, 6, "h"),
    EVERY_8_HOURS("Every 8 hours", 1, 8, "h"),
    EVERY_12_HOURS("Every 12 hours", 1, 12, "h"),
    ON_ALTERNATE_DAYS("On alternate days", 1, 2, "d"),
    ONCE_A_WEEK("Once a week", 1, 1, "wk"),
    TWICE_A_WEEK("Twice a week", 2, 1, "wk"),
    THRICE_A_WEEK("Thrice a week", 3, 1, "wk"),
    EVERY_2_WEEKS("Every 2 weeks", 1, 2, "wk"),
    EVERY_3_WEEKS("Every 3 weeks", 1, 3, "wk"),
    ONCE_A_MONTH("Once a month", 1, 1, "mo"),
    FIVE_TIMES_A_DAY("Five times a day", 5, 1, "d"),
    FOUR_DAYS_A_WEEK("Four days a week", 4, 1, "wk"),
    FIVE_DAYS_A_WEEK("Five days a week", 5, 1, "wk"),
    SIX_DAYS_A_WEEK("Six days a week", 6, 1, "wk");

    private static final Map<String, Frequency> BY_TEXT = new HashMap<>();
    private static final Map<String, Frequency> BY_NORMALIZED_TEXT = new HashMap<>();
    private static final Map<String, FrequencyValue> ALIASES = new HashMap<>();
    private static final Map<String, Integer> NUMBER_WORDS = new HashMap<>();

    private static final String INTERVAL_UNIT = "(minutes?|mins|min|hours?|hrs?|h|days?|d|weeks?|wks?|wk|months?|mo|years?|yrs?|yr)";
    private static final Pattern EVERY_N_UNIT = Pattern.compile("^every (\\d+|\\w+) " + INTERVAL_UNIT + "$");
    private static final Pattern N_TIMES_A_DAY = Pattern.compile("^(\\d+|\\w+) times a day$");
    private static final Pattern N_TIMES_A_WEEK = Pattern.compile("^(\\d+|\\w+) times a week$");
    private static final Pattern N_DAYS_A_WEEK = Pattern.compile("^(\\d+|\\w+) days a week$");
    private static final Pattern N_TIMES_A_DAY_EVERY_UNIT = Pattern.compile("^(\\d+|\\w+) times a day / every (\\d+|\\w+) " + INTERVAL_UNIT + "$");

    public final String frequencyText;
    public final int frequencyCount;
    public final int periodCount;
    public final String periodUnit;

    static {
        NUMBER_WORDS.put("one", 1);
        NUMBER_WORDS.put("two", 2);
        NUMBER_WORDS.put("three", 3);
        NUMBER_WORDS.put("four", 4);
        NUMBER_WORDS.put("five", 5);
        NUMBER_WORDS.put("six", 6);
        NUMBER_WORDS.put("seven", 7);
        NUMBER_WORDS.put("eight", 8);
        NUMBER_WORDS.put("nine", 9);
        NUMBER_WORDS.put("ten", 10);
        NUMBER_WORDS.put("eleven", 11);
        NUMBER_WORDS.put("twelve", 12);

        for (Frequency e : values()) {
            BY_TEXT.put(e.frequencyText, e);
            BY_NORMALIZED_TEXT.put(normalize(e.frequencyText), e);
        }
        ALIASES.put("stat", frequencyValueOf(IMMEDIATELY));
        ALIASES.put("stat (immediately)", frequencyValueOf(IMMEDIATELY));
        ALIASES.put("in morning", new FrequencyValue(1, 1, "d", Timing.EventTiming.MORN));
        ALIASES.put("in afternoon", new FrequencyValue(1, 1, "d", Timing.EventTiming.AFT));
        ALIASES.put("nocte", new FrequencyValue(1, 1, "d", Timing.EventTiming.NIGHT));
        ALIASES.put("nocte (at night)", new FrequencyValue(1, 1, "d", Timing.EventTiming.NIGHT));
        ALIASES.put("at night", new FrequencyValue(1, 1, "d", Timing.EventTiming.NIGHT));
        ALIASES.put("three times a day", frequencyValueOf(THRICE_A_DAY));
        ALIASES.put("three times a week", frequencyValueOf(THRICE_A_WEEK));
        ALIASES.put("twice a day / every 12 hours", frequencyValueOf(TWICE_A_DAY));
        ALIASES.put("three times a day / every 8 hours", frequencyValueOf(THRICE_A_DAY));
        ALIASES.put("four times a day / every 6 hours", frequencyValueOf(FOUR_TIMES_A_DAY));
    }

    private Frequency(String frequencyText, int frequencyCount, int periodCount, String periodUnit) {
        this.frequencyText = frequencyText;
        this.frequencyCount = frequencyCount;
        this.periodCount = periodCount;
        this.periodUnit = periodUnit;
    }

    public static Frequency valueOfFrequency(String frequencyText) {
        return BY_TEXT.get(frequencyText);
    }

    public static FrequencyValue resolveFrequency(String frequencyText) {
        String normalized = normalize(frequencyText);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        Frequency exact = BY_NORMALIZED_TEXT.get(normalized);
        if (exact != null) {
            return frequencyValueOf(exact);
        }
        FrequencyValue alias = ALIASES.get(normalized);
        if (alias != null) {
            return alias;
        }
        try {
            return inferFromPattern(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static FrequencyValue inferFromPattern(String normalized) {
        FrequencyValue intervalWithTimes = matchNTimesInDayEveryInterval(normalized);
        if (intervalWithTimes != null) {
            return intervalWithTimes;
        }
        FrequencyValue everyInterval = matchEveryInterval(normalized);
        if (everyInterval != null) {
            return everyInterval;
        }
        FrequencyValue nTimesADay = matchNTimes(N_TIMES_A_DAY, "d", normalized);
        if (nTimesADay != null) {
            return nTimesADay;
        }
        FrequencyValue nTimesAWeek = matchNTimes(N_TIMES_A_WEEK, "wk", normalized);
        if (nTimesAWeek != null) {
            return nTimesAWeek;
        }
        return matchNTimes(N_DAYS_A_WEEK, "wk", normalized);
    }

    private static FrequencyValue matchNTimesInDayEveryInterval(String normalized) {
        Matcher matcher = N_TIMES_A_DAY_EVERY_UNIT.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        return new FrequencyValue(parseNumber(matcher.group(1)), 1, "d", null);
    }

    private static FrequencyValue matchEveryInterval(String normalized) {
        Matcher matcher = EVERY_N_UNIT.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        String unitCode = toUnitCode(matcher.group(2));
        if (unitCode == null) {
            return null;
        }
        return new FrequencyValue(1, parseNumber(matcher.group(1)), unitCode, null);
    }

    private static FrequencyValue matchNTimes(Pattern pattern, String unitCode, String normalized) {
        Matcher matcher = pattern.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        return new FrequencyValue(parseNumber(matcher.group(1)), 1, unitCode, null);
    }

    private static FrequencyValue frequencyValueOf(Frequency frequency) {
        return new FrequencyValue(frequency.getFrequencyCount(), frequency.getPeriodCount(), frequency.getPeriodUnit(), null);
    }

    private static String normalize(String frequencyText) {
        if (frequencyText == null) {
            return null;
        }
        return frequencyText.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ENGLISH);
    }

    private static int parseNumber(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            Integer number = NUMBER_WORDS.get(token);
            if (number == null) {
                throw new IllegalArgumentException("Unparseable frequency number: " + token);
            }
            return number;
        }
    }

    private static String toUnitCode(String unit) {
        String trimmed = unit.toLowerCase(Locale.ENGLISH);
        if (trimmed.startsWith("min")) {
            return "min";
        }
        if (trimmed.startsWith("h")) {
            return "h";
        }
        if (trimmed.startsWith("d")) {
            return "d";
        }
        if (trimmed.startsWith("w")) {
            return "wk";
        }
        if (trimmed.startsWith("mo")) {
            return "mo";
        }
        if (trimmed.startsWith("y")) {
            return "a";
        }
        return null;
    }

    public String getFrequencyText() {
        return frequencyText;
    }

    public int getFrequencyCount() {
        return frequencyCount;
    }

    public int getPeriodCount() {
        return periodCount;
    }

    public String getPeriodUnit() {
        return periodUnit;
    }

    public static final class FrequencyValue {
        private final int frequencyCount;
        private final int periodCount;
        private final String periodUnit;
        private final Timing.EventTiming when;

        private FrequencyValue(int frequencyCount, int periodCount, String periodUnit, Timing.EventTiming when) {
            this.frequencyCount = frequencyCount;
            this.periodCount = periodCount;
            this.periodUnit = periodUnit;
            this.when = when;
        }

        public int getFrequencyCount() {
            return frequencyCount;
        }

        public int getPeriodCount() {
            return periodCount;
        }

        public String getPeriodUnit() {
            return periodUnit;
        }

        public Timing.EventTiming getWhen() {
            return when;
        }
    }
}