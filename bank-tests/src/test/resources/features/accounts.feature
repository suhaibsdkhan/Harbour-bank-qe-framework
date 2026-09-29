@api
Feature: Account management
  As a bank customer
  I want to open accounts and deposit money
  So that I can hold funds with the bank

  @smoke
  Scenario: Open a chequing account with an initial deposit
    When Alice opens a chequing account with an initial deposit of $250.00
    Then Alice's balance should be $250.00
    And Alice's latest transaction should be a CREDIT of $250.00

  Scenario: Deposit into an existing account
    Given Bob has a savings account with a balance of $100.00
    When Bob deposits $0.01
    Then Bob's balance should be $100.01
    And Bob's latest transaction should be a CREDIT of $0.01

  Scenario Outline: Invalid deposits are rejected
    Given Carol has a chequing account with a balance of $10.00
    When Carol deposits $<amount>
    Then the request should fail with status 400 and code VALIDATION_FAILED
    And Carol's balance should be $10.00

    Examples:
      | amount |
      | 0.00   |
      | -1.00  |
      | 1.999  |

  Scenario: A frozen account cannot receive deposits
    Given Dave has a chequing account with a balance of $10.00
    And Dave's account is frozen
    When Dave deposits $5.00
    Then the request should fail with status 422 and code ACCOUNT_FROZEN
    And Dave's account should be FROZEN
