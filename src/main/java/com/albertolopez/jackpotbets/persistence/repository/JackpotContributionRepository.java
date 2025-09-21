package com.albertolopez.jackpotbets.persistence.repository;

import com.albertolopez.jackpotbets.persistence.entities.JackpotContributionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JackpotContributionRepository extends JpaRepository<JackpotContributionEntity, Long> {

    Optional<JackpotContributionEntity> findByBetId(String betId);
}