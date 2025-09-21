package com.albertolopez.jackpotbets.persistence.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OutboxMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID betId;
    private String jackpotId;
    private UUID userId;
    private BigDecimal betAmount;
    private String topic;
    private Instant createdAt;
    private Instant processedAt;

    public OutboxMessageEntity(UUID betId, String jackpotId, UUID userId, BigDecimal betAmount, String topic, Instant createdAt) {
        this.betId = betId;
        this.jackpotId = jackpotId;
        this.userId = userId;
        this.betAmount = betAmount;
        this.topic = topic;
        this.createdAt = createdAt;
    }
}