@api
Feature: Money transfers
  As a bank customer
  I want to move money between accounts
  So that I can pay people, and the bank must never lose or create money doing it

  Background:
    Given the following customers:
      | customer | type     | balance  |
      | Alice    | chequing | 1000.00  |
      | Bob      | savings  | 50.00    |

  @smoke @db
  Scenario: Successful transfer is double-entry booked
    When Alice transfers $200.00 to Bob with memo "Concert tickets"
    Then the transfer should be completed
    And Alice's balance should be $800.00
    And Bob's balance should be $250.00
    And the database should hold a COMPLETED transfer of $200.00
    And the ledger should show a DEBIT for Alice and a CREDIT for Bob
    And every account should reconcile with the ledger

  @db
  Scenario Outline: Business rules reject a transfer without moving money
    When <payer> transfers $<amount> to <payee>
    Then the request should fail with status 422 and code <code>
    And the database should hold a REJECTED transfer of $<amount>
    And the database should record the failure reason <code>
    And the ledger should contain 0 entries for the transfer
    And Alice's balance in the database should be $1000.00
    And Bob's balance in the database should be $50.00

    Examples:
      | payer | payee | amount   | code               |
      | Bob   | Alice | 50.01    | INSUFFICIENT_FUNDS |
      | Alice | Bob   | 10000.01 | LIMIT_EXCEEDED     |
      | Alice | Alice | 1.00     | SAME_ACCOUNT       |

  Scenario: Transfers involving a frozen account are rejected
    Given Bob's account is frozen
    When Alice transfers $10.00 to Bob
    Then the request should fail with status 422 and code ACCOUNT_FROZEN
    And Alice's balance should be $1000.00

  @smoke
  Scenario: Retrying a transfer with the same idempotency key charges once
    When Alice sends $300.00 to Bob with a new idempotency key
    And the same request is retried 3 times
    Then Alice's balance should be $700.00
    And Bob's balance should be $350.00
