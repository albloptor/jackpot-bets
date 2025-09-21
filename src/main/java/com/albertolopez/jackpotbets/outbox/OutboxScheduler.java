package com.albertolopez.jackpotbets.outbox;

import com.albertolopez.jackpotbets.persistence.entities.BetEntity;
import com.albertolopez.jackpotbets.persistence.entities.OutboxMessageEntity;
import com.albertolopez.jackpotbets.persistence.repository.BetRepository;
import com.albertolopez.jackpotbets.persistence.repository.OutboxRepository;
import com.jackpot.schema.BetMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final BetRepository betRepository;
    private final KafkaTemplate<String, BetMessage> kafkaTemplate;

    @Value("${jackpot.outbox.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${jackpot.outbox.scheduler.fixed-delay}")
    @Transactional
    public void processOutbox() {
        List<OutboxMessageEntity> messages = outboxRepository.findUnprocessedMessages(batchSize);

        for (OutboxMessageEntity messageEntity : messages) {
            Optional<BetEntity> bet = betRepository.findByBetId(messageEntity.getBetId().toString());
            bet.ifPresent(betEntity -> {
                BetMessage betMessage = BetMessage.newBuilder()
                        .setBetId(messageEntity.getBetId().toString())
                        .setJackpotId(betEntity.getJackpotId())
                        .setUserId(betEntity.getUserId())
                        .setBetAmount(betEntity.getBetAmount().doubleValue())
                        .setCreatedAt(messageEntity.getCreatedAt().toEpochMilli())
                        .build();
                try {
                    kafkaTemplate.send(messageEntity.getTopic(), messageEntity.getBetId().toString(), betMessage);
                    messageEntity.setProcessedAt(Instant.now());
                } catch (Exception e) {
                    // Log the error and continue with other messages
                    System.err.println("Failed to send Kafka message for uuid " + messageEntity.getBetId() + ": " + e.getMessage());
                }
            });
        }
    }
}