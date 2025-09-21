package com.albertolopez.jackpotbets.domain;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class Bet {
    UUID betId;
    UUID userId;
    String jackpotId;
    BigDecimal betAmount;
    Instant createdAt;
}
