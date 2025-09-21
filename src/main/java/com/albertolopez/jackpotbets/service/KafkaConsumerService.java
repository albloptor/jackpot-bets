package com.albertolopez.jackpotbets.service;

import com.albertolopez.jackpotbets.domain.JackpotContribution;
import com.albertolopez.jackpotbets.persistence.entities.JackpotContributionEntity;
import com.albertolopez.jackpotbets.persistence.entities.JackpotEntity;
import com.albertolopez.jackpotbets.persistence.repository.JackpotContributionRepository;
import com.albertolopez.jackpotbets.persistence.repository.JackpotRepository;
import com.jackpot.schema.BetMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static java.math.RoundingMode.HALF_UP;

@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final JackpotRepository jackpotRepository;
    private final JackpotContributionRepository contributionRepository;

    @KafkaListener(topics = "${jackpot.kafka-topic}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void listen(BetMessage betMessage) {
        JackpotEntity jackpot = jackpotRepository.findById(betMessage.getJackpotId())
                .orElseThrow(() -> new IllegalStateException("Jackpot not found for id: " + betMessage.getJackpotId()));

        BigDecimal contributionAmount = calculateContribution(jackpot, betMessage.getBetAmount());
        jackpot.setCurrentPool(jackpot.getCurrentPool().add(contributionAmount));
        jackpotRepository.save(jackpot);

        JackpotContribution contribution = JackpotContribution.builder()
                .betId(UUID.fromString(betMessage.getBetId()))
                .userId(UUID.fromString(betMessage.getUserId()))
                .jackpotId(jackpot.getId())
                .stakeAmount(BigDecimal.valueOf(betMessage.getBetAmount()))
                .contributionAmount(contributionAmount)
                .currentJackpotAmount(jackpot.getCurrentPool())
                .createdAt(Instant.now())
                .build();

        contributionRepository.save(new JackpotContributionEntity(contribution));
    }

    private BigDecimal calculateContribution(JackpotEntity jackpot, double betAmount) {
        BigDecimal betAmt = BigDecimal.valueOf(betAmount);
        return switch (jackpot.getContributionType()) {
            case FIXED_CONTRIBUTION -> fixedContribution(jackpot, betAmt);
            case VARIABLE_CONTRIBUTION -> variableContribution(jackpot, betAmt);
        };
    }

    private static BigDecimal fixedContribution(JackpotEntity jackpot, BigDecimal betAmt) {
        return betAmt.multiply(jackpot.getContributionPercentage());
    }

    private static BigDecimal variableContribution(JackpotEntity jackpot, BigDecimal betAmt) {
        BigDecimal currentPool = jackpot.getCurrentPool();
        BigDecimal threshold = jackpot.getContributionThreshold();
        BigDecimal decreaseRate = jackpot.getContributionDecreaseRate();
        BigDecimal initialPercentage = jackpot.getContributionPercentage();

        if (currentPool.compareTo(threshold) >= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal contributionPercentage = initialPercentage.subtract(
                (currentPool.divide(threshold, HALF_UP).multiply(decreaseRate))
        );

        return betAmt.multiply(contributionPercentage);
    }
}