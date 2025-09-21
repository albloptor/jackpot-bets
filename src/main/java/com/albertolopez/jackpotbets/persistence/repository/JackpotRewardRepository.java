package com.albertolopez.jackpotbets.persistence.repository;

import com.albertolopez.jackpotbets.persistence.entities.JackpotRewardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JackpotRewardRepository extends JpaRepository<JackpotRewardEntity, Long> {

    Optional<JackpotRewardEntity> findByBetId(String betId);
}