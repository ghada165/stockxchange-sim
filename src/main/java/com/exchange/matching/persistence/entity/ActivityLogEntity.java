package com.exchange.matching.persistence.entity;

import com.exchange.matching.model.ActivityType;
import jakarta.persistence.*;

import java.time.Instant;

/** Ligne du journal d'activite global, consultable par l'administrateur. */
@Entity
@Table(name = "activity_logs")
public class ActivityLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType action;

    private String details;

    protected ActivityLogEntity() {
    }

    public ActivityLogEntity(String actor, ActivityType action, String details) {
        this.occurredAt = Instant.now();
        this.actor = actor;
        this.action = action;
        this.details = details == null || details.length() <= 500 ? details : details.substring(0, 500);
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActor() { return actor; }
    public ActivityType getAction() { return action; }
    public String getDetails() { return details; }
}
