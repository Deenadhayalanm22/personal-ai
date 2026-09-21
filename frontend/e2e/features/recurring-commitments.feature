Feature: Manage commitments
  A user can correct or remove a recurring planning obligation without deleting an expense.

  Scenario: Edit and delete a manually created monthly commitment
    Given the user has created a ₹15,000 Home rent monthly commitment due on the 5th
    When the user edits its amount to ₹16,000 and due day to the 7th
    Then Manage commitments shows the updated amount and due day
  When the user deletes Home rent
  Then it no longer appears in Manage commitments or the refreshed current projection

  Scenario: Review and complete a due commitment
    Given Internet bill is due on the 5th of the current month
    When the date reaches the 5th and the user opens Monthly Commitment
    Then Internet bill is red in the included commitments list
    When the user reviews it and marks the commitment done
    Then the Manage commitments row and story evidence show it as completed

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
