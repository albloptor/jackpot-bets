package com.albertolopez.jackpotbets.persistence.repository;

import com.albertolopez.jackpotbets.persistence.entities.OutboxMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessageEntity, Long> {

    @Query(value = "SELECT * FROM outbox_messages WHERE processed_at IS NULL ORDER BY created_at ASC LIMIT ?1",
            nativeQuery = true)
    List<OutboxMessageEntity> findUnprocessedMessages(int limit);

    Optional<OutboxMessageEntity> findByBetId(UUID betId);
}