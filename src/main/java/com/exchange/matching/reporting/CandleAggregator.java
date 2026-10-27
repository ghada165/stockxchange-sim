package com.exchange.matching.reporting;

import com.exchange.matching.dto.CandleResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Regroupe des executions en chandeliers OHLC (open, high, low, close,
 * volume) par intervalle de temps fixe. Classe pure, sans dependance a
 * Spring ni a la base: testee seule dans CandleAggregatorTest.
 */
public final class CandleAggregator {

    /** Une execution reduite a ce qui compte pour un graphique de prix. */
    public record PricePoint(Instant time, BigDecimal price, BigDecimal quantity) {}

    private CandleAggregator() {
    }

    /**
     * @param points executions triees par date croissante
     * @param interval duree d'un chandelier (ex. 1 minute)
     */
    public static List<CandleResponse> aggregate(List<PricePoint> points, Duration interval) {
        long step = interval.toMillis();
        if (step <= 0) {
            throw new IllegalArgumentException("L'intervalle doit etre positif");
        }
        List<CandleResponse> candles = new ArrayList<>();
        CandleResponse current = null;
        for (PricePoint point : points) {
            Instant bucket = Instant.ofEpochMilli(Math.floorDiv(point.time().toEpochMilli(), step) * step);
            if (current == null || !current.start().equals(bucket)) {
                if (current != null) {
                    candles.add(current);
                }
                current = new CandleResponse(bucket, point.price(), point.price(), point.price(),
                    point.price(), point.quantity());
            } else {
                current = new CandleResponse(
                    current.start(),
                    current.open(),
                    current.high().max(point.price()),
                    current.low().min(point.price()),
                    point.price(),
                    current.volume().add(point.quantity())
                );
            }
        }
        if (current != null) {
            candles.add(current);
        }
        return candles;
    }
}
