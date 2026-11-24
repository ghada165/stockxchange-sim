package com.exchange.matching.service;

import com.exchange.matching.dto.ActivityLogResponse;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.persistence.entity.ActivityLogEntity;
import com.exchange.matching.persistence.repository.ActivityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Journal d'activite global (module Administration). Un enregistrement
 * rejoint la transaction en cours s'il y en a une: si l'operation echoue
 * et est annulee, sa trace l'est aussi, ce qui garde le journal fidele.
 */
@Service
public class ActivityLogService {

    @Autowired
    private ActivityLogRepository repository;

    public void record(String actor, ActivityType action, String details) {
        repository.save(new ActivityLogEntity(actor, action, details));
    }

    @Transactional(readOnly = true)
    public List<ActivityLogResponse> recent(String actor, ActivityType action) {
        List<ActivityLogEntity> logs;
        if (actor != null && !actor.isBlank()) {
            logs = repository.findTop200ByActorOrderByOccurredAtDescIdDesc(actor.trim().toLowerCase());
        } else if (action != null) {
            logs = repository.findTop200ByActionOrderByOccurredAtDescIdDesc(action);
        } else {
            logs = repository.findTop200ByOrderByOccurredAtDescIdDesc();
        }
        return logs.stream().map(ActivityLogResponse::from).toList();
    }
}
