package com.albertolopez.jackpotbets.persistence.repository;

import com.albertolopez.jackpotbets.persistence.entities.BetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BetRepository extends JpaRepository<BetEntity, String> {

    Optional<BetEntity> findByBetId(String betId);
}