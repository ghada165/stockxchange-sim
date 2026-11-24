package com.exchange.matching.service;

import com.exchange.matching.TestInjection;
import com.exchange.matching.dto.AdminOverviewResponse;
import com.exchange.matching.dto.AdminUserResponse;
import com.exchange.matching.exception.ForbiddenOperationException;
import com.exchange.matching.exception.ResourceNotFoundException;
import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.model.Role;
import com.exchange.matching.persistence.entity.SymbolEntity;
import com.exchange.matching.persistence.entity.UserEntity;
import com.exchange.matching.persistence.entity.WalletEntity;
import com.exchange.matching.persistence.repository.OrderRepository;
import com.exchange.matching.persistence.repository.SymbolRepository;
import com.exchange.matching.persistence.repository.UserRepository;
import com.exchange.matching.persistence.repository.WalletRepository;
import com.exchange.matching.reporting.TradeStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private SymbolRepository symbolRepository;
    @Mock private ReportingService reportingService;
    @Mock private MarketHoursService marketHours;
    @Mock private ActivityLogService activityLog;
    @Mock private TradingService tradingService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = TestInjection.inject(new AdminService(), userRepository, walletRepository, orderRepository,
            symbolRepository, reportingService, marketHours, activityLog, tradingService,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Test
    void overview_shouldAggregatePlatformFigures() {
        SymbolEntity tesla = new SymbolEntity("TSLA", "Tesla", new BigDecimal("250"));
        tesla.setStatus(AccountStatus.SUSPENDED);
        when(symbolRepository.findAll()).thenReturn(List.of(new SymbolEntity("AAPL", "Apple", new BigDecimal("230")), tesla));
        when(reportingService.globalStats()).thenReturn(new TradeStats(12L, new BigDecimal("40"), new BigDecimal("9200"), null, null));
        when(userRepository.count()).thenReturn(5L);
        when(userRepository.countByRole(Role.TRADER)).thenReturn(4L);
        when(userRepository.countByStatus(AccountStatus.SUSPENDED)).thenReturn(1L);
        when(orderRepository.countByStatusIn(anyList())).thenReturn(33L);
        when(marketHours.isOpen()).thenReturn(true);

        AdminOverviewResponse overview = adminService.overview();

        assertThat(overview.users()).isEqualTo(5);
        assertThat(overview.traders()).isEqualTo(4);
        assertThat(overview.symbols()).isEqualTo(2);
        assertThat(overview.suspendedSymbols()).isEqualTo(1);
        assertThat(overview.activeOrders()).isEqualTo(33);
        assertThat(overview.tradesLast24h()).isEqualTo(12);
        assertThat(overview.turnoverLast24h()).isEqualByComparingTo("9200");
        assertThat(overview.marketOpen()).isTrue();
    }

    @Test
    void users_shouldIncludeCashBalanceAndActiveOrders() {
        when(userRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(
            new UserEntity("admin", "h", null, null, Role.ADMIN), new UserEntity("alice", "h", null, null, Role.TRADER)));
        when(walletRepository.findByUserId("admin")).thenReturn(Optional.empty());
        when(walletRepository.findByUserId("alice")).thenReturn(Optional.of(new WalletEntity("alice", new BigDecimal("98850"))));
        when(orderRepository.countByUserIdAndStatusIn(eq("admin"), anyList())).thenReturn(0L);
        when(orderRepository.countByUserIdAndStatusIn(eq("alice"), anyList())).thenReturn(3L);

        List<AdminUserResponse> users = adminService.users();

        assertThat(users).extracting(AdminUserResponse::username).containsExactly("admin", "alice");
        assertThat(users.get(0).cashBalance()).isNull(); // l'admin n'a pas de wallet
        assertThat(users.get(1).cashBalance()).isEqualByComparingTo("98850");
        assertThat(users.get(1).activeOrders()).isEqualTo(3);
    }

    @Test
    void users_whenNoUser_shouldReturnEmptyList() {
        when(userRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of());

        assertThat(adminService.users()).isEmpty();
    }

    @Test
    void setUserStatus_shouldSuspendUserAndLog() {
        UserEntity bob = new UserEntity("bob", "h", null, null, Role.TRADER);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(userRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(bob));
        when(walletRepository.findByUserId("bob")).thenReturn(Optional.empty());

        AdminUserResponse result = adminService.setUserStatus("admin", "bob", AccountStatus.SUSPENDED);

        assertThat(bob.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(result.status()).isEqualTo(AccountStatus.SUSPENDED);
        verify(activityLog).record("admin", ActivityType.USER_STATUS_CHANGED, "bob -> SUSPENDED");
    }

    @Test
    void setUserStatus_onOwnAccount_shouldBeForbidden() {
        UserEntity admin = new UserEntity("admin", "h", null, null, Role.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> adminService.setUserStatus("admin", "admin", AccountStatus.SUSPENDED))
            .isInstanceOf(ForbiddenOperationException.class);

        assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(activityLog, never()).record(anyString(), any(), anyString());
    }

    @Test
    void setUserStatus_whenUserNotFound_shouldThrow() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.setUserStatus("admin", "ghost", AccountStatus.ACTIVE))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Utilisateur inconnu: ghost");
    }
}
