package com.albertolopez.jackpotbets.persistence.entities;

import com.albertolopez.jackpotbets.domain.Bet;
import jakarta.persistence.Entity;
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
@Table(name = "bets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BetEntity {
    @Id
    private String betId;
    private String userId;
    private String jackpotId;
    private BigDecimal betAmount;
    private Instant createdAt;

    public BetEntity(Bet bet) {
        this.betId = bet.getBetId().toString();
        this.userId = bet.getUserId().toString();
        this.jackpotId = bet.getJackpotId();
        this.betAmount = bet.getBetAmount();
        this.createdAt = bet.getCreatedAt();
    }
}
