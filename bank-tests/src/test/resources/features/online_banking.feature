@ui
Feature: Online banking
  Customers use the web page to open accounts and move money.

  @smoke
  Scenario: Open an account and pay a friend online
    Given Bob has a savings account with a balance of $0.00
    And Alice is on the online banking page
    When Alice opens a chequing account online with $500.00
    And Alice sends $125.00 to Bob online with memo "Groceries"
    Then the page should confirm "completed"
    And the page should list Alice's balance as "$375.00"
    And the page should list Bob's balance as "$125.00"

  Scenario: Overdraft attempt is refused online
    Given Alice has a chequing account with a balance of $20.00
    And Bob has a savings account with a balance of $0.00
    And Alice is on the online banking page
    When Alice sends $25.00 to Bob online with memo "Too much"
    Then the page should show the error "INSUFFICIENT_FUNDS"
    And the page should list Alice's balance as "$20.00"
