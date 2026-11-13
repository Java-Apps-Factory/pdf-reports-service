package com.example.reporting.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reporting.data.ReportCategory;
import com.example.reporting.pdf.SummaryData;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SummaryCollectorTest {

    private final List<ReportCategory> categories = List.of(new ReportCategory("CAT1", "Fees", 1),
            new ReportCategory("CAT2", "Permits", 2), new ReportCategory("CAT3", "Liens", 3));

    @Test
    void countsPerCategoryIncludingZerosInDisplayOrder() {
        SummaryCollector collector = new SummaryCollector(categories);
        collector.add("CAT1");
        collector.add("CAT3");
        collector.add("CAT3");

        SummaryData summary = collector.toSummary(DocumentSpecFactory::categoryLabel);
        assertThat(summary.rows().stream().map(r -> r.label() + "=" + r.count()).collect(Collectors.toList()))
                .containsExactly("Category 1 – Fees=1", "Category 2 – Permits=0",
                        "Category 3 – Liens=2");
        assertThat(summary.total()).isEqualTo(3).isEqualTo(collector.total());
        assertThat(collector.count("CAT9")).isZero();
    }

    @Test
    void rejectsInactiveCategories() {
        assertThatThrownBy(() -> new SummaryCollector(categories).add("CAT9"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
