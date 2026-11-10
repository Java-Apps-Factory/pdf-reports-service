package com.example.reporting.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DevRecordGeneratorTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 11, 10);
    private static final int RECORDS = 5000;

    @Test
    void spreadsRecordsUnevenlyOverTwentyFipsCodesWithOneLargeCode() {
        Map<String, Integer> countsByFips = new HashMap<>();
        DevRecordGenerator generator = new DevRecordGenerator(42L, BASE_DATE);
        for (int i = 0; i < RECORDS; i++) {
            countsByFips.merge((String) generator.next()[1], 1, Integer::sum);
        }

        assertThat(countsByFips).hasSize(20);
        assertThat(countsByFips.keySet()).allMatch(code -> code.matches("51\\d{3}"));
        int largest = countsByFips.get(DevRecordGenerator.LARGE_FIPS);
        assertThat(largest).isGreaterThan(RECORDS / 4);
        assertThat(countsByFips.values()).allMatch(count -> count <= largest);
    }

    @Test
    void leavesOneCategoryEmptyForTheEmptyCategoryFipsCode() {
        Set<String> categories = new HashSet<>();
        DevRecordGenerator generator = new DevRecordGenerator(42L, BASE_DATE);
        for (int i = 0; i < RECORDS; i++) {
            Object[] row = generator.next();
            if (DevRecordGenerator.EMPTY_CATEGORY_FIPS.equals(row[1])) {
                categories.add((String) row[2]);
            }
        }

        assertThat(categories).containsExactlyInAnyOrder("CAT1", "CAT2", "CAT4");
    }

    @Test
    void isDeterministicAndProducesUniqueCaseNumbers() {
        DevRecordGenerator first = new DevRecordGenerator(42L, BASE_DATE);
        DevRecordGenerator second = new DevRecordGenerator(42L, BASE_DATE);
        Set<Object> caseNumbers = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            Object[] row = first.next();
            assertThat(row).containsExactly(second.next());
            caseNumbers.add(row[3]);
        }

        assertThat(caseNumbers).hasSize(500);
    }

    @Test
    void producesOneParameterPerInsertPlaceholder() {
        long placeholders = DevRecordGenerator.insertSql().chars().filter(c -> c == '?').count();

        assertThat(new DevRecordGenerator(1L, BASE_DATE).next()).hasSize((int) placeholders);
    }
}
