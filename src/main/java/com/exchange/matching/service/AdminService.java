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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/** Module Administration: supervision des utilisateurs et tableau de bord (les symboles: SymbolAdminService). */
@Service
public class AdminService {

    private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final OrderRepository orderRepository;
    private final SymbolRepository symbolRepository;
    private final ReportingService reportingService;
    private final MarketHoursService marketHours;
    private final ActivityLogService activityLog;
    private final TransactionTemplate transactionTemplate;

    public AdminService(UserRepository userRepository, WalletRepository walletRepository,
                        OrderRepository orderRepository, SymbolRepository symbolRepository,
                        ReportingService reportingService, MarketHoursService marketHours,
                        ActivityLogService activityLog, TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.orderRepository = orderRepository;
        this.symbolRepository = symbolRepository;
        this.reportingService = reportingService;
        this.marketHours = marketHours;
        this.activityLog = activityLog;
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
                walletRepository.findByUserId(u.getUsername()).map(WalletEntity::getBalance).orElse(null),
                orderRepository.countByUserIdAndStatusIn(u.getUsername(), ACTIVE)))
            .toList();
    }

    public AdminUserResponse setUserStatus(String admin, String username, AccountStatus status) {
        transactionTemplate.executeWithoutResult(tx -> {
            UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur inconnu: " + username));
            if (user.getUsername().equals(admin)) {
                throw new ForbiddenOperationException("Un administrateur ne peut pas modifier son propre statut");
            }
            user.setStatus(status);
            activityLog.record(admin, ActivityType.USER_STATUS_CHANGED, username + " -> " + status);
        });
        return users().stream().filter(u -> u.username().equals(username)).findFirst().orElseThrow();
    }
}
