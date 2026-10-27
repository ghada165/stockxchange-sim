package com.exchange.matching.engine;

import com.exchange.matching.model.Order;
import com.exchange.matching.model.OrderSide;
import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.model.OrderType;
import com.exchange.matching.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingEngineTest {

    private MatchingEngine engine;
    private static final String AAPL = "AAPL";

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    @Test
    void un_ordre_limit_seul_reste_dans_le_carnet_sans_trade() {
        Order buy = limitOrder("u1", OrderSide.BUY, "150.00", "10");

        List<Trade> trades = engine.submitOrder(buy).trades();

        assertThat(trades).isEmpty();
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(engine.getOrderBook(AAPL).bestBid()).contains(new BigDecimal("150.00"));
    }

    @Test
    void deux_ordres_a_prix_egal_se_matchent_completement() {
        Order sell = limitOrder("seller", OrderSide.SELL, "150.00", "10");
        engine.submitOrder(sell);

        Order buy = limitOrder("buyer", OrderSide.BUY, "150.00", "10");
        List<Trade> trades = engine.submitOrder(buy).trades();

        assertThat(trades).hasSize(1);
        Trade trade = trades.get(0);
        assertThat(trade.price()).isEqualByComparingTo("150.00");
        assertThat(trade.quantity()).isEqualByComparingTo("10");
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(sell.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(engine.getOrderBook(AAPL).isEmpty()).isTrue();
    }

    @Test
    void execution_au_prix_de_l_ordre_passif_pas_de_l_agressif() {
        // Le vendeur est deja dans le carnet a 150.00 (ordre passif)
        Order sell = limitOrder("seller", OrderSide.SELL, "150.00", "10");
        engine.submitOrder(sell);

        // L'acheteur arrive agressif, pret a payer jusqu'a 155.00
        Order buy = limitOrder("buyer", OrderSide.BUY, "155.00", "10");
        List<Trade> trades = engine.submitOrder(buy).trades();

        // Le trade doit s'executer au prix du vendeur (passif), pas 155.00
        assertThat(trades.get(0).price()).isEqualByComparingTo("150.00");
    }

    @Test
    void match_partiel_laisse_le_reliquat_dans_le_carnet() {
        Order sell = limitOrder("seller", OrderSide.SELL, "150.00", "10");
        engine.submitOrder(sell);

        Order buy = limitOrder("buyer", OrderSide.BUY, "150.00", "4");
        List<Trade> trades = engine.submitOrder(buy).trades();

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).quantity()).isEqualByComparingTo("4");
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(sell.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(sell.getRemainingQuantity()).isEqualByComparingTo("6");
        assertThat(engine.getOrderBook(AAPL).bestAsk()).contains(new BigDecimal("150.00"));
    }

    @Test
    void time_priority_respectee_a_prix_egal() {
        // Deux vendeurs au meme prix: le premier arrive doit etre servi en premier
        Order sellFirst = limitOrder("seller1", OrderSide.SELL, "150.00", "5");
        Order sellSecond = limitOrder("seller2", OrderSide.SELL, "150.00", "5");
        engine.submitOrder(sellFirst);
        engine.submitOrder(sellSecond);

        Order buy = limitOrder("buyer", OrderSide.BUY, "150.00", "5");
        List<Trade> trades = engine.submitOrder(buy).trades();

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).sellOrderId()).isEqualTo(sellFirst.getId());
        assertThat(sellFirst.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(sellSecond.getStatus()).isEqualTo(OrderStatus.OPEN);
    }

    @Test
    void price_priority_prime_sur_time_priority() {
        // Un vendeur moins cher arrive APRES mais doit etre servi AVANT un vendeur plus cher arrive avant
        Order sellExpensive = limitOrder("seller1", OrderSide.SELL, "151.00", "5");
        Order sellCheap = limitOrder("seller2", OrderSide.SELL, "150.00", "5");
        engine.submitOrder(sellExpensive);
        engine.submitOrder(sellCheap);

        Order buy = limitOrder("buyer", OrderSide.BUY, "151.00", "5");
        List<Trade> trades = engine.submitOrder(buy).trades();

        assertThat(trades.get(0).sellOrderId()).isEqualTo(sellCheap.getId());
    }

    @Test
    void aucun_match_si_les_prix_ne_se_croisent_pas() {
        Order sell = limitOrder("seller", OrderSide.SELL, "155.00", "10");
        engine.submitOrder(sell);

        Order buy = limitOrder("buyer", OrderSide.BUY, "150.00", "10");
        List<Trade> trades = engine.submitOrder(buy).trades();

        assertThat(trades).isEmpty();
        assertThat(engine.getOrderBook(AAPL).bestBid()).contains(new BigDecimal("150.00"));
        assertThat(engine.getOrderBook(AAPL).bestAsk()).contains(new BigDecimal("155.00"));
    }

    @Test
    void ordre_market_consomme_plusieurs_niveaux_de_prix() {
        engine.submitOrder(limitOrder("s1", OrderSide.SELL, "150.00", "5"));
        engine.submitOrder(limitOrder("s2", OrderSide.SELL, "151.00", "5"));

        Order marketBuy = marketOrder("buyer", OrderSide.BUY, "8");
        List<Trade> trades = engine.submitOrder(marketBuy).trades();

        assertThat(trades).hasSize(2);
        assertThat(trades.get(0).price()).isEqualByComparingTo("150.00");
        assertThat(trades.get(0).quantity()).isEqualByComparingTo("5");
        assertThat(trades.get(1).price()).isEqualByComparingTo("151.00");
        assertThat(trades.get(1).quantity()).isEqualByComparingTo("3");
        assertThat(marketBuy.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void ordre_market_sans_liquidite_suffisante_annule_le_reliquat() {
        engine.submitOrder(limitOrder("s1", OrderSide.SELL, "150.00", "3"));

        Order marketBuy = marketOrder("buyer", OrderSide.BUY, "10");
        List<Trade> trades = engine.submitOrder(marketBuy).trades();

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).quantity()).isEqualByComparingTo("3");
        // 3 executes, 7 non executables -> l'ordre ne doit pas rester dans le carnet
        assertThat(marketBuy.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(engine.getOrderBook(AAPL).isEmpty()).isTrue();
    }

    @Test
    void annulation_retire_bien_l_ordre_du_carnet() {
        Order buy = limitOrder("buyer", OrderSide.BUY, "150.00", "10");
        engine.submitOrder(buy);

        engine.cancelOrder(AAPL, buy.getId());

        assertThat(buy.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(engine.getOrderBook(AAPL).isEmpty()).isTrue();
    }

    @Test
    void ordre_market_avec_plafond_ne_depasse_jamais_son_prix_plafond() {
        engine.submitOrder(limitOrder("s1", OrderSide.SELL, "150.00", "5"));
        engine.submitOrder(limitOrder("s2", OrderSide.SELL, "151.00", "5"));
        engine.submitOrder(limitOrder("s3", OrderSide.SELL, "160.00", "5"));

        // Plafond a 151: le niveau a 160 ne doit jamais etre touche
        Order marketBuy = Order.marketWithCap("buyer", AAPL, OrderSide.BUY, new BigDecimal("12"), new BigDecimal("151.00"));
        MatchResult result = engine.submitOrder(marketBuy);

        assertThat(result.trades()).hasSize(2);
        assertThat(result.trades()).allSatisfy(t -> assertThat(t.price()).isLessThanOrEqualTo(new BigDecimal("151.00")));
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.remainingQuantity()).isEqualByComparingTo("2");
        assertThat(engine.getOrderBook(AAPL).bestAsk()).contains(new BigDecimal("160.00"));
    }

    @Test
    void estimation_du_plafond_market_renvoie_le_dernier_niveau_a_consommer() {
        engine.submitOrder(limitOrder("s1", OrderSide.SELL, "150.00", "5"));
        engine.submitOrder(limitOrder("s2", OrderSide.SELL, "151.00", "5"));
        engine.submitOrder(limitOrder("s3", OrderSide.SELL, "152.00", "5"));

        assertThat(engine.estimatePriceCap(AAPL, OrderSide.BUY, new BigDecimal("5"))).contains(new BigDecimal("150.00"));
        assertThat(engine.estimatePriceCap(AAPL, OrderSide.BUY, new BigDecimal("6"))).contains(new BigDecimal("151.00"));
        // liquidite insuffisante: dernier niveau disponible
        assertThat(engine.estimatePriceCap(AAPL, OrderSide.BUY, new BigDecimal("100"))).contains(new BigDecimal("152.00"));
        // carnet oppose vide
        assertThat(engine.estimatePriceCap(AAPL, OrderSide.SELL, BigDecimal.ONE)).isEmpty();
    }

    @Test
    void un_utilisateur_ne_trade_jamais_contre_lui_meme() {
        Order mySell = limitOrder("alice", OrderSide.SELL, "150.00", "5");
        engine.submitOrder(mySell);

        Order myBuy = limitOrder("alice", OrderSide.BUY, "150.00", "5");
        MatchResult result = engine.submitOrder(myBuy);

        assertThat(result.trades()).isEmpty();
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(mySell.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(engine.getOrderBook(AAPL).bestBid()).isEmpty();
    }

    @Test
    void annulation_renvoie_la_quantite_restante_apres_execution_partielle() {
        Order sell = limitOrder("seller", OrderSide.SELL, "150.00", "10");
        engine.submitOrder(sell);
        engine.submitOrder(limitOrder("buyer", OrderSide.BUY, "150.00", "3"));

        Order cancelled = engine.cancelOrder(AAPL, sell.getId());

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getRemainingQuantity()).isEqualByComparingTo("7");
    }

    @Test
    void un_trade_porte_les_identifiants_acheteur_et_vendeur() {
        engine.submitOrder(limitOrder("seller", OrderSide.SELL, "150.00", "2"));
        Trade trade = engine.submitOrder(limitOrder("buyer", OrderSide.BUY, "150.00", "2")).trades().get(0);

        assertThat(trade.buyerId()).isEqualTo("buyer");
        assertThat(trade.sellerId()).isEqualTo("seller");
        assertThat(trade.notional()).isEqualByComparingTo("300.00");
    }

    // --- Helpers ---


    private Order limitOrder(String user, OrderSide side, String price, String qty) {
        return new Order(user, AAPL, side, OrderType.LIMIT, new BigDecimal(price), new BigDecimal(qty));
    }

    private Order marketOrder(String user, OrderSide side, String qty) {
        return new Order(user, AAPL, side, OrderType.MARKET, null, new BigDecimal(qty));
    }
}
