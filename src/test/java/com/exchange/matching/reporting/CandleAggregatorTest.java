package com.exchange.matching.reporting;

import com.exchange.matching.dto.CandleResponse;
import com.exchange.matching.reporting.CandleAggregator.PricePoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandleAggregatorTest {

    private static PricePoint at(String time, String price, String qty) {
        return new PricePoint(Instant.parse(time), new BigDecimal(price), new BigDecimal(qty));
    }

    @Test
    void regroupe_les_executions_par_minute_en_chandeliers_ohlc() {
        List<CandleResponse> candles = CandleAggregator.aggregate(List.of(
            at("2026-10-27T10:00:05Z", "100.00", "2"),
            at("2026-10-27T10:00:30Z", "103.00", "1"),
            at("2026-10-27T10:00:50Z", "99.00", "4"),
            at("2026-10-27T10:00:59Z", "101.00", "3"),
            at("2026-10-27T10:02:10Z", "105.00", "5")
        ), Duration.ofMinutes(1));

        assertThat(candles).hasSize(2);
        CandleResponse first = candles.get(0);
        assertThat(first.start()).isEqualTo(Instant.parse("2026-10-27T10:00:00Z"));
        assertThat(first.open()).isEqualByComparingTo("100.00");
        assertThat(first.high()).isEqualByComparingTo("103.00");
        assertThat(first.low()).isEqualByComparingTo("99.00");
        assertThat(first.close()).isEqualByComparingTo("101.00");
        assertThat(first.volume()).isEqualByComparingTo("10");

        CandleResponse second = candles.get(1);
        assertThat(second.start()).isEqualTo(Instant.parse("2026-10-27T10:02:00Z"));
        assertThat(second.open()).isEqualByComparingTo("105.00");
        assertThat(second.volume()).isEqualByComparingTo("5");
    }

    @Test
    void aucune_execution_aucun_chandelier() {
        assertThat(CandleAggregator.aggregate(List.of(), Duration.ofMinutes(1))).isEmpty();
    }
}
