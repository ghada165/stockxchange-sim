package com.exchange.matching.service;

import com.exchange.matching.config.ExchangeProperties;
import com.exchange.matching.config.MarketSeeder;
import com.exchange.matching.dto.AdminOverviewResponse;
import com.exchange.matching.dto.AdminUserResponse;
import com.exchange.matching.dto.CreateSymbolRequest;
import com.exchange.matching.dto.MarketSummaryResponse;
import com.exchange.matching.exception.ForbiddenOperationException;
import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.model.Role;
import com.exchange.matching.persistence.entity.PositionEntity;
import com.exchange.matching.persistence.entity.SymbolEntity;
import com.exchange.matching.persistence.entity.UserEntity;
import com.exchange.matching.persistence.entity.WalletEntity;
import com.exchange.matching.persistence.repository.OrderRepository;
import com.exchange.matching.persistence.repository.PositionRepository;
import com.exchange.matching.persistence.repository.SymbolRepository;
import com.exchange.matching.persistence.repository.UserRepository;
import com.exchange.matching.persistence.repository.WalletRepository;
import com.exchange.matching.reporting.TradeStats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/** Module Administration: symboles, supervision des utilisateurs, tableau de bord. */
@Service
public class AdminService {

    private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;
    private final SymbolRepository symbolRepository;
    private final ReportingService reportingService;
    private final MarketHoursService marketHours;
    private final TradingService tradingService;
    private final MarketSeeder marketSeeder;
    private final ActivityLogService activityLog;
    private final ExchangeProperties properties;
    private final TransactionTemplate transactionTemplate;

    public AdminService(UserRepository userRepository, WalletRepository walletRepository,
                        PositionRepository positionRepository, OrderRepository orderRepository,
                        SymbolRepository symbolRepository, ReportingService reportingService,
                        MarketHoursService marketHours, TradingService tradingService, MarketSeeder marketSeeder,
                        ActivityLogService activityLog, ExchangeProperties properties,
                        TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.symbolRepository = symbolRepository;
        this.reportingService = reportingService;
        this.marketHours = marketHours;
        this.tradingService = tradingService;
        this.marketSeeder = marketSeeder;
        this.activityLog = activityLog;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    public AdminOverviewResponse overview() {
        List<SymbolEntity> symbols = symbolRepository.findAll();
        TradeStats stats = reportingService.globalStats();
        return new AdminOverviewResponse(
            userRepository.count(),
            userRepository.countByRole(Role.TRADER),
            userRepository.countByStatus(AccountStatus.SUSPENDED),
            symbols.size(),
            symbols.stream().filter(s -> !s.isTradable()).count(),
            orderRepository.countByStatusIn(ACTIVE),
            stats.tradeCount(),
            stats.volume(),
            stats.turnover(),
            marketHours.isOpen()
        );
    }

    public List<AdminUserResponse> users() {
        return userRepository.findAllByOrderByCreatedAtAsc().stream()
            .map(u -> new AdminUserResponse(
                u.getUsername(), u.getDisplayName(), u.getEmail(), u.getRole(), u.getStatus(), u.getCreatedAt(),
                u.isLocked(Instant.now()) ? u.getLockedUntil() : null,
                walletRepository.findByUserId(u.getUsername()).map(WalletEntity::getBalance).orElse(null),
                orderRepository.countByUserIdAndStatusIn(u.getUsername(), ACTIVE)))
            .toList();
    }

    /**
     * Suspendre ou reactiver un utilisateur. Une suspension invalide aussi ses
     * jetons: il est deconnecte immediatement, et devra se reconnecter s'il est
     * reactive.
     */
    public AdminUserResponse setUserStatus(String admin, String username, AccountStatus status) {
        transactionTemplate.executeWithoutResult(tx -> {
            UserEntity user = requireOtherUser(admin, username, "son propre statut");
            if (status == AccountStatus.SUSPENDED && user.getRole() == Role.ADMIN) {
                requireAnotherActiveAdmin(user);
            }
            user.setStatus(status);
            if (status == AccountStatus.SUSPENDED) {
                user.revokeAllTokens();
            }
            activityLog.record(admin, ActivityType.USER_STATUS_CHANGED, username + " -> " + status);
        });
        return userView(username);
    }

    /**
     * Attribution de role (cahier des charges: "Attribution de role (Trader / Admin)").
     * Le role etant inscrit dans le jeton, les jetons existants de l'utilisateur
     * sont invalides: il se reconnecte avec ses nouveaux droits. Un utilisateur
     * qui devient trader recoit son compte de trading s'il n'en avait pas.
     */
    public AdminUserResponse setUserRole(String admin, String username, Role role) {
        transactionTemplate.executeWithoutResult(tx -> {
            UserEntity user = requireOtherUser(admin, username, "son propre role");
            if (user.getRole() == role) {
                return;
            }
            if (user.getRole() == Role.ADMIN) {
                requireAnotherActiveAdmin(user);
            }
            Role previous = user.getRole();
            user.setRole(role);
            user.revokeAllTokens();
            if (role == Role.TRADER) {
                tradingService.openAccount(username);
            }
            activityLog.record(admin, ActivityType.USER_ROLE_CHANGED, username + ": " + previous + " -> " + role);
        });
        return userView(username);
    }

    /** Deverrouiller un compte bloque apres trop d'echecs de connexion, sans attendre la fin du delai. */
    public AdminUserResponse unlockUser(String admin, String username) {
        transactionTemplate.executeWithoutResult(tx -> {
            UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur inconnu: " + username));
            user.registerSuccessfulLogin();
            activityLog.record(admin, ActivityType.USER_STATUS_CHANGED, username + " deverrouille");
        });
        return userView(username);
    }

    private UserEntity requireOtherUser(String admin, String username, String what) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur inconnu: " + username));
        if (user.getUsername().equals(admin)) {
            throw new ForbiddenOperationException("Un administrateur ne peut pas modifier " + what);
        }
        return user;
    }

    /** Il doit toujours rester au moins un administrateur actif, sinon plus personne ne peut administrer. */
    private void requireAnotherActiveAdmin(UserEntity admin) {
        long activeAdmins = userRepository.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE);
        long others = admin.isActive() ? activeAdmins - 1 : activeAdmins;
        if (others < 1) {
            throw new ForbiddenOperationException("Impossible: ce serait le dernier administrateur actif");
        }
    }

    private AdminUserResponse userView(String username) {
        return users().stream().filter(u -> u.username().equals(username)).findFirst().orElseThrow();
    }

    /**
     * Introduction en bourse d'un nouveau symbole: il est cote, chaque compte
     * de trading existant recoit sa dotation d'actions de depart, et le teneur
     * de marche place ses premiers ordres pour que le carnet ne soit pas vide.
     */
    public MarketSummaryResponse createSymbol(String admin, CreateSymbolRequest request) {
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

    public MarketSummaryResponse setSymbolStatus(String admin, String symbol, AccountStatus status) {
        transactionTemplate.executeWithoutResult(tx -> {
            SymbolEntity entity = symbolRepository.findById(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Symbole inconnu: " + symbol));
            entity.setStatus(status);
            activityLog.record(admin, ActivityType.SYMBOL_STATUS_CHANGED, symbol + " -> " + status);
        });
        return summaryOf(symbol);
    }

    private MarketSummaryResponse summaryOf(String symbol) {
        return tradingService.getMarketSummaries().stream()
            .filter(s -> s.symbol().equals(symbol))
            .findFirst()
            .orElseThrow();
    }
}
