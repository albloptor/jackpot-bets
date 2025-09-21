package com.albertolopez.jackpotbets

import com.albertolopez.jackpotbets.persistence.entities.BetEntity
import com.albertolopez.jackpotbets.persistence.repository.BetRepository
import com.albertolopez.jackpotbets.persistence.repository.JackpotRepository
import com.albertolopez.jackpotbets.persistence.repository.JackpotRewardRepository
import com.albertolopez.jackpotbets.persistence.repository.OutboxRepository
import groovy.json.JsonOutput
import io.restassured.RestAssured
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.spock.Testcontainers
import org.testcontainers.utility.DockerImageName
import spock.lang.Specification

import java.time.Instant

@SpringBootTest(classes = JackpotbetsApplication, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class BetControllerSpec extends Specification {

    private static final String FIXED_JACKPOT_ID = "8d75dbf1-e50e-42f5-91b6-829c10f0d275"
    private static final String VARIABLE_JACKPOT_ID = "fab085d6-4f0b-47ec-ac7a-5a7293d0d48f"

    @LocalServerPort
    int port

    @Autowired
    BetRepository betRepository

    @Autowired
    OutboxRepository outboxRepository

    @Autowired
    JackpotRepository jackpotRepository

    @Autowired
    JackpotRewardRepository jackpotRewardRepository

    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))

    def setupSpec() {
        kafkaContainer.start()
    }

    def cleanupSpec() {
        kafkaContainer.stop()
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.producer.bootstrap-servers", kafkaContainer::getBootstrapServers)
        registry.add("spring.kafka.consumer.bootstrap-servers", kafkaContainer::getBootstrapServers)
        registry.add("jackpot.outbox.schedule", { "*/1 * * * * *" }) // Faster schedule for testing
        registry.add("spring.kafka.properties.schema.registry.url", { "mock://test" }) // Mocking schema registry
    }

    def setup() {
        RestAssured.port = port
        jackpotRewardRepository.deleteAll()
        betRepository.deleteAll()
        outboxRepository.deleteAll()

        // Reset jackpot pools for each test
        def fixedJackpot = jackpotRepository.findById(FIXED_JACKPOT_ID).orElseThrow()
        fixedJackpot.currentPool = fixedJackpot.initialPool
        jackpotRepository.save(fixedJackpot)

        def variableJackpot = jackpotRepository.findById(VARIABLE_JACKPOT_ID).orElseThrow()
        variableJackpot.currentPool = variableJackpot.initialPool
        jackpotRepository.save(variableJackpot)
    }

    def "should record bet and publish message to kafka using outbox pattern"() {
        given: "A new bet request"
        def betId = UUID.randomUUID()
        def userId = UUID.randomUUID()
        def jackpotId = FIXED_JACKPOT_ID
        def betAmount = 100.00
        def betRequest = [
                betId    : betId,
                jackpotId: jackpotId,
                betAmount: betAmount
        ]

        when: "The bet is published to the API"
        def response = RestAssured.given()
                .contentType("application/json")
                .header("X-User-ID", userId)
                .body(JsonOutput.toJson(betRequest))
                .when()
                .put("/api/v1/bets")
                .then()
                .extract().response()

        then: "The API returns 202 Accepted"
        response.statusCode() == HttpStatus.ACCEPTED.value()

        and: "A record is created in the bets and outbox tables"
        def betEntity = betRepository.findById(betId.toString()).orElse(null)
        betEntity != null
        betEntity.betId == betId.toString()
        betEntity.userId == userId.toString()
        betEntity.jackpotId == jackpotId.toString()
        betEntity.betAmount == betAmount.toBigDecimal()
        betEntity.createdAt != null

        def outboxRecord = outboxRepository.findByBetId(betId).orElse(null)
        outboxRecord != null
        outboxRecord.betId == betId
        outboxRecord.userId == userId
        outboxRecord.jackpotId == jackpotId.toString()
        outboxRecord.betAmount == betAmount.toBigDecimal()
        outboxRecord.createdAt != null
        outboxRecord.topic == "jackpot-bets"
        outboxRecord.processedAt == null
    }

    def "should return 409 when a bet with the same ID already exists"() {
        given: "An existing bet record"
        def betId = UUID.randomUUID()
        def userId = UUID.randomUUID()
        def existingBet = new BetEntity(
                betId.toString(),
                UUID.randomUUID().toString(),
                FIXED_JACKPOT_ID,
                BigDecimal.TEN,
                Instant.now())
        betRepository.save(existingBet)

        and: "A new bet request with the same ID"
        def betRequest = [
                betId    : betId,
                jackpotId: FIXED_JACKPOT_ID,
                betAmount: 200.00
        ]

        when: "The bet is published to the API"
        def response = RestAssured.given()
                .contentType("application/json")
                .header("X-User-ID", userId)
                .body(JsonOutput.toJson(betRequest))
                .when()
                .put("/api/v1/bets")
                .then()
                .extract().response()

        then: "The API returns 409 Conflict"
        response.statusCode() == HttpStatus.CONFLICT.value()

        and: "No new record is inserted into the outbox table"
        outboxRepository.count() == 0
    }

    def "should evaluate a bet for a fixed chance jackpot"() {
        given: "A bet for a fixed jackpot"
        def betId = UUID.randomUUID()
        def userId = UUID.randomUUID()
        def bet = new BetEntity(
                betId.toString(),
                userId.toString(),
                FIXED_JACKPOT_ID,
                BigDecimal.TEN,
                Instant.now())
        betRepository.save(bet)

        when: "The bet is evaluated"
        def response = RestAssured.given()
                .header("X-User-ID", userId)
                .when()
                .put("/api/v1/bets/{betId}/evaluate", betId)
                .then()
                .extract().response()

        then: "The API returns 200 OK and the response body is correct"
        response.statusCode() == HttpStatus.OK.value()
        def json = response.jsonPath()
        json.getString("betId") == betId.toString()
        json.getBoolean("jackpotWon") instanceof Boolean
        json.getDouble("rewardAmount") >= 0
    }

    def "should win a bet for a fixed chance jackpot"() {
        given: "A bet for a fixed jackpot with a guaranteed win chance"
        def betId = UUID.randomUUID()
        def userId = UUID.randomUUID()
        def jackpotId = FIXED_JACKPOT_ID

        def jackpot = jackpotRepository.findById(jackpotId).orElseThrow()
        jackpot.winChancePercentage = BigDecimal.valueOf(100.00)
        jackpotRepository.save(jackpot)

        def bet = new BetEntity(betId.toString(), userId.toString(), jackpotId, BigDecimal.TEN, Instant.now())
        betRepository.save(bet)

        when: "The bet is evaluated"
        def response = RestAssured.given()
                .header("X-User-ID", userId)
                .when()
                .put("/api/v1/bets/{betId}/evaluate", betId)
                .then()
                .extract().response()

        then: "The API returns 200 OK, jackpot is won, and a reward record is created"
        response.statusCode() == HttpStatus.OK.value()
        def json = response.jsonPath()
        json.getBoolean("jackpotWon")
        jackpotRewardRepository.findByBetId(betId.toString()).isPresent()
    }

    def "should evaluate a bet for a variable chance jackpot and reset pool on win"() {
        given: "A bet for a variable jackpot"
        def betId = UUID.randomUUID()
        def userId = UUID.randomUUID()
        def jackpotId = VARIABLE_JACKPOT_ID
        def bet = new BetEntity(betId.toString(), userId.toString(), jackpotId, BigDecimal.TEN, Instant.now())
        betRepository.save(bet)

        and: "The jackpot pool is large enough to guarantee a win"
        def jackpot = jackpotRepository.findById(jackpotId).orElseThrow()
        jackpot.currentPool = jackpot.winChanceLimit
        jackpotRepository.save(jackpot)

        def initialPool = jackpot.initialPool
        def currentPoolBefore = jackpot.currentPool

        when: "The bet is evaluated"
        def response = RestAssured.given()
                .header("X-User-ID", userId)
                .when()
                .put("/api/v1/bets/{betId}/evaluate", betId)
                .then()
                .extract().response()

        then: "The API returns 200 OK, jackpot is won, and the pool is reset"
        response.statusCode() == HttpStatus.OK.value()
        def json = response.jsonPath()
        json.getBoolean("jackpotWon")
        json.getDouble("rewardAmount") == currentPoolBefore.doubleValue()

        and: "The jackpot pool is reset to its initial value"
        def updatedJackpot = jackpotRepository.findById(jackpotId).orElseThrow()
        updatedJackpot.currentPool == initialPool

        and: "A jackpot reward record is created"
        jackpotRewardRepository.findByBetId(betId.toString()).isPresent()
    }

    def "should fail to evaluate a bet if it belongs to another user"() {
        given: "A bet belonging to a different user"
        def betId = UUID.randomUUID()
        def actualUserId = UUID.randomUUID()
        def bet = new BetEntity(betId.toString(), actualUserId.toString(), FIXED_JACKPOT_ID, BigDecimal.TEN, Instant.now())
        betRepository.save(bet)

        and: "A request from a different user"
        def maliciousUserId = UUID.randomUUID()

        when: "The request is made"
        def response = RestAssured.given()
                .header("X-User-ID", maliciousUserId)
                .when()
                .put("/api/v1/bets/{betId}/evaluate", betId)
                .then()
                .extract().response()

        then: "The API returns 403 Forbidden"
        response.statusCode() == HttpStatus.FORBIDDEN.value()
    }

    def "should return 400 Bad Request for invalid bet requests"() {
        given: "A new bet request with invalid data"
        def userId = UUID.randomUUID()
        def betRequest = [
                betId    : betId,
                jackpotId: jackpotId,
                betAmount: betAmount
        ]

        when: "The request is sent to the API"
        def response = RestAssured.given()
                .contentType("application/json")
                .header("X-User-ID", userId)
                .body(JsonOutput.toJson(betRequest))
                .when()
                .put("/api/v1/bets")
                .then()
                .extract().response()

        then: "The API returns 400 Bad Request"
        response.statusCode() == HttpStatus.BAD_REQUEST.value()

        where:
        betId             | jackpotId        | betAmount
        UUID.randomUUID() | FIXED_JACKPOT_ID | null
        UUID.randomUUID() | null             | 100.00
        null              | FIXED_JACKPOT_ID | 100.00
    }

    def "should return 400 Bad Request when X-User-ID header is missing"() {
        given: "A valid bet request"
        def betRequest = [
                betId    : UUID.randomUUID(),
                jackpotId: FIXED_JACKPOT_ID,
                betAmount: 100.00
        ]

        when: "The request is sent to the API without the X-User-ID header"
        def response = RestAssured.given()
                .contentType("application/json")
                .body(JsonOutput.toJson(betRequest))
                .when()
                .put("/api/v1/bets")
                .then()
                .extract().response()

        then: "The API returns 400 Bad Request with a message about the missing header"
        response.statusCode() == HttpStatus.BAD_REQUEST.value()
        response.asString().contains("Required request header 'X-User-ID' for method parameter type UUID is not present")
    }
}