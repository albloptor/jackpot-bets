package com.albertolopez.jackpotbets.api.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class BetEvaluationResponse {
    private String betId;
    private boolean jackpotWon;
    private BigDecimal rewardAmount;
}