# Testing Strategy – ASMS
## JUnit 5 · Mockito · Testcontainers · Karate

---

## 1. Testing Pyramid

```
                 ╔══════════╗
                 ║  E2E /   ║  ← Karate API tests (Exercises 1.1–1.4)
                 ║  API     ║     ~10% of tests
                 ╠══════════╣
              ╔══╣Integration╠══╗
              ║  ║   Tests   ║  ║  ← Testcontainers (real MongoDB, Kafka, Redis)
              ║  ╚══════════╝  ║     ~30% of tests
           ╔══╣                ╠══╗
           ║  ║   Unit Tests   ║  ║  ← JUnit 5 + Mockito 5
           ║  ╚════════════════╝  ║     ~60% of tests
           ╚══════════════════════╝

Target: 70%+ code coverage (enforced by SonarQube quality gate)
```

---

## 2. Unit Tests (JUnit 5 + Mockito 5)

### TicketService Test

```java
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock TicketRepository repository;
    @Mock TicketEventPort   eventPort;
    @InjectMocks TicketServiceImpl ticketService;

    private static final String USER_ID = "usr_test_01";

    @Test
    @DisplayName("createTicket — should persist ticket and publish Kafka event")
    void createTicket_shouldPersistAndPublishEvent() {
        // given
        TicketRequest req = new TicketRequest(
            "Water leakage", "Pipe burst under sink",
            TicketCategory.PLUMBING, TicketPriority.P2_HIGH
        );
        Ticket savedTicket = Ticket.builder()
            .id("tkt_01").title("Water leakage").raisedBy(USER_ID)
            .status(TicketStatus.OPEN).build();
        when(repository.save(any(Ticket.class))).thenReturn(savedTicket);

        // when
        Ticket result = ticketService.createTicket(req, USER_ID);

        // then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(result.raisedBy()).isEqualTo(USER_ID);
        verify(eventPort).publishCreated(result);
        verify(repository).save(any(Ticket.class));
    }

    @Test
    @DisplayName("createTicket — should set status to OPEN initially")
    void createTicket_initialStatusShouldBeOpen() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Ticket result = ticketService.createTicket(
            new TicketRequest("Test", "Desc", TicketCategory.GENERAL, TicketPriority.P3_MEDIUM),
            USER_ID
        );
        assertThat(result.status()).isEqualTo(TicketStatus.OPEN);
    }

    @ParameterizedTest
    @EnumSource(TicketCategory.class)
    @DisplayName("createTicket — should accept all valid categories")
    void createTicket_allCategoriesAccepted(TicketCategory category) {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertDoesNotThrow(() ->
            ticketService.createTicket(new TicketRequest("T", "D", category, TicketPriority.P4_LOW), USER_ID)
        );
    }

    @Test
    @DisplayName("updateStatus — invalid transition should throw exception")
    void updateStatus_invalidTransition_shouldThrow() {
        Ticket closedTicket = Ticket.builder().id("tkt_01").status(TicketStatus.CLOSED).build();
        when(repository.findById("tkt_01")).thenReturn(Optional.of(closedTicket));

        assertThrows(InvalidTransitionException.class,
            () -> ticketService.updateStatus("tkt_01", TicketStatus.OPEN, "reopen", USER_ID)
        );
        verify(eventPort, never()).publishStatusChanged(any(), any());
    }
}
```

### PaymentService Test

