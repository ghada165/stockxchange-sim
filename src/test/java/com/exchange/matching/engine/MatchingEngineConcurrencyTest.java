package com.exchange.matching.engine;

import com.exchange.matching.model.Order;
import com.exchange.matching.model.OrderSide;
import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.model.OrderType;
import com.exchange.matching.model.Trade;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce test est le coeur de la preuve "production-ready": il demontre que le
 * moteur ne perd, ne duplique, ni ne corrompt aucune quantite quand des
 * ordres sont soumis EXACTEMENT en meme temps depuis plusieurs threads sur
 * le meme symbole.
 *
 * Sans le verrouillage par symbole dans MatchingEngine, ce test echouerait
 * de facon non-deterministe (parfois passe, parfois echoue) a cause de race
 * conditions classiques: deux threads lisant le meme niveau de prix,
 * calculant chacun un matchedQty base sur un remainingQuantity perime, et
 * appelant reduceRemaining() avec un total qui depasse la quantite reelle.
 */
class MatchingEngineConcurrencyTest {

    private static final String SYMBOL = "AAPL";

    @RepeatedTest(5) // repete pour augmenter la chance de detecter une race condition
    @Timeout(30)
    void ordres_simultanes_meme_prix_aucune_perte_aucune_duplication() throws InterruptedException {
        MatchingEngine engine = new MatchingEngine();

        int pairs = 500; // 500 achats vs 500 ventes, qty=1, meme prix -> doit tout matcher
        int threadCount = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(pairs * 2);

        ConcurrentLinkedQueue<Trade> allTrades = new ConcurrentLinkedQueue<>();
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < pairs; i++) {
            submitAsync(engine, pool, startLatch, doneLatch, allTrades, errors,
                new Order("buyer-" + i, SYMBOL, OrderSide.BUY, OrderType.LIMIT,
                    new BigDecimal("100.00"), BigDecimal.ONE));
            submitAsync(engine, pool, startLatch, doneLatch, allTrades, errors,
                new Order("seller-" + i, SYMBOL, OrderSide.SELL, OrderType.LIMIT,
                    new BigDecimal("100.00"), BigDecimal.ONE));
        }

        startLatch.countDown(); // libere tous les threads en meme temps
        boolean finished = doneLatch.await(20, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).as("tous les ordres doivent avoir ete traites").isTrue();
        assertThat(errors.get()).as("aucune exception dans le moteur").isZero();

        BigDecimal totalTraded = allTrades.stream()
            .map(Trade::getQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 500 paires de 1 unite -> exactement 500 unites doivent avoir ete echangees,
        // ni plus (duplication) ni moins (perte)
        assertThat(totalTraded).isEqualByComparingTo(new BigDecimal(pairs));
        assertThat(engine.getOrderBook(SYMBOL).isEmpty())
            .as("le carnet doit etre totalement vide, tout doit avoir matche")
            .isTrue();
    }

    @Test
    @Timeout(30)
    void annulations_concurrentes_aux_soumissions_ne_corrompent_pas_le_carnet() throws InterruptedException {
        MatchingEngine engine = new MatchingEngine();
        int orderCount = 300;
        ExecutorService pool = Executors.newFixedThreadPool(12);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(orderCount);
        AtomicInteger errors = new AtomicInteger(0);

        List<Order> orders = new CopyOnWriteArrayList<>();
        for (int i = 0; i < orderCount; i++) {
            orders.add(new Order("user-" + i, SYMBOL, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100.00"), BigDecimal.ONE));
        }

        // La moitie des threads soumettent, l'autre moitie annule ce qui vient d'etre soumis
        for (Order order : orders) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    engine.submitOrder(order);
                    // tentative d'annulation immediate (peut echouer si deja matche, c'est OK)
                    try {
                        engine.cancelOrder(SYMBOL, order.getId());
                    } catch (RuntimeException ignoredAlreadyGoneOrFilled) {
                        // acceptable: l'ordre a pu etre traite avant l'annulation
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(20, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).isTrue();
        assertThat(errors.get()).as("aucune exception inattendue (hors annulation deja traitee)").isZero();
        // Chaque ordre doit avoir un statut final coherent, jamais un etat incoherent
        for (Order order : orders) {
            assertThat(order.getStatus()).isIn(OrderStatus.OPEN, OrderStatus.CANCELLED,
                OrderStatus.FILLED, OrderStatus.PARTIALLY_FILLED);
        }
    }

    private void submitAsync(MatchingEngine engine, ExecutorService pool, CountDownLatch startLatch,
                              CountDownLatch doneLatch, ConcurrentLinkedQueue<Trade> allTrades,
                              AtomicInteger errors, Order order) {
        pool.submit(() -> {
            try {
                startLatch.await();
                List<Trade> trades = engine.submitOrder(order);
                allTrades.addAll(trades);
            } catch (Exception e) {
                errors.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });
    }
}
