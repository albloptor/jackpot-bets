package com.albertolopez.jackpotbets.api;

import com.albertolopez.jackpotbets.api.dto.BetEvaluationResponse;
import com.albertolopez.jackpotbets.api.dto.BetRequest;
import com.albertolopez.jackpotbets.service.BetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bets")
@RequiredArgsConstructor
public class BetController {

    private final BetService betService;

    @PutMapping
    public ResponseEntity<Void> publishBet(@RequestHeader("X-User-ID") UUID userId,
                                           @Valid @RequestBody BetRequest request) {
        betService.recordBetAndPublish(userId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PutMapping("/{betId}/evaluate")
    public ResponseEntity<BetEvaluationResponse> evaluateBet(
            @PathVariable UUID betId,
            @RequestHeader("X-User-ID") UUID userId) {
        return ResponseEntity.ok(betService.evaluateBet(betId, userId));
    }
}