```java
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock UpiPaymentStrategy    upiStrategy;
    @Mock CardPaymentStrategy   cardStrategy;
    @Mock PaymentRepository     paymentRepository;
    @InjectMocks PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        when(upiStrategy.supportedMethod()).thenReturn(PaymentMethod.UPI);
        when(cardStrategy.supportedMethod()).thenReturn(PaymentMethod.CREDIT_CARD);
        paymentService = new PaymentServiceImpl(
            List.of(upiStrategy, cardStrategy), paymentRepository
        );
    }

    @Test
    void processPayment_upi_shouldDelegateToUpiStrategy() {
        PaymentRequest req = new PaymentRequest(
            new BigDecimal("5500"), "INR", PaymentMethod.UPI,
            PaymentReferenceType.MAINTENANCE, "inv_01"
        );
        when(upiStrategy.execute(req)).thenReturn(PaymentResult.success("txn_001"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.processPayment(req, "usr_01");

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(upiStrategy).execute(req);
        verify(cardStrategy, never()).execute(any());
    }

    @Test
    void processPayment_unsupportedMethod_shouldThrow() {
        PaymentRequest req = new PaymentRequest(
            BigDecimal.TEN, "INR", PaymentMethod.NET_BANKING,
            PaymentReferenceType.AMENITY_BOOKING, "booking_01"
        );
        assertThrows(UnsupportedPaymentMethodException.class,
            () -> paymentService.processPayment(req, "usr_01")
        );
    }
}
```

### Lift Algorithm Test

```java
class LiftControllerTest {

    @Test
    @DisplayName("SCAN — should serve floors in order, not call order")
    void scanAlgorithm_shouldServeFLoorsInOrder() throws InterruptedException {
        Lift lift = new Lift(1);
        List<Integer> visitedFloors = new CopyOnWriteArrayList<>();
        lift.setFloorVisitCallback(visitedFloors::add);

        Thread liftThread = Thread.ofVirtual().start(lift);
        lift.callLift(7);
        lift.callLift(2);
        lift.callLift(9);
        lift.callLift(4);
        Thread.sleep(15_000);
        lift.shutdown();
        liftThread.join();

        // SCAN order: 2, 4, 7, 9 (ascending), not call order 7, 2, 9, 4
        assertThat(visitedFloors).containsExactly(2, 4, 7, 9);
    }

    @Test
    @DisplayName("Lift failure — should trigger recovery and reset to ground floor")
    void liftFailure_shouldRecover() {
        Lift lift = new Lift(1);
        lift.simulateFailureAt(5);

        lift.callLift(10);
        Thread.ofVirtual().start(lift);

        await().atMost(Duration.ofSeconds(10))
            .until(() -> lift.getCurrentFloor() == 0);

        assertThat(lift.getStatus()).isEqualTo(LiftStatus.IDLE);
    }
}
```

---

