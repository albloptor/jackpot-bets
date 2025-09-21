package com.albertolopez.jackpotbets.persistence.entities;

import com.albertolopez.jackpotbets.domain.JackpotReward;
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

@Entity
@Table(name = "jackpot_rewards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JackpotRewardEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String betId;
    private String userId;
    private String jackpotId;
    private BigDecimal jackpotRewardAmount;
    private Instant createdAt;

    public JackpotRewardEntity(JackpotReward reward) {
        this.betId = reward.getBetId().toString();
        this.userId = reward.getUserId().toString();
        this.jackpotId = reward.getJackpotId().toString();
        this.jackpotRewardAmount = reward.getJackpotRewardAmount();
        this.createdAt = reward.getCreatedAt();
    }
}