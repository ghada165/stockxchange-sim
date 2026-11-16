package com.exchange.matching.service;

import com.exchange.matching.dto.ActivityLogResponse;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.persistence.entity.ActivityLogEntity;
import com.exchange.matching.persistence.repository.ActivityLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityLogServiceTest {

    @Mock
    private ActivityLogRepository repository;

    @InjectMocks
    private ActivityLogService service;

    @Test
    void record_shouldSaveEntry() {
        service.record("alice", ActivityType.ORDER_PLACED, "BUY 1 AAPL");

        ArgumentCaptor<ActivityLogEntity> saved = ArgumentCaptor.forClass(ActivityLogEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getActor()).isEqualTo("alice");
        assertThat(saved.getValue().getAction()).isEqualTo(ActivityType.ORDER_PLACED);
        assertThat(saved.getValue().getDetails()).isEqualTo("BUY 1 AAPL");
        assertThat(saved.getValue().getOccurredAt()).isNotNull();
    }

    @Test
    void record_shouldTruncateDetailsTo500Characters() {
        service.record("alice", ActivityType.ORDER_PLACED, "x".repeat(800));

        ArgumentCaptor<ActivityLogEntity> saved = ArgumentCaptor.forClass(ActivityLogEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getDetails()).hasSize(500);
    }

    @Test
    void recent_withoutFilter_shouldReturnLatestEntries() {
        when(repository.findTop200ByOrderByOccurredAtDescIdDesc()).thenReturn(List.of(
            new ActivityLogEntity("bob", ActivityType.LOGIN_SUCCESS, null),
            new ActivityLogEntity("alice", ActivityType.LOGIN_SUCCESS, null)));

        List<ActivityLogResponse> logs = service.recent(null, null);

        assertThat(logs).extracting(ActivityLogResponse::actor).containsExactly("bob", "alice");
    }

    @Test
    void recent_byActor_shouldNormalizeNameAndIgnoreActionFilter() {
        when(repository.findTop200ByActorOrderByOccurredAtDescIdDesc("alice")).thenReturn(List.of(
            new ActivityLogEntity("alice", ActivityType.ORDER_PLACED, "x")));

        List<ActivityLogResponse> logs = service.recent("  Alice ", ActivityType.LOGIN_FAILED);

        assertThat(logs).hasSize(1);
        verify(repository, never()).findTop200ByActionOrderByOccurredAtDescIdDesc(any());
    }

    @Test
    void recent_byAction_shouldUseActionQuery() {
        when(repository.findTop200ByActionOrderByOccurredAtDescIdDesc(ActivityType.LOGIN_FAILED)).thenReturn(List.of());

        assertThat(service.recent(" ", ActivityType.LOGIN_FAILED)).isEmpty();

        verify(repository, never()).findTop200ByActorOrderByOccurredAtDescIdDesc(anyString());
    }
}
