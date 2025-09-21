package com.albertolopez.jackpotbets.service;

import com.albertolopez.jackpotbets.api.dto.BetRequest;
import com.albertolopez.jackpotbets.api.dto.BetEvaluationResponse;
import com.albertolopez.jackpotbets.domain.Bet;
import com.albertolopez.jackpotbets.domain.JackpotReward;
import com.albertolopez.jackpotbets.exception.BetNotFoundException;
import com.albertolopez.jackpotbets.persistence.entities.BetEntity;
import com.albertolopez.jackpotbets.persistence.entities.JackpotEntity;
import com.albertolopez.jackpotbets.persistence.entities.JackpotRewardEntity;
import com.albertolopez.jackpotbets.persistence.entities.OutboxMessageEntity;
import com.albertolopez.jackpotbets.persistence.repository.BetRepository;
import com.albertolopez.jackpotbets.persistence.repository.JackpotRepository;
import com.albertolopez.jackpotbets.persistence.repository.JackpotRewardRepository;
import com.albertolopez.jackpotbets.persistence.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BetService {

    public static final double MAX_PROBABILITY = 1.0;

    private final BetRepository betRepository;
    private final OutboxRepository outboxRepository;
    private final JackpotRepository jackpotRepository;
    private final JackpotRewardRepository jackpotRewardRepository;

    @Transactional
    public void recordBetAndPublish(UUID userId, BetRequest request) {
        Optional<BetEntity> existingBet = betRepository.findById(request.getBetId().toString());
        if (existingBet.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bet already exists");
        }

        Bet bet = Bet.builder()
                .betId(request.getBetId())
                .userId(userId)
                .jackpotId(request.getJackpotId())
                .betAmount(request.getBetAmount())
                .createdAt(Instant.now())
                .build();

        BetEntity betEntity = new BetEntity(bet);
        betRepository.save(betEntity);
        outboxRepository.save(new OutboxMessageEntity(
                bet.getBetId(),
                bet.getJackpotId(),
                bet.getUserId(),
                bet.getBetAmount(),
                "jackpot-bets",
                Instant.now()));
    }

    @Transactional
    public BetEvaluationResponse evaluateBet(UUID betId, UUID userId) {
        BetEntity betEntity = betRepository.findById(betId.toString())
                .orElseThrow(() -> new BetNotFoundException("Bet not found with id: " + betId));

        if (!betEntity.getUserId().equals(userId.toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bet does not belong to this user");
        }

        JackpotEntity jackpotEntity = jackpotRepository.findById(betEntity.getJackpotId())
                .orElseThrow(() -> new IllegalStateException("Jackpot not found for id: " + betEntity.getJackpotId()));

        Optional<JackpotRewardEntity> existingReward = jackpotRewardRepository.findByBetId(betId.toString());
        return existingReward
                .map(jackpotReward -> evaluationResponseForExistingReward(betId, jackpotReward))
                .orElseGet(() -> newEvaluationResponse(betId, userId, jackpotEntity));
    }

    private BetEvaluationResponse newEvaluationResponse(UUID betId, UUID userId, JackpotEntity jackpotEntity) {
        double chance = calculateWinChance(jackpotEntity);
        double randomNumber = new Random().nextDouble();
        boolean jackpotWon = randomNumber < chance;
        JackpotReward reward = JackpotReward.builder()
                .betId(betId)
                .userId(userId)
                .jackpotId(UUID.fromString(jackpotEntity.getId()))
                .jackpotRewardAmount(jackpotWon ? jackpotEntity.getCurrentPool() : BigDecimal.ZERO)
                .createdAt(Instant.now())
                .build();

        if (jackpotWon) {
            jackpotEntity.setCurrentPool(jackpotEntity.getInitialPool());
            jackpotRepository.save(jackpotEntity);
        }
        jackpotRewardRepository.save(new JackpotRewardEntity(reward));

        return BetEvaluationResponse.builder()
                .betId(betId.toString())
                .jackpotWon(jackpotWon)
                .rewardAmount(reward.getJackpotRewardAmount())
                .build();
    }

    private static BetEvaluationResponse evaluationResponseForExistingReward(UUID betId, JackpotRewardEntity existingReward) {
        return BetEvaluationResponse.builder()
                .betId(betId.toString())
                .jackpotWon(existingReward.getJackpotRewardAmount().compareTo(BigDecimal.ZERO) > 0)
                .rewardAmount(existingReward.getJackpotRewardAmount())
                .build();
    }

    private double calculateWinChance(JackpotEntity jackpot) {
        return switch (jackpot.getWinChanceType()) {
            case FIXED_CHANCE -> fixedChance(jackpot);
            case VARIABLE_CHANCE -> variableChance(jackpot);
        };
    }

    private static double fixedChance(JackpotEntity jackpot) {
        return jackpot.getWinChancePercentage().doubleValue();
    }

    private static double variableChance(JackpotEntity jackpot) {
        BigDecimal initialChance = jackpot.getWinChanceInitialChance();
        BigDecimal increaseRate = jackpot.getWinChanceIncreaseRate();
        BigDecimal currentPool = jackpot.getCurrentPool();
        BigDecimal limit = jackpot.getWinChanceLimit();

        if (currentPool.compareTo(limit) >= 0) {
            return MAX_PROBABILITY;
        }

        BigDecimal increasedChance = currentPool.divide(limit, RoundingMode.HALF_UP).multiply(increaseRate);
        double chance = initialChance.add(increasedChance).doubleValue();

        return Math.min(MAX_PROBABILITY, chance);
    }
}
