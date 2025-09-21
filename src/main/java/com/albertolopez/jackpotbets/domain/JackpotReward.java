package com.albertolopez.jackpotbets.domain;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class JackpotReward {
    UUID betId;
    UUID userId;
    UUID jackpotId;
    BigDecimal jackpotRewardAmount;
    Instant createdAt;
}