## 3. Integration Tests (Testcontainers)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TicketControllerIntegrationTest {

    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7.0")
        .withExposedPorts(27017);

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
        .withKraft();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired TestRestTemplate restTemplate;
    @Autowired TicketRepository ticketRepository;

    @BeforeEach
    void setUp() { ticketRepository.deleteAll(); }

    @Test
    void createTicket_end_to_end_persists_to_mongodb() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "usr_test");
        headers.set("X-User-Role", "OWNER");
        headers.setContentType(MediaType.APPLICATION_JSON);

        TicketRequest req = new TicketRequest(
            "Test ticket", "Integration test", TicketCategory.GENERAL, TicketPriority.P3_MEDIUM
        );

        ResponseEntity<TicketResponse> response = restTemplate.exchange(
            "/api/v1/tickets", HttpMethod.POST,
            new HttpEntity<>(req, headers), TicketResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("OPEN");

        // Verify persisted in real MongoDB
        assertThat(ticketRepository.findById(response.getBody().id())).isPresent();
    }

    @Test
    void createTicket_missingTitle_returns422() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "usr_test");
        headers.set("X-User-Role", "OWNER");
        headers.setContentType(MediaType.APPLICATION_JSON);

        TicketRequest req = new TicketRequest("", "desc", TicketCategory.GENERAL, TicketPriority.P4_LOW);

        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
            "/api/v1/tickets", HttpMethod.POST,
            new HttpEntity<>(req, headers), ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getDetail()).contains("Title is required");
    }
}
```

---

## 4. Karate API Tests (Exercises 1.1 – 1.4)

### Feature File

```gherkin
# karate-tests/src/test/resources/asms/exercises/ApiChainTest.feature
Feature: ECA Testing Exercise — API Chain (Exercises 1.1 to 1.4)

  Background:
    * url 'https://reqres.in'

  # ─────────────────────────────────────────────────────────────
  # Exercise 1.1 — GET List of Users, find Michael
  # ─────────────────────────────────────────────────────────────
  Scenario Outline: Exercise 1.1 — Get users page 2, find Michael, validate schema
    Given path '/api/users'
    And param page = 2
    When method GET
    Then status 200

    # Schema validation
    And match response ==
    """
    {
      page:    '#number',
      per_page:'#number',
      total:   '#number',
      total_pages: '#number',
      data: '#[]',
      support: '#object'
    }
    """

    # Find Michael
    * def michael = karate.jsonPath(response.data, "$[?(@.first_name=='Michael')]")[0]
    * assert michael != null
    * karate.log('Michael found:', michael)

    # Store for downstream exercises
    * def michaelEmail = michael.email
    * def michaelId    = michael.id

    And match michael contains { first_name: 'Michael', email: '#string', id: '#number' }

  # ─────────────────────────────────────────────────────────────
  # Exercise 1.2 — POST Register using Michael's email
  # ─────────────────────────────────────────────────────────────
  Scenario: Exercise 1.2 — Register user with Michael's email and generated password

    # Step 1: Re-fetch Michael (chaining)
    Given path '/api/users'
    And param page = 2
    When method GET
    Then status 200
    * def michael      = karate.jsonPath(response.data, "$[?(@.first_name=='Michael')]")[0]
    * def michaelEmail = michael.email
    * def michaelId    = michael.id

    # Step 2: Generate password via Java utility
    * def passwordUtil = Java.type('com.asms.karate.PasswordGenerator')
    * def generatedPwd = passwordUtil.generate(12)

    # Step 3: Register
    Given path '/api/register'
    And request { email: '#(michaelEmail)', password: '#(generatedPwd)' }
    When method POST
    Then status 200

    And match response contains { id: '#number', token: '#string' }

    # Exercise requirement: ID from register == ID from user list
    And assert response.id == michaelId
    * def registrationToken = response.token

    # Write email and password to JSON file
    * def credentials = { email: '#(michaelEmail)', password: '#(generatedPwd)' }
    * karate.write(credentials, 'credentials.json')
    * karate.log('Credentials saved to credentials.json')

  # ─────────────────────────────────────────────────────────────
  # Exercise 1.3 — GET Single User using ID from Exercise 1.2
  # ─────────────────────────────────────────────────────────────
  Scenario: Exercise 1.3 — Get single user by ID from registration response

    # Re-run chain to get ID (in real suite, use shared state / session)
    Given path '/api/users'
    And param page = 2
    When method GET
    Then status 200
    * def michael  = karate.jsonPath(response.data, "$[?(@.first_name=='Michael')]")[0]
    * def userId   = michael.id

    # Fetch single user
    Given path '/api/users/' + userId
    When method GET
    Then status 200

    # Schema validation
    And match response ==
    """
    {
      data: {
        id:         '#number',
        email:      '#string',
        first_name: '#string',
        last_name:  '#string',
        avatar:     '#string'
      },
      support: '#object'
    }
    """

    And match response.data contains
    """
    {
      id:         '#(userId)',
      email:      '#string',
      first_name: '#string',
      last_name:  '#string'
    }
    """

  # ─────────────────────────────────────────────────────────────
  # Exercise 1.4 — POST Login, verify token matches Exercise 1.2 token
  # ─────────────────────────────────────────────────────────────
  Scenario: Exercise 1.4 — Login with Michael's credentials, token must match registration

    # Read credentials from file written in Exercise 1.2
    * def credentials = read('credentials.json')
    * def emailFromFile    = credentials.email
    * def passwordFromFile = credentials.password

    # Re-register to get reference token
    Given path '/api/register'
    And request { email: '#(emailFromFile)', password: '#(passwordFromFile)' }
    When method POST
    Then status 200
    * def registrationToken = response.token

    # Login
    Given path '/api/login'
    And request { email: '#(emailFromFile)', password: '#(passwordFromFile)' }
    When method POST
    Then status 200

    And match response contains { token: '#string' }

    # CRITICAL: token from login must equal token from registration
    And assert response.token == registrationToken
    * karate.log('Token match verified:', response.token)
