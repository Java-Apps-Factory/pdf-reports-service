package com.example.reporting.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.reporting.data.ReferenceDataRepository;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/** Every startup check of SPEC section 16, one per test. */
class ProfileValidatorTest {

    private static final Map<String, Boolean> VA_ROW = Map.of("VA", true);
    private static final Map<String, Set<String>> VA_CATEGORIES = Map.of("VA", Set.of("CAT1", "CAT2", "CAT3", "CAT4"));

    private final ReferenceDataRepository referenceData = mock(ReferenceDataRepository.class);
    private final ProfileValidator validator = new ProfileValidator(referenceData, new DefaultResourceLoader());

    @Test
    void acceptsTheShippedVirginiaProfile() {
        assertThat(problems(TestProfiles.virginia())).isEmpty();
    }

    @Test
    void rejectsWidthsThatDoNotSumTo100() {
        List<ColumnDefinition> columns = new ArrayList<>(TestProfiles.virginiaColumns());
        columns.set(0, new ColumnDefinition("caseNumber", "Case Number", ColumnType.TEXT, 15, false));
        assertThat(problems(withColumns(columns))).singleElement().asString()
                .contains("reporting.states.VA.columns", "widths sum to 101");
    }

    @Test
    void rejectsKeysTheRowMapperDoesNotProduce() {
        List<ColumnDefinition> columns = new ArrayList<>(TestProfiles.virginiaColumns());
        columns.set(5, new ColumnDefinition("socialSecurity", "SSN", ColumnType.TEXT, 10, false));
        assertThat(problems(withColumns(columns))).singleElement().asString()
                .contains("key socialSecurity is not produced by ReportRowMapper");
    }

    @Test
    void rejectsColumnTypesThatDoNotFitTheValues() {
        List<ColumnDefinition> columns = new ArrayList<>(TestProfiles.virginiaColumns());
        columns.set(4, new ColumnDefinition("amount", "Amount", ColumnType.DATE, 11, false));
        assertThat(problems(withColumns(columns))).singleElement().asString()
                .contains("key amount holds BigDecimal values and cannot be shown as DATE");
    }

    @Test
    void rejectsMissingLogoAndMissingAltText() {
        assertThat(problems(TestProfiles.profile(TestProfiles.virginiaColumns(), Map.of(),
                "classpath:branding/missing.png", " ")))
                .hasSize(2)
                .anySatisfy(p -> assertThat(p).contains("logo.alt-text is missing"))
                .anySatisfy(p -> assertThat(p).contains("logo.resource classpath:branding/missing.png does not exist"));
    }

    @Test
    void rejectsAnEnabledStateWithoutReferenceRow() {
        List<String> problems = validator.problems(TestProfiles.properties(
                Map.of("VA", TestProfiles.virginia(), "MD", TestProfiles.virginia()), Duration.ofMinutes(15),
                Paths.get("tmp")), VA_ROW, VA_CATEGORIES);
        assertThat(problems).singleElement().asString().contains("reporting.states.MD is enabled",
                "not in RPT_STATE");
    }

    @Test
    void rejectsAnActiveStateRowWithoutProfile() {
        List<String> problems = validator.problems(TestProfiles.virginiaProperties(Paths.get("tmp")),
                Map.of("VA", true, "NC", true, "SC", false), VA_CATEGORIES);
        assertThat(problems).singleElement().asString().contains("Active state NC in RPT_STATE has no profile");
    }

    @Test
    void rejectsALeaseOfFiveMinutesOrLess() {
        List<String> problems = validator.problems(TestProfiles.properties(Map.of("VA", TestProfiles.virginia()),
                Duration.ofMinutes(5), Paths.get("tmp")), VA_ROW, VA_CATEGORIES);
        assertThat(problems).singleElement().asString().contains("lease-duration must be longer than 5 minutes");
    }

    @Test
    void checksCategoryOverridesLikeDefaultColumns() {
        List<ColumnDefinition> narrow = List.of(new ColumnDefinition("caseNumber", "Case", ColumnType.TEXT, 50, false),
                new ColumnDefinition("amount", "Amount", ColumnType.CURRENCY, 40, false));
        StateProfile profile = TestProfiles.profile(TestProfiles.virginiaColumns(),
                Map.of("CAT2", narrow, "CAT9", TestProfiles.virginiaColumns()), TestProfiles.LOGO, "Logo");
        assertThat(problems(profile)).hasSize(2)
                .anySatisfy(p -> assertThat(p).contains("category-columns.CAT2", "widths sum to 90"))
                .anySatisfy(p -> assertThat(p).contains("category-columns.CAT9 names a category that is not active"));
    }

    @Test
    void validateThrowsWithEveryProblemListed() {
        when(referenceData.states()).thenReturn(Map.of("VA", true, "NC", true));
        when(referenceData.activeCategoryCodesByState()).thenReturn(VA_CATEGORIES);
        assertThatThrownBy(() -> validator.validate(TestProfiles.properties(Map.of("VA", TestProfiles.virginia()),
                Duration.ofMinutes(1), Paths.get("tmp"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid reporting configuration")
                .hasMessageContaining("lease-duration")
                .hasMessageContaining("Active state NC");
    }

    @Test
    void validatePassesForAValidConfiguration() {
        when(referenceData.states()).thenReturn(VA_ROW);
        when(referenceData.activeCategoryCodesByState()).thenReturn(VA_CATEGORIES);
        validator.validate(TestProfiles.virginiaProperties(Paths.get("tmp")));
    }

    @Test
    void onlyTextColumnsWrap() {
        assertThat(TestProfiles.column("lastName", ColumnType.TEXT, 10, null).isWrap()).isTrue();
        assertThat(TestProfiles.column("lastName", ColumnType.TEXT, 10, false).isWrap()).isFalse();
        assertThat(TestProfiles.column("amount", ColumnType.CURRENCY, 10, true).isWrap()).isFalse();
        assertThat(TestProfiles.virginia().columnsFor("CAT1")).isEqualTo(TestProfiles.virginiaColumns());
    }

    private List<String> problems(StateProfile profile) {
        return validator.problems(TestProfiles.properties(Map.of("VA", profile), Duration.ofMinutes(15),
                Paths.get("tmp")), VA_ROW, VA_CATEGORIES);
    }

    private static StateProfile withColumns(List<ColumnDefinition> columns) {
        return TestProfiles.profile(columns, Map.of(), TestProfiles.LOGO, "Logo");
    }
}
