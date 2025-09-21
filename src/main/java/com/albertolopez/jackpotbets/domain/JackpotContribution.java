package com.albertolopez.jackpotbets.domain;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class JackpotContribution {
    UUID betId;
    UUID userId;
    String jackpotId;
    BigDecimal stakeAmount;
    BigDecimal contributionAmount;
    BigDecimal currentJackpotAmount;
    Instant createdAt;
}