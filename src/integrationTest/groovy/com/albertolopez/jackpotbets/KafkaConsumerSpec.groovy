package com.albertolopez.jackpotbets

import com.albertolopez.jackpotbets.persistence.entities.OutboxMessageEntity
import com.albertolopez.jackpotbets.persistence.repository.BetRepository
import com.albertolopez.jackpotbets.persistence.repository.JackpotContributionRepository
import com.albertolopez.jackpotbets.persistence.repository.JackpotRepository
import com.albertolopez.jackpotbets.persistence.repository.OutboxRepository
import org.awaitility.Awaitility
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.spock.Testcontainers
import org.testcontainers.utility.DockerImageName
import spock.lang.Ignore
import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.TimeUnit

@SpringBootTest(classes = JackpotbetsApplication)
@ActiveProfiles("test")
@Testcontainers
@EnableKafka
class KafkaConsumerSpec extends Specification {

    @Autowired
    OutboxRepository outboxRepository

    @Autowired
    JackpotRepository jackpotRepository

    @Autowired
    JackpotContributionRepository contributionRepository

    @Autowired
    BetRepository betRepository

    @Container
    public static final Network network = Network.newNetwork()

    @Container
    public static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
            .withNetwork(network)
            .withNetworkAliases("kafka")

    @Container
    public static final GenericContainer<? extends GenericContainer> schemaRegistry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.6.1"))
            .withNetwork(network)
            .withNetworkAliases("schema-registry")
            .withExposedPorts(8081)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
            .dependsOn(kafka)

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers)
        registry.add("spring.kafka.consumer.bootstrap-servers", kafka::getBootstrapServers)
        registry.add("jackpot.outbox.scheduler.fixed-delay", { "200" })
        registry.add("spring.kafka.properties.schema.registry.url", () -> "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getFirstMappedPort())
        registry.add("spring.kafka.producer.properties.value.serializer", () -> "io.confluent.kafka.serializers.KafkaAvroSerializer")
        registry.add("spring.kafka.consumer.properties.value.deserializer", () -> "io.confluent.kafka.serializers.KafkaAvroDeserializer")
        registry.add("spring.kafka.consumer.properties.specific.avro.reader", () -> true)
    }

    def setupSpec() {
        kafka.start()
        schemaRegistry.start()
    }

    def cleanupSpec() {
        schemaRegistry.stop()
        kafka.stop()
    }

    def setup() {
        betRepository.deleteAll()
        outboxRepository.deleteAll()
        contributionRepository.deleteAll()

        def fixedJackpot = jackpotRepository.findById("fixed-jackpot-id").orElseThrow()
        fixedJackpot.currentPool = fixedJackpot.initialPool
        jackpotRepository.save(fixedJackpot)
    }

    @Ignore
    def "should process a message from the outbox and update the jackpot pool"() {
        given: "A record in the outbox table"
        def betId = UUID.randomUUID()
        def userId = UUID.randomUUID()
        def jackpotId = "fixed-jackpot-id"
        def betAmount = 50.00
        def initialJackpot = jackpotRepository.findById(jackpotId).orElseThrow()
        def initialPool = initialJackpot.currentPool

        def outboxMessageEntity = new OutboxMessageEntity(betId, jackpotId, userId, betAmount, "jackpot-bets", Instant.now())

        outboxRepository.save(outboxMessageEntity)

        when: "The scheduled job runs and the Kafka consumer processes the message"

        then: "The jackpot pool is updated and the outbox message is processed"
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until {
            def updatedJackpot = jackpotRepository.findById(jackpotId).orElse(null)
            updatedJackpot != null && updatedJackpot.currentPool > initialPool
        }

        def finalJackpot = jackpotRepository.findById(jackpotId).orElseThrow()
        def expectedContribution = betAmount * initialJackpot.contributionPercentage
        finalJackpot.currentPool == initialPool.add(expectedContribution.toBigDecimal())

        and: "A contribution record is created in the database"
        contributionRepository.findByBetId(betId.toString()).isPresent()

        and: "The outbox message is marked as processed"
        def processedMessage = outboxRepository.findByBetId(betId).orElseThrow()
        processedMessage.processedAt != null
    }
}