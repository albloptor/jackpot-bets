package com.albertolopez.jackpotbets.outbox;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class OutboxMessage {
    UUID betId;
    String topic;
    String message;
    Instant createdAt;
    Instant processedAt;
}