package com.exchange.matching.config;

import com.exchange.matching.engine.MatchingEngine;
import com.exchange.matching.model.Order;
import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.persistence.entity.OrderEntity;
import com.exchange.matching.persistence.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Au demarrage de l'application, le MatchingEngine part d'un carnet VIDE
 * (c'est une structure en memoire). Si l'application redemarre alors que
 * des ordres LIMIT etaient encore actifs (OPEN / PARTIALLY_FILLED) au moment
 * de l'arret, ce loader les recharge dans le meme ordre chronologique pour
 * que le carnet retrouve exactement l'etat qu'il avait avant l'arret.
 */
@Component
public class OrderBookLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderBookLoader.class);

    private final OrderRepository orderRepository;
    private final MatchingEngine matchingEngine;

    public OrderBookLoader(OrderRepository orderRepository, MatchingEngine matchingEngine) {
        this.orderRepository = orderRepository;
        this.matchingEngine = matchingEngine;
    }

    @Override
    public void run(String... args) {
        List<OrderEntity> activeOrders = orderRepository.findByStatusInOrderBySequenceAsc(
            List.of(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED));

        long maxSequence = 0L;
        for (OrderEntity entity : activeOrders) {
            Order order = Order.reconstruct(entity.getId(), entity.getUserId(), entity.getSymbol(),
                entity.getSide(), entity.getType(), entity.getPrice(), entity.getQuantity(),
                entity.getRemainingQuantity(), entity.getStatus(), entity.getCreatedAt(), entity.getSequence());
            matchingEngine.restoreOrder(order);
            maxSequence = Math.max(maxSequence, entity.getSequence());
        }
        matchingEngine.fastForwardSequence(maxSequence);

        log.info("Carnet d'ordres recharge: {} ordre(s) actif(s) restaure(s)", activeOrders.size());
    }
}
