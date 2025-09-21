package com.albertolopez.jackpotbets.persistence.repository;

import com.albertolopez.jackpotbets.persistence.entities.JackpotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JackpotRepository extends JpaRepository<JackpotEntity, String> {
}

