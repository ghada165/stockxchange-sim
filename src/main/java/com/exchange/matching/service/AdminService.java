package com.exchange.matching.service;

import com.exchange.matching.dto.AdminOverviewResponse;
import com.exchange.matching.dto.AdminUserResponse;
import com.exchange.matching.exception.ForbiddenOperationException;
import com.exchange.matching.exception.ResourceNotFoundException;
import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.model.Role;
import com.exchange.matching.persistence.entity.SymbolEntity;
import com.exchange.matching.persistence.entity.UserEntity;
import com.exchange.matching.persistence.entity.WalletEntity;
import com.exchange.matching.persistence.repository.OrderRepository;
import com.exchange.matching.persistence.repository.SymbolRepository;
import com.exchange.matching.persistence.repository.UserRepository;
import com.exchange.matching.persistence.repository.WalletRepository;
import com.exchange.matching.reporting.TradeStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/** Module Administration: supervision des utilisateurs et tableau de bord (les symboles: SymbolAdminService). */
@Service
public class AdminService {

    private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private SymbolRepository symbolRepository;
    @Autowired
    private ReportingService reportingService;
    @Autowired
    private MarketHoursService marketHours;
    @Autowired
    private ActivityLogService activityLog;
    @Autowired
    private TradingService tradingService;
    @Autowired
    private TransactionTemplate transactionTemplate;

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
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur inconnu: " + username));
            user.registerSuccessfulLogin();
            activityLog.record(admin, ActivityType.USER_STATUS_CHANGED, username + " deverrouille");
        });
        return userView(username);
    }

    private UserEntity requireOtherUser(String admin, String username, String what) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("Utilisateur inconnu: " + username));
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
}
