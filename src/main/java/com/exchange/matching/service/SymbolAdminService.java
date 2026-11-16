package com.exchange.matching.service;

import com.exchange.matching.config.ExchangeProperties;
import com.exchange.matching.config.MarketSeeder;
import com.exchange.matching.dto.CreateSymbolRequest;
import com.exchange.matching.dto.MarketSummaryResponse;
import com.exchange.matching.dto.UpdateSymbolRequest;
import com.exchange.matching.exception.ResourceNotFoundException;
import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.persistence.entity.OrderEntity;
import com.exchange.matching.persistence.entity.PositionEntity;
import com.exchange.matching.persistence.entity.SymbolEntity;
import com.exchange.matching.persistence.entity.WalletEntity;
import com.exchange.matching.persistence.repository.OrderRepository;
import com.exchange.matching.persistence.repository.PositionRepository;
import com.exchange.matching.persistence.repository.SymbolRepository;
import com.exchange.matching.persistence.repository.TradeRepository;
import com.exchange.matching.persistence.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Gestion des symboles cotes par l'administrateur: CRUD complet.
 *
 *   Create : introduction en bourse (dotation d'actions + amorcage du carnet)
 *   Read   : liste des symboles, detail d'un symbole
 *   Update : nom et prix de reference, ou statut (ACTIVE / SUSPENDED)
 *   Delete : retrait de la cote, seulement si personne d'autre que le teneur
 *            de marche n'a jamais passe d'ordre dessus (sinon on suspend)
 */
@Service
public class SymbolAdminService {

    private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);

    private final SymbolRepository symbolRepository;
    private final WalletRepository walletRepository;
    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final TradingService tradingService;
    private final MarketSeeder marketSeeder;
    private final ActivityLogService activityLog;
    private final ExchangeProperties properties;
    private final TransactionTemplate transactionTemplate;

    public SymbolAdminService(SymbolRepository symbolRepository, WalletRepository walletRepository,
                              PositionRepository positionRepository, OrderRepository orderRepository,
                              TradeRepository tradeRepository, TradingService tradingService,
                              MarketSeeder marketSeeder, ActivityLogService activityLog,
                              ExchangeProperties properties, TransactionTemplate transactionTemplate) {
        this.symbolRepository = symbolRepository;
        this.walletRepository = walletRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.tradingService = tradingService;
        this.marketSeeder = marketSeeder;
        this.activityLog = activityLog;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    /** Tous les symboles avec statut, dernier prix et meilleurs prix. */
    public List<MarketSummaryResponse> findAll() {
        return tradingService.getMarketSummaries();
    }

    /** @throws ResourceNotFoundException si le symbole n'existe pas */
    public MarketSummaryResponse findById(String symbol) {
        requireSymbol(symbol);
        return summaryOf(symbol);
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    /**
     * Introduction en bourse d'un nouveau symbole: il est cote, chaque compte
     * de trading existant recoit sa dotation d'actions de depart, et le teneur
     * de marche place ses premiers ordres pour que le carnet ne soit pas vide.
     *
     * @throws IllegalStateException si le symbole existe deja (HTTP 409)
     */
    public MarketSummaryResponse create(String admin, CreateSymbolRequest request) {
        SymbolEntity created = transactionTemplate.execute(tx -> {
            if (symbolRepository.existsById(request.symbol())) {
                throw new IllegalStateException("Le symbole " + request.symbol() + " existe deja");
            }
            SymbolEntity symbol = symbolRepository.save(
                new SymbolEntity(request.symbol(), request.name().trim(), request.referencePrice()));
            for (WalletEntity wallet : walletRepository.findAll()) {
                PositionEntity position = new PositionEntity(wallet.getUserId(), symbol.getSymbol());
                position.credit(properties.startingShares());
                positionRepository.save(position);
            }
            // Apres la boucle: si le teneur de marche n'avait pas encore de compte,
            // son ouverture lui attribue directement les actions du nouveau symbole.
            tradingService.openAccount(TradingService.MARKET_MAKER);
            activityLog.record(admin, ActivityType.SYMBOL_CREATED, symbol.getSymbol() + " (" + symbol.getName()
                + ") a " + symbol.getReferencePrice().toPlainString() + " $");
            return symbol;
        });
        marketSeeder.seed(created.getSymbol(), created.getReferencePrice());
        return summaryOf(created.getSymbol());
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    /**
     * Modifie le nom et le prix de reference. Les ordres deja dans le carnet
     * ne bougent pas: le prix de reference sert a l'affichage et a l'amorcage.
     *
     * @throws ResourceNotFoundException si le symbole n'existe pas
     */
    public MarketSummaryResponse update(String admin, String symbol, UpdateSymbolRequest request) {
        transactionTemplate.executeWithoutResult(tx -> {
            SymbolEntity entity = requireSymbol(symbol);
            entity.setName(request.name().trim());
            entity.setReferencePrice(request.referencePrice());
            symbolRepository.save(entity);
            activityLog.record(admin, ActivityType.SYMBOL_UPDATED, symbol + ": " + entity.getName()
                + ", reference " + request.referencePrice().toPlainString() + " $");
        });
        return summaryOf(symbol);
    }

    /**
     * Suspend ou reactive la cotation.
     *
     * @throws ResourceNotFoundException si le symbole n'existe pas
     */
    public MarketSummaryResponse setStatus(String admin, String symbol, AccountStatus status) {
        transactionTemplate.executeWithoutResult(tx -> {
            SymbolEntity entity = requireSymbol(symbol);
            entity.setStatus(status);
            symbolRepository.save(entity);
            activityLog.record(admin, ActivityType.SYMBOL_STATUS_CHANGED, symbol + " -> " + status);
        });
        return summaryOf(symbol);
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    /**
     * Retire un symbole de la cote. Autorise seulement si aucun trade n'a eu
     * lieu et qu'aucun utilisateur (hors teneur de marche) n'a passe d'ordre:
     * sinon l'historique et les releves deviendraient incoherents, et il faut
     * suspendre le symbole a la place.
     *
     * Etapes: suspension (plus aucun nouvel ordre accepte), annulation des
     * ordres du teneur de marche (liberation de ses fonds bloques), puis
     * suppression des ordres, positions et du symbole.
     *
     * @throws ResourceNotFoundException si le symbole n'existe pas (HTTP 404)
     * @throws IllegalStateException     si le symbole a deja ete echange (HTTP 409)
     */
    public void delete(String admin, String symbol) {
        SymbolEntity entity = requireSymbol(symbol);
        requireNeverTraded(symbol);

        AccountStatus previous = entity.getStatus();
        setStatusQuietly(symbol, AccountStatus.SUSPENDED);
        try {
            requireNeverTraded(symbol); // un ordre a pu arriver juste avant la suspension
        } catch (IllegalStateException e) {
            setStatusQuietly(symbol, previous);
            throw e;
        }

        for (OrderEntity order : orderRepository.findBySymbolAndUserIdAndStatusIn(
                symbol, TradingService.MARKET_MAKER, ACTIVE)) {
            tradingService.cancelOrder(TradingService.MARKET_MAKER, order.getId());
        }

        transactionTemplate.executeWithoutResult(tx -> {
            orderRepository.deleteAllBySymbol(symbol);
            positionRepository.deleteAllBySymbol(symbol);
            symbolRepository.deleteById(symbol);
            activityLog.record(admin, ActivityType.SYMBOL_DELETED, symbol + " (" + entity.getName() + ") retire de la cote");
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private SymbolEntity requireSymbol(String symbol) {
        return symbolRepository.findById(symbol)
            .orElseThrow(() -> new ResourceNotFoundException("Symbole inconnu: " + symbol));
    }

    private void requireNeverTraded(String symbol) {
        if (tradeRepository.existsBySymbol(symbol)
            || orderRepository.existsBySymbolAndUserIdNot(symbol, TradingService.MARKET_MAKER)) {
            throw new IllegalStateException("Impossible de supprimer " + symbol
                + ": des ordres ou des transactions existent deja. Suspendez la cotation a la place.");
        }
    }

    private void setStatusQuietly(String symbol, AccountStatus status) {
        transactionTemplate.executeWithoutResult(tx -> symbolRepository.findById(symbol).ifPresent(s -> {
            s.setStatus(status);
            symbolRepository.save(s);
        }));
    }

    private MarketSummaryResponse summaryOf(String symbol) {
        return tradingService.getMarketSummaries().stream()
            .filter(s -> s.symbol().equals(symbol))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Symbole inconnu: " + symbol));
    }
}
