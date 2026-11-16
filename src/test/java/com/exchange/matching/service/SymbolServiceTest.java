package com.exchange.matching.service;

import com.exchange.matching.exception.MarketClosedException;
import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.persistence.entity.SymbolEntity;
import com.exchange.matching.persistence.repository.SymbolRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SymbolServiceTest {

    @Mock
    private SymbolRepository repository;

    @InjectMocks
    private SymbolService service;

    private final SymbolEntity apple = new SymbolEntity("AAPL", "Apple Inc.", new BigDecimal("230"));

    @Test
    void list_shouldReturnSymbolsInListingOrder() {
        SymbolEntity tesla = new SymbolEntity("TSLA", "Tesla", new BigDecimal("250"));
        when(repository.findAllByOrderByCreatedAtAscSymbolAsc()).thenReturn(List.of(apple, tesla));

        assertThat(service.list()).containsExactly(apple, tesla);
        assertThat(service.codes()).containsExactly("AAPL", "TSLA");
    }

    @Test
    void list_whenNoSymbol_shouldReturnEmpty() {
        when(repository.findAllByOrderByCreatedAtAscSymbolAsc()).thenReturn(List.of());

        assertThat(service.codes()).isEmpty();
    }

    @Test
    void require_whenExists_shouldReturnSymbol() {
        when(repository.findById("AAPL")).thenReturn(Optional.of(apple));

        assertThat(service.require("AAPL")).isSameAs(apple);
    }

    @Test
    void require_whenUnknown_shouldThrow() {
        when(repository.findById("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.require("XXX"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Symbole inconnu: XXX");
    }

    @Test
    void requireTradable_whenActive_shouldReturnSymbol() {
        when(repository.findById("AAPL")).thenReturn(Optional.of(apple));

        assertThat(service.requireTradable("AAPL")).isSameAs(apple);
    }

    @Test
    void requireTradable_whenSuspended_shouldThrowMarketClosed() {
        apple.setStatus(AccountStatus.SUSPENDED);
        when(repository.findById("AAPL")).thenReturn(Optional.of(apple));

        assertThatThrownBy(() -> service.requireTradable("AAPL"))
            .isInstanceOf(MarketClosedException.class)
            .hasMessage("La cotation de AAPL est suspendue");
    }
}