```

### Java Password Generator Utility

```java
// karate-tests/src/test/java/com/asms/karate/PasswordGenerator.java
public class PasswordGenerator {
    private static final String CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$";
    private static final SecureRandom random = new SecureRandom();

    public static String generate(int length) {
        return IntStream.range(0, length)
            .mapToObj(i -> String.valueOf(CHARS.charAt(random.nextInt(CHARS.length()))))
            .collect(Collectors.joining());
    }
}
```

### Karate Runner

```java
// karate-tests/src/test/java/com/asms/karate/ExerciseRunner.java
@Karate.Test
Karate testExercises() {
    return Karate.run("ApiChainTest").relativeTo(getClass());
}
```

---

## 5. ASMS API Test Suite (Karate)

```gherkin
# karate-tests/src/test/resources/asms/tickets/TicketApiTest.feature
Feature: Support Ticket API Tests

  Background:
    * url 'http://localhost:8084'
    * def token = call read('../auth/get-token.feature') { role: 'OWNER' }
    * header Authorization = 'Bearer ' + token

  Scenario: Create ticket — happy path
    Given path '/api/v1/tickets'
    And request
    """
    {
      "title":       "Water leakage",
      "description": "Pipe burst under sink",
      "category":    "PLUMBING",
      "priority":    "P2_HIGH"
    }
    """
    When method POST
    Then status 201
    And match response contains { id: '#string', status: 'OPEN', raisedBy: '#string' }
    * def ticketId = response.id

  Scenario: Get ticket by ID
    Given path '/api/v1/tickets/' + ticketId
    When method GET
    Then status 200
    And match response.id == ticketId

  Scenario: Create ticket — missing title returns 422
    Given path '/api/v1/tickets'
    And request { "title": "", "description": "D", "category": "GENERAL", "priority": "P4_LOW" }
    When method POST
    Then status 422
    And match response contains { status: 422, detail: '#string' }
    And match response.detail contains 'Title is required'
```

---

## 6. SonarQube Quality Gate

```yaml
# sonar-project.properties
sonar.projectKey=asms-user-service
sonar.projectName=ASMS User Service
sonar.java.source=21
sonar.sources=src/main/java
sonar.tests=src/test/java
sonar.java.coveragePlugin=jacoco
sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml

# Quality Gate thresholds
sonar.qualitygate.wait=true
# Gate requires:
#   Coverage > 70%
#   Duplications < 3%
#   Zero Critical bugs
#   Zero Critical security hotspots
```

```xml
<!-- pom.xml — JaCoCo + Surefire -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.70</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## 7. Test Categories Summary

| Category | Tool | Count Target | Coverage Focus |
|---|---|---|---|
| Unit | JUnit 5 + Mockito | ~200 tests | Service layer, domain logic, state machines |
| Integration | Testcontainers | ~50 tests | Controller → DB round-trips, Kafka produce/consume |
| API/E2E | Karate | ~30 scenarios | REST contract, chained flows, error handling |
| Load | Gatling | 3 scenarios | 500 TPS, 1000 concurrent, spike test |
| Security | OWASP ZAP | Automated scan | OWASP Top 10 on deployed API |
| Contract | Spring Cloud Contract | Per service pair | API backward compatibility |
