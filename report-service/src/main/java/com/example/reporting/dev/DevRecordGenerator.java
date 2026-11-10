package com.example.reporting.dev;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Random;

/**
 * Produces deterministic fake source records for development and tests.
 *
 * <p>Records spread unevenly over 20 Virginia FIPS codes. {@link #LARGE_FIPS} receives about 40%
 * of all records (paging tests) and {@link #EMPTY_CATEGORY_FIPS} never receives
 * {@link #EMPTY_CATEGORY} (empty-section tests). All names are obviously fake.
 */
final class DevRecordGenerator {

    static final String STATE_CODE = "VA";
    static final String LARGE_FIPS = "51059";
    static final String EMPTY_CATEGORY_FIPS = "51013";
    static final String EMPTY_CATEGORY = "CAT3";

    private static final String[] FIPS_CODES = {
        LARGE_FIPS, EMPTY_CATEGORY_FIPS, "51107", "51153", "51760", "51810", "51710", "51041", "51087", "51550",
        "51700", "51510", "51650", "51177", "51179", "51003", "51161", "51770", "51680", "51840"
    };
    private static final int[] WEIGHTS = {40, 8, 7, 6, 6, 5, 4, 4, 3, 3, 3, 2, 2, 2, 1, 1, 1, 1, 1, 1};
    private static final String[] CATEGORIES = {"CAT1", "CAT2", EMPTY_CATEGORY, "CAT4"};
    private static final String[] LAST_NAMES = {
        "Testerson", "Sampleton", "Placeholder", "Exampleworth", "Mockingham", "Demoville", "Fixture", "Dummy",
        "Fakenham", "Specimen", "Stubbs-Mockley", "Loremipsum"
    };
    private static final String[] FIRST_NAMES = {
        "Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India", "Juliett"
    };
    private static final String[] STATUSES = {"OPEN", "CLOSED", "PENDING", "REVIEW"};
    private static final String[] DESCRIPTIONS = {
        "Sample record.",
        "Generated development record used to exercise table layout and text wrapping.",
        "Long sample description. This text is intentionally verbose so that it wraps over several lines in "
                + "the description column and makes row heights vary from one row to the next across a page.",
    };
    private static final int EVENT_DATE_SPREAD_DAYS = 1825;
    private static final int MAX_AMOUNT_CENTS = 5_000_000;

    private final Random random;
    private final LocalDate baseDate;
    private final int totalWeight;
    private long sequence;

    DevRecordGenerator(long seed, LocalDate baseDate) {
        this.random = new Random(seed);
        this.baseDate = baseDate;
        int sum = 0;
        for (int weight : WEIGHTS) {
            sum += weight;
        }
        this.totalWeight = sum;
    }

    /** SQL matching the parameter order of {@link #next()}. */
    static String insertSql() {
        return "INSERT INTO RPT_RECORD (STATE_CODE, FIPS_CODE, CATEGORY_CODE, CASE_NUMBER, LAST_NAME, FIRST_NAME, "
                + "EVENT_DATE, AMOUNT, STATUS, DESCRIPTION) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    /** Returns the bind parameters of the next record, in {@link #insertSql()} order. */
    Object[] next() {
        sequence++;
        String fipsCode = pickFipsCode();
        return new Object[] {
            STATE_CODE,
            fipsCode,
            pickCategory(fipsCode),
            String.format("VA-%08d", sequence),
            pick(LAST_NAMES),
            pick(FIRST_NAMES),
            Date.valueOf(baseDate.minusDays(random.nextInt(EVENT_DATE_SPREAD_DAYS))),
            pickAmount(),
            pick(STATUSES),
            random.nextInt(10) == 0 ? null : pick(DESCRIPTIONS),
        };
    }

    private String pickFipsCode() {
        int roll = random.nextInt(totalWeight);
        for (int i = 0; i < FIPS_CODES.length; i++) {
            roll -= WEIGHTS[i];
            if (roll < 0) {
                return FIPS_CODES[i];
            }
        }
        return FIPS_CODES[0];
    }

    private String pickCategory(String fipsCode) {
        String category = pick(CATEGORIES);
        if (EMPTY_CATEGORY_FIPS.equals(fipsCode) && EMPTY_CATEGORY.equals(category)) {
            return CATEGORIES[0];
        }
        return category;
    }

    private BigDecimal pickAmount() {
        int roll = random.nextInt(20);
        if (roll == 0) {
            return null;
        }
        BigDecimal amount = BigDecimal.valueOf(random.nextInt(MAX_AMOUNT_CENTS), 2);
        return roll == 1 ? amount.negate() : amount;
    }

    private String pick(String[] values) {
        return values[random.nextInt(values.length)];
    }
}
