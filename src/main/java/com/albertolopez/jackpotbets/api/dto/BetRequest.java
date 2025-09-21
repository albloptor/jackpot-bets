package com.albertolopez.jackpotbets.api.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class BetRequest {

    @NotNull
    private UUID betId;

    @NotNull
    private String jackpotId;

    @NotNull
    private BigDecimal betAmount;
}