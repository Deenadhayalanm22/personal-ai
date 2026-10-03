Feature: Credit-card purchases and bill settlement across months
  Purchases count as spending when they happen. Paying their card bill is a
  separate settlement that clears the bill due without new spending.

  Scenario: May purchases generate a June bill and are paid on June 21
    Given the application and browser clocks are May 1, 2026
    And I add a bank account using only its name and type
    And I add a credit card with bill generation day 1 and payment due day 21, starting with June bills
    When I record credit-card purchases of 480, 1200 and 2320 during May
    And I record a 600 bank/debit purchase during May
    Then May spending is 4600, including 4000 on the card
    And no May bill is included before the configured start month
    And the projected June bill is 4000 for May 1 through May 31
    And its due date is June 21 and payment is disabled before June 1
    When I advance both clocks to June 1
    And I record a 900 credit-card purchase on June 1
    Then June spending is 900 and the June bill remains 4000
    And the 900 purchase belongs to the July 21 bill
    When I advance both clocks to June 21
    And I open Credit-card bills in Accounts from the compact Home widget
    And I record a 1000 card-bill payment
    Then the June bill has 3000 remaining
    When I record the remaining 3000 payment
    Then the June bill is settled
    And June spending remains 900 from one expense
    And both dated settlements appear in payment history and Activity
    When I reload the dashboard
    And I open the bill details in Accounts again
    Then the settlements remain persisted
    And May spending remains 4600 from four expenses
    And July still has 900 remaining for the June purchase
