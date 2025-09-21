# Jackpot Bets

This is a backend application that processes bets for jackpot contributions and rewards. It's built with Java 21, Spring Boot, and uses Kafka for asynchronous message processing.

## Technologies Used

- **Java 21**
- **Spring Boot**
- **Gradle**
- **Apache Kafka**
- **H2 Database**
- **Flyway:** for database migration and schema management.
- **Hibernate:** ORM for interacting with the database.
- **Lombok** library to reduce boilerplate code.
- **Avro:** data serialization system for Kafka messages.
- **Testcontainers:** to run real services (like Kafka) in Docker containers for integration tests.
- **Spock:** Testing framework for writing expressive and readable tests.

## Prerequisites

- JDK 21 or later
- Docker (for running Testcontainers)

## How to Build and Run

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/your-username/jackpot-service.git](https://github.com/your-username/jackpot-service.git)
    cd jackpot-service
    ```
2. **Start Docker desktop or similar on your machine**

3. **Run docker compose in the root of the project**: This will spin up Kafka, Schema registry and Zookeeper locally.
    ```bash
    docker compose up
    ```

4. **Build the application:**
    ```bash
    ./gradlew clean build
    ```
   The tests will run as part of the build. There is a custom `integrationTest` gradle task to run the integration tests.

5. **Run the application:**
    The application will start with an in-memory H2 database.
    ```bash
    ./gradlew bootRun
    ```

## API Endpoints

The application exposes two main API endpoints:

### 1. Publish a Bet

This endpoint uses a transactional outbox pattern to ensure a bet is recorded in the database and then asynchronously published to Kafka.

-   **Endpoint:** `PUT /api/v1/bets`
- **Request Headers:**
  -   `X-User-ID`: The ID of the user placing the bet.
-   **Request Body:**
    ```json
    {
      "betId": "UUID",
      "jackpotId": "UUID",
      "betAmount": 100.50
    }
    ```
-   **Responses:**
    -   `202 Accepted`: If the bet is successfully accepted and added to the outbox.
    -   `409 Conflict`: If a bet with the same `betId` already exists.

#### Two jackpots have been initialised in the database as part of the Flyway migration script:
```
Jackpot with fixed contribution and fixed reward: 8d75dbf1-e50e-42f5-91b6-829c10f0d275
Jackpot with variable contribution and variable reward: fab085d6-4f0b-47ec-ac7a-5a7293d0d48f
```
### 2. Evaluate a Bet for Jackpot

This endpoint checks if a specific bet wins a jackpot based on its configuration.

-   **Endpoint:** `PUT /api/v1/bets/{betId}/evaluate`
-   **Path Parameters:**
    -   `betId`: The ID of the bet to evaluate.
-   **Request Headers:**
    -   `X-User-ID`: The ID of the user placing the request. This is used to verify that the bet belongs to the user.
-   **Responses:**
    -   `200 OK`: If the bet is successfully evaluated. The response body will contain the reward amount.
    -   `404 Not Found`: If the bet is not found.
    -   `403 Forbidden`: If the `betId` does not belong to the user identified by `X-User-ID`.

## Database Schema

The database schema is managed by Flyway. The `V1__initial_schema.sql` migration script creates the necessary tables:

-   `jackpots`: Stores jackpot configurations and current pool amounts.
-   `bets`: Stores the initial bet information.
-   `jackpot_contributions`: Stores records of each bet's contribution to a jackpot.
-   `jackpot_rewards`: Stores records of jackpot wins and their amounts.
-   `outbox_messages`: The table for the transactional outbox pattern.

The contents of the database can be checked in http://localhost:8080/h2-console/. Use:
```
JDBC url: jdbc:h2:mem:jackpotdb
Username: sa
Password: password
```

## Configuration

All application and jackpot logic properties are managed in `src/main/resources/application.yml`. You can modify this file to change Kafka settings, jackpot configurations, and background process schedules.

## Design decisions

There are some conscious design decisions that I made during the development of this project:

- I decided to use Avro for Kafka message serialization and deserialization. In production environments it's common as messages can be stored more efficiently and strict schemas can be evolved over time as required and managed by the schema registry.
- I decided to make the `PUT /api/v1/bets` endpoint idempotent. This is to ensure that the same bet id is not submitted twice. If a bet_id is submitted twice, the second time will return a 409 Conflict, signaling that it has been submitted before.
  - How is it done? Instead of publishing the message directly to Kafka upon receiving the request, a bet record is inserted into the database, where the bet_id is unique. If two concurrent threads try to insert the same bet_id into the database, the second one will fail. 
  - Transactional outbox pattern: in the same database transaction, I insert a record into the outbox_messages table. This table is eventually processed by a scheduled job, which is in charge of publishing all unpublished bets to Kafka. After doing so, it sets the `processed_at` column on the processed record.
- I decided to use integration tests with TestContainers in order to spin up a kafka cluster and schema registry and verify that the behaviour of the application is as expected, without testing internal implementation details.
- For running the application locally, kafka, schema registry and zookeeper need to be running. For convenience, I created a docker compose file to start all three.
- I've used Hikari in order to create a connection pool to the database. This is what is usually done in real environments.

## Improvements
Due to time constraints, there are some things I couldn't implement in a way I normally would have. In a real-world scenario, I would have made the following improvements:
- Move business logic to domain objects: I started creating domain objects but ended up relying on JPA entities, which leaked to the service layer. In a real-world scenario, I would have encapsulated JPA entities in repository classes and map them to Java domain objects which would be used throughout the application. I would then have moved the business logic from the service layer to the domain objects, in a DDD fashion.
- Definitely add more tests: I would have tested more behaviours (usually I follow TDD) and I would have written unit tests for the domain classes, especially the ones with complex business logic around contribution and reward calculation.
- Use domain objects to encapsulate UUIDs or Strings. For instance, JackpotId should be an immutable class wrapping a String or a UUID. That way it's clearer when objects are passed around as parameters.
- Pessimistic locking when updating the jackpots. Since contention is likely in such a system, I would have locked rows for update, preferring pessimistic locking to optimistic locking.
- Use the Strategy pattern in order to inject contribution and reward calculations into each Jackpot object. That way, adding new configurations would be more scalable and cleaner over time.
- Clean up the database schema. In some tables I've used a surrogate primary key, but in others I use business values for primary keys, like jackpot_id or bet_id.
- Fix the KafkaConsumerSpec (it's currently ignored). I couldn't figure out why the Kafka consumer listen method wasn't being invoked as part of the integration test (it is when running the app with bootRun). It would have allowed me to properly test that inserting a record into the outbox_messages table results in an eventual insertion of a jackpot contribution record and the update of the jackpot pool.
- Add bet statuses. It will probably be useful to be able to tell whether a bet has been placed, processed, rewarded or not rewarded. For this, a status column could be added to the bets table.
- Create a custom Money class for monetary operations. It would encapsulate a BigDecimal instance and expose only the relevant methods for the domain.
- Reference the Kafka topic name specified in the application.yml throughout the code base without hardcoding it.
- Inject Random and Clock with dependency injection. This would make it possible to test edge cases around probability and time.