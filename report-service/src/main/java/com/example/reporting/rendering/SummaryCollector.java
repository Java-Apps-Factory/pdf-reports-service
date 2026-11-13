package com.example.reporting.rendering;

import com.example.reporting.data.ReportCategory;
import com.example.reporting.pdf.SummaryData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Counts rows per category while they stream into the PDF, so the summary on page 1 always matches the
 * tables (AD-05). Every active category starts at zero (FR-05).
 */
public final class SummaryCollector {

    private final List<ReportCategory> categories;
    private final Map<String, long[]> counts = new LinkedHashMap<>();
    private long total;

    /**
     * Creates a collector for the state's active categories.
     *
     * @param categories categories in display order
     */
    public SummaryCollector(List<ReportCategory> categories) {
        this.categories = List.copyOf(categories);
        for (ReportCategory category : this.categories) {
            counts.put(category.code(), new long[1]);
        }
    }

    /**
     * Counts one rendered row.
     *
     * @param categoryCode the row's category
     * @throws IllegalArgumentException for a category that is not active
     */
    public void add(String categoryCode) {
        long[] count = counts.get(categoryCode);
        if (count == null) {
            throw new IllegalArgumentException("Category " + categoryCode + " is not active");
        }
        count[0]++;
        total++;
    }

    /**
     * Returns the rows counted for a category.
     *
     * @param categoryCode category code
     * @return count, 0 for unknown categories
     */
    public long count(String categoryCode) {
        long[] count = counts.get(categoryCode);
        return count == null ? 0 : count[0];
    }

    /**
     * Returns all rows counted.
     *
     * @return total
     */
    public long total() {
        return total;
    }

    /**
     * Builds the summary table: one row per active category in display order, zeros included.
     *
     * @param label summary label of a category
     * @return summary data
     */
    public SummaryData toSummary(Function<ReportCategory, String> label) {
        SummaryData.Builder builder = SummaryData.builder();
        for (ReportCategory category : categories) {
            builder.addRow(label.apply(category), count(category.code()));
        }
        return builder.build();
    }
}
