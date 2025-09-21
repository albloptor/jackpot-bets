package com.albertolopez.jackpotbets.persistence.entities;

import com.albertolopez.jackpotbets.domain.ContributionType;
import com.albertolopez.jackpotbets.domain.WinChanceType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "jackpots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JackpotEntity {

    @Id
    private String id;

    private BigDecimal initialPool;
    private BigDecimal currentPool;

    @Enumerated(EnumType.STRING)
    private ContributionType contributionType;

    private BigDecimal contributionPercentage;
    private BigDecimal contributionDecreaseRate;
    private BigDecimal contributionThreshold;

    @Enumerated(EnumType.STRING)
    private WinChanceType winChanceType;

    private BigDecimal winChancePercentage;
    private BigDecimal winChanceInitialChance;
    private BigDecimal winChanceLimit;
    private BigDecimal winChanceIncreaseRate;

}
