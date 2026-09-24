Feature: Manage commitments
  A user can correct or remove a recurring planning obligation without deleting an expense.

  @planned-commitment-savings
  Scenario: Prepare for next September's insurance, save in October, and skip November without replanning
    Given the user is signed in to their own financial profile
    And the clock is 30 September 2026
    When the user creates a ₹55,000 Yearly insurance commitment repeating every September
    And sets its next expected payment date to 1 September 2027
    And opens its View details section
    Then Save for this commitment is available for the upcoming payment
    When the user selects Save for this commitment
    Then the form is prefilled with the ₹55,000 payment target and 1 September 2027 target date
    And it shows eleven monthly saving opportunities from October 2026 through August 2027
    And it prefills ₹5,000 per month with the calculation "₹55,000 / 11 months"
    And no savings plan or saved money is recorded before confirmation
    When the user confirms the prefilled form
    Then one savings plan is created for that upcoming insurance payment
    And it shows ₹0 set aside and ₹55,000 remaining
    And the insurance payment remains ₹55,000 due on 1 September 2027
    When the clock advances to 1 October 2026
    And the user opens the October Monthly Commitment story
    Then the ₹5,000 insurance savings contribution appears with a red due border and a Review action
    And it is labelled as savings for insurance rather than an insurance payment due
    When the user selects Review for that savings contribution
    Then the user is taken to the Yearly insurance commitment card
    And its Due now and View details area has the red due treatment for the savings contribution
    When the user opens View details
    Then it explains "You created a savings plan for this payment. Set aside ₹5,000 this month."
    And the October savings contribution offers Pay and Skip
    When the user selects Pay and confirms ₹5,000 actually set aside
    Then October's savings contribution is recorded as paid
    And the plan shows ₹5,000 set aside and ₹50,000 remaining
    And the October savings due treatment clears from the card and story
    And no insurance payment, expense, or bank transfer is recorded by the savings action
    When the clock advances to 1 November 2026
    And the user opens the November Monthly Commitment story
    Then the ₹5,000 insurance savings contribution appears with a red due border and a Review action
    When the user selects Review and opens the commitment's View details
    And selects Skip for the November savings contribution
    Then November's savings contribution is recorded as skipped
    And the November savings due treatment clears from the card and story
    And the plan still shows ₹5,000 set aside and ₹50,000 remaining
    And the monthly savings amount remains ₹5,000 through August 2027
    And it explains "If you save ₹5,000 in each remaining month, you will be ₹5,000 short for September."
    And it does not redistribute the skipped amount or extend the target date
    And the insurance commitment remains active and its payment is not skipped or completed

  @planned-commitment-savings
  Scenario: Offer saving only for an upcoming infrequent commitment without an existing plan
    Given the clock is 30 September 2026
    When the user opens View details for each commitment
    Then Save for this commitment is available for an active yearly insurance payment expected on 1 September 2027
    And it is not available for a monthly commitment
    And it is not available for a payment due today or overdue
    And it is not available for a commitment without a future expected payment date
    And a commitment with an existing savings plan shows that plan instead of another creation action

  @planned-commitment-savings
  Scenario: Dismiss the savings setup without creating a plan
    Given the user has opened Save for this commitment for an upcoming yearly insurance payment
    When the user closes the prefilled form without confirming
    Then no savings plan or contribution is created
    And the insurance commitment is unchanged

  Scenario: Edit and delete a manually created monthly commitment
    Given the user has created a ₹15,000 Home rent monthly commitment due on the 5th
    Then its card offers only View details
    When the user opens View details and edits its amount to ₹16,000 and due day to the 7th
    Then Manage commitments shows the updated amount and due day
    When the user opens View details and deletes Home rent
    Then it no longer appears in Manage commitments or the refreshed current projection

  Scenario: Review and complete a due commitment
    Given Internet bill is due on the 5th of the current month
    When the date reaches the 5th and the user opens Monthly Commitment
    Then Internet bill is red in the included commitments list
    When the user reviews its details and marks the commitment paid
    Then the Manage commitments row and story evidence show it as completed

  Scenario: Skip one month and retain the recurring rule
    Given Family support is an active ₹10,000 monthly commitment
    When the user opens View details and skips the current month
    Then the current occurrence and history show Skipped
    And Family support remains active at ₹10,000

  Scenario: Add a one-time extra payment
    Given Family support is an active ₹10,000 monthly commitment
    When the user opens View details and records this month paid
    And opens Add extra above Current occurrence
    And adds ₹2,000 extra with the reason "Birthday support" in the popup
    Then history shows the paid amount, ₹2,000 extra, and "Birthday support" separately
    And the usual planning amount remains ₹10,000

  Scenario: Due presentation clears after an outcome
    Given Family support is due in the current month
    Then its Money card contains a compact red border around only Due now and View details on the right
    And its Monthly Commitment source is bordered red
    When the user pays or skips the occurrence in details
    Then the red due presentation clears

  Scenario: Do not create a retroactive due reminder
    Given it is 15 April and the user adds commitments due on the 1st, 10th, and 25th
    Then none of those commitments is due immediately
    When the date reaches 25 April
    Then only the 25th commitment is due and can be completed
    When the date reaches 30 April
    Then no commitment is due
    When the date reaches 1 May
    Then only the first-day commitment is due and can be completed

  @planned-flexible-recurrence
  Scenario: Complete an early bike service and choose its next expected date
    Given the user has an active Bike service commitment estimated at ₹2,000 every 4 months
    And its next expected date is 15 January 2027
    When the user completes Bike service early on 21 September 2026 for ₹2,400
    And chooses 21 January 2027 as its next expected date
    Then its history records the ₹2,400 actual payment on 21 September 2026
    And the commitment remains active with ₹2,000 as its planning estimate
    And it shows 21 January 2027 as the next expected date

  @planned-flexible-recurrence
  Scenario: Adjust an internet recharge after an offer
    Given the user has an active Internet recharge commitment estimated at ₹799 usually every 3 months
    And its next expected date is 15 December 2026
    When the user completes the recharge early on 21 September 2026 for ₹699
    And chooses to expect the next recharge in 2 months
    Then its history records the ₹699 actual payment on 21 September 2026
    And it shows 21 November 2026 as the next expected date
    And the usual 3-month recurrence remains unchanged for future suggestions
