package com.exchange.matching.persistence.repository;

import com.exchange.matching.model.ActivityType;
import com.exchange.matching.persistence.entity.ActivityLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLogEntity, Long> {

    List<ActivityLogEntity> findTop200ByOrderByOccurredAtDescIdDesc();

    List<ActivityLogEntity> findTop200ByActorOrderByOccurredAtDescIdDesc(String actor);

    List<ActivityLogEntity> findTop200ByActionOrderByOccurredAtDescIdDesc(ActivityType action);
}
