Feature: ECA Karate Exercises 1.1 – 1.4 (using reqres.in)

  Background:
    * url 'https://reqres.in'
    * configure ssl = true

  # Exercise 1.1 — GET /api/users?page=2, schema validation, find Michael
  Scenario: Ex 1.1 - List Users page 2 and find Michael
    Given path '/api/users'
    And param page = 2
    When method GET
    Then status 200

    # Schema validation
    And match response == { page: '#number', per_page: '#number', total: '#number', total_pages: '#number', data: '#array', support: '#object' }
    And match each response.data == { id: '#number', email: '#string', first_name: '#string', last_name: '#string', avatar: '#string' }
    And def total = response.total
    * print 'Total users:', total

    # Find Michael
    And def michael = karate.filter(response.data, function(u){ return u.first_name == 'Michael' })
    And match michael[0].first_name == 'Michael'
    * def michaelEmail = michael[0].email
    * def michaelId = michael[0].id
    * print 'Found Michael → email:', michaelEmail, 'id:', michaelId

  # Exercise 1.2 — POST /api/register with Michael's email + generated password
  Scenario: Ex 1.2 - Register Michael and verify ID
    # GET page 2 to find Michael
    Given path '/api/users'
    And param page = 2
    When method GET
    Then status 200
    And def michael = karate.filter(response.data, function(u){ return u.first_name == 'Michael' })
    And def michaelEmail = michael[0].email
    And def michaelId = michael[0].id

    # Generate password (using Java PasswordGenerator)
    * def PasswordGenerator = Java.type('com.asms.user.karate.PasswordGenerator')
    * def generatedPassword = PasswordGenerator.generate(12)
    * print 'Generated password:', generatedPassword

    # Register
    Given path '/api/register'
    And request { email: '#(michaelEmail)', password: '#(generatedPassword)' }
    When method POST
    Then status 200
    And match response == { id: '#number', token: '#string' }
    And def registrationToken = response.token
    * print 'Registration token:', registrationToken

    # Verify ID matches page-2 Michael's id
    And match response.id == michaelId
    * karate.write({ email: michaelEmail, password: generatedPassword, token: registrationToken, id: michaelId }, 'credentials.json')

  # Exercise 1.3 — GET /api/users/{id} with schema validation
  Scenario: Ex 1.3 - Get user by ID and validate schema
    * def userId = 7
    Given path '/api/users/' + userId
    When method GET
    Then status 200
    And match response == { data: { id: '#number', email: '#string', first_name: '#string', last_name: '#string', avatar: '#string' }, support: '#object' }
    * print 'User:', response.data.first_name, response.data.last_name, '—', response.data.email

  # Exercise 1.4 — Read credentials.json, re-register for reference token, login, assert tokens match
  Scenario: Ex 1.4 - Login and verify token matches registration token
    * def credentials = read('classpath:karate/credentials.json')
    * print 'Loaded credentials for:', credentials.email

    # Re-register to get reference token
    Given path '/api/register'
    And request { email: '#(credentials.email)', password: '#(credentials.password)' }
    When method POST
    Then status 200
    And def referenceToken = response.token
    * print 'Reference token (re-register):', referenceToken

    # Login
    Given path '/api/login'
    And request { email: '#(credentials.email)', password: '#(credentials.password)' }
    When method POST
    Then status 200
    And match response == { token: '#string' }
    And def loginToken = response.token
    * print 'Login token:', loginToken

    # Assert login token == registration token
    And match loginToken == referenceToken
    * print 'SUCCESS: login token matches registration token'
