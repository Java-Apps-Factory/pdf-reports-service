package com.example.reporting.tracking;

import static com.example.reporting.tracking.DocumentStatus.DATA_ERROR;
import static com.example.reporting.tracking.DocumentStatus.GENERATED;
import static com.example.reporting.tracking.DocumentStatus.GENERATING;
import static com.example.reporting.tracking.DocumentStatus.GENERATION_FAILED;
import static com.example.reporting.tracking.DocumentStatus.GENERATION_FAILED_FINAL;
import static com.example.reporting.tracking.DocumentStatus.PENDING;
import static com.example.reporting.tracking.DocumentStatus.SENDING;
import static com.example.reporting.tracking.DocumentStatus.SEND_FAILED;
import static com.example.reporting.tracking.DocumentStatus.SEND_FAILED_FINAL;
import static com.example.reporting.tracking.DocumentStatus.SENT;
import static com.example.reporting.tracking.DocumentStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Every allowed and every forbidden pair of the SPEC section 9 state machine. */
class DocumentStatusTest {

    private static final Map<DocumentStatus, Set<DocumentStatus>> ALLOWED = allowed();

    static Stream<Arguments> everyPair() {
        List<Arguments> pairs = new ArrayList<>();
        for (DocumentStatus from : DocumentStatus.values()) {
            for (DocumentStatus to : DocumentStatus.values()) {
                pairs.add(Arguments.of(from, to, ALLOWED.get(from).contains(to)));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @MethodSource("everyPair")
    void transitionMatchesSection9(DocumentStatus from, DocumentStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
        if (allowed) {
            from.requireTransitionTo(to);
        } else {
            assertThatThrownBy(() -> from.requireTransitionTo(to)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(from + " -> " + to);
        }
    }

    @Test
    void finalAndInitialStatuses() {
        assertThat(DocumentStatus.finalStatuses())
                .containsExactlyInAnyOrder(GENERATION_FAILED_FINAL, DATA_ERROR, SEND_FAILED_FINAL, SENT, SUPERSEDED);
        assertThat(SENT.isFinal()).isTrue();
        assertThat(GENERATED.isFinal()).isFalse();
        assertThat(PENDING.isInitial()).isTrue();
        assertThat(DATA_ERROR.isInitial()).isTrue();
        assertThat(GENERATING.isInitial()).isFalse();
    }

    @Test
    void onlyManualRetransmitLeavesAFinalStatus() {
        for (DocumentStatus status : DocumentStatus.finalStatuses()) {
            Set<DocumentStatus> exits = EnumSet.noneOf(DocumentStatus.class);
            for (DocumentStatus to : DocumentStatus.values()) {
                if (status.canTransitionTo(to)) {
                    exits.add(to);
                }
            }
            assertThat(exits).as(status.name())
                    .isEqualTo(status == SEND_FAILED_FINAL
                            ? EnumSet.of(SEND_FAILED) : EnumSet.noneOf(DocumentStatus.class));
        }
    }

    private static Map<DocumentStatus, Set<DocumentStatus>> allowed() {
        Map<DocumentStatus, Set<DocumentStatus>> map = new EnumMap<>(DocumentStatus.class);
        for (DocumentStatus status : DocumentStatus.values()) {
            map.put(status, EnumSet.noneOf(DocumentStatus.class));
        }
        map.put(PENDING, EnumSet.of(GENERATING));
        map.put(GENERATION_FAILED, EnumSet.of(GENERATING));
        map.put(GENERATING, EnumSet.of(GENERATED, DATA_ERROR, GENERATION_FAILED, GENERATION_FAILED_FINAL));
        map.put(GENERATED, EnumSet.of(SENDING));
        map.put(SEND_FAILED, EnumSet.of(SENDING, SEND_FAILED));
        map.put(SENDING, EnumSet.of(SUPERSEDED, SENT, SEND_FAILED, SEND_FAILED_FINAL));
        map.put(SEND_FAILED_FINAL, EnumSet.of(SEND_FAILED));
        return map;
    }
}
