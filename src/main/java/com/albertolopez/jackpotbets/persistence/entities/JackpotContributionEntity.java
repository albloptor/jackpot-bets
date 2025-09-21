package com.albertolopez.jackpotbets.persistence.entities;

import com.albertolopez.jackpotbets.domain.JackpotContribution;
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
@Table(name = "jackpot_contributions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JackpotContributionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String betId;
    private String userId;
    private String jackpotId;
    private BigDecimal stakeAmount;
    private BigDecimal contributionAmount;
    private BigDecimal currentJackpotAmount;
    private Instant createdAt;

    public JackpotContributionEntity(JackpotContribution contribution) {
        this.betId = contribution.getBetId().toString();
        this.userId = contribution.getUserId().toString();
        this.jackpotId = contribution.getJackpotId();
        this.stakeAmount = contribution.getStakeAmount();
        this.contributionAmount = contribution.getContributionAmount();
        this.currentJackpotAmount = contribution.getCurrentJackpotAmount();
        this.createdAt = contribution.getCreatedAt();
    }
}
