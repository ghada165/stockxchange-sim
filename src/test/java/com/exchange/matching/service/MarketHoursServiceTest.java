package com.exchange.matching.service;

import com.exchange.matching.TestInjection;
import com.exchange.matching.config.ExchangeProperties;
import com.exchange.matching.dto.MarketHoursRequest;
import com.exchange.matching.dto.MarketStatusResponse;
import com.exchange.matching.exception.MarketClosedException;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.model.MarketHours;
import com.exchange.matching.persistence.entity.MarketSettingsEntity;
import com.exchange.matching.persistence.repository.MarketSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketHoursServiceTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Mock private MarketSettingsRepository repository;
    @Mock private ActivityLogService activityLog;

    private MarketHoursService service;

    @BeforeEach
    void setUp() {
        ExchangeProperties properties = new ExchangeProperties(null, null, null, false, false,
            new ExchangeProperties.MarketHoursSettings("09:00", "17:30", "Europe/Paris", null, false), null);
        service = TestInjection.inject(new MarketHoursService(), repository, properties, activityLog);
    }

    /** Tous les jours sauf aujourd'hui: le marche est forcement ferme maintenant. */
    private static Set<DayOfWeek> allDaysButToday() {
        EnumSet<DayOfWeek> days = EnumSet.allOf(DayOfWeek.class);
        days.remove(ZonedDateTime.now(PARIS).getDayOfWeek());
        return days;
    }

    @Test
    void current_beforeInitialize_shouldFallBackToApplicationYml() {
        MarketHours hours = service.current();

        assertThat(hours.open()).isEqualTo(LocalTime.of(9, 0));
        assertThat(hours.enforced()).isFalse();
        assertThat(service.isOpen()).isTrue(); // non applique: toujours ouvert
    }

    @Test
    void initialize_whenSettingsExist_shouldLoadThem() {
        MarketHours stored = new MarketHours(LocalTime.of(10, 0), LocalTime.of(16, 0), PARIS, allDaysButToday(), true);
        when(repository.findById(MarketSettingsEntity.SINGLETON_ID)).thenReturn(Optional.of(new MarketSettingsEntity(stored)));

        service.initialize();

        assertThat(service.current().open()).isEqualTo(LocalTime.of(10, 0));
        assertThat(service.isOpen()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void initialize_whenNoSettings_shouldCreateThemFromConfiguration() {
        when(repository.findById(MarketSettingsEntity.SINGLETON_ID)).thenReturn(Optional.empty());
        when(repository.save(any(MarketSettingsEntity.class))).thenAnswer(i -> i.getArgument(0));

        service.initialize();

        verify(repository).save(any(MarketSettingsEntity.class));
        assertThat(service.current().close()).isEqualTo(LocalTime.of(17, 30));
    }

    @Test
    void requireOpen_whenClosed_shouldThrow() {
        when(repository.findById(MarketSettingsEntity.SINGLETON_ID)).thenReturn(Optional.of(new MarketSettingsEntity(
            new MarketHours(LocalTime.of(0, 0), LocalTime.of(23, 59), PARIS, allDaysButToday(), true))));
        service.initialize();

        assertThatThrownBy(service::requireOpen)
            .isInstanceOf(MarketClosedException.class)
            .hasMessageContaining("Marche ferme");
    }

    @Test
    void requireOpen_whenNotEnforced_shouldPass() {
        assertThatCode(service::requireOpen).doesNotThrowAnyException();
    }

    @Test
    void update_shouldSaveNewHoursAndLog() {
        when(repository.findById(MarketSettingsEntity.SINGLETON_ID)).thenReturn(Optional.empty());

        MarketStatusResponse status = service.update(new MarketHoursRequest(LocalTime.of(8, 0), LocalTime.of(20, 0),
            "America/New_York", Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), true), "admin");

        ArgumentCaptor<MarketSettingsEntity> saved = ArgumentCaptor.forClass(MarketSettingsEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().toMarketHours().zone()).isEqualTo(ZoneId.of("America/New_York"));
        assertThat(status.openTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(status.tradingDays()).isEqualTo(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));
        assertThat(service.current().enforced()).isTrue();
        verify(activityLog).record(eq("admin"), eq(ActivityType.MARKET_HOURS_UPDATED), contains("08:00-20:00"));
    }

    @Test
    void update_withUnknownZone_shouldThrowAndSaveNothing() {
        assertThatThrownBy(() -> service.update(new MarketHoursRequest(LocalTime.of(9, 0), LocalTime.of(17, 0),
                "Mars/Olympus", Set.of(DayOfWeek.MONDAY), true), "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Fuseau horaire inconnu: Mars/Olympus");

        verify(repository, never()).save(any());
    }

    @Test
    void update_withCloseBeforeOpen_shouldThrow() {
        assertThatThrownBy(() -> service.update(new MarketHoursRequest(LocalTime.of(18, 0), LocalTime.of(9, 0),
                "Europe/Paris", Set.of(DayOfWeek.MONDAY), true), "admin"))
            .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
    }
}
