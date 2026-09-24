@planned-commitment-savings-goals
Feature: Attach a savings goal to an infrequent commitment
  A user can plan and record money set aside for an upcoming commitment.
  The goal is optional and never changes the commitment's payment amount.

  Background:
    Given the user is signed in to their own financial profile
    And the date is 1 January 2027

  Scenario Outline: Offer a goal only for a commitment less frequent than monthly
    Given the user has an active <cadence> commitment
    When the user opens View details for that commitment
    Then <button> appears beside the commitment label

    Examples:
      | cadence         | button                       |
      | monthly         | no Attach goal button         |
      | every 4 months  | an Attach goal button         |
      | every 6 months  | an Attach goal button         |
      | yearly          | an Attach goal button         |

  Scenario: Explain the upcoming insurance payment in the attachment popup
    Given the user has a ₹48,000 yearly insurance commitment due on 1 September 2027
    And the verified Monthly Commitment story totals ₹30,000 for January 2027
    When the user selects Attach goal beside the insurance label in View details
    Then the backend sends the user's available financial snapshot to its AI summary service
    And a popup shows the ₹48,000 expected September payment and its due date
    And it shows the verified ₹30,000 January commitment total with its month and source
    And the AI summarizes how the September payment fits alongside the user's known commitments
    And it offers to set aside money before September without changing the insurance commitment

  Scenario Outline: Prefill the minimum plan whether salary is shared or not
    Given the user has a ₹48,000 insurance commitment due on 1 September 2027
    And January through August are eight saving opportunities before that due date
    And no money has been recorded as set aside for this payment
    And the user has <salary context>
    When the user opens the Attach goal popup
    Then the form is prefilled with a ₹48,000 target and 1 September 2027 target date
    And the backend suggestion shows a minimum of ₹6,000 per month from January through August
    And it shows the ₹48,000 target, eight months, and resulting ₹6,000 minimum
    And the minimum is the same with or without salary
    And the user can edit the target and monthly contribution before confirming

    Examples:
      | salary context              |
      | not shared salary           |
      | shared an exact salary      |
      | shared only a salary range  |

  Scenario: AI explains the suggestion using only available snapshot facts
    Given the user has a ₹48,000 insurance commitment due on 1 September 2027
    And the user's snapshot includes their own commitments, recorded spending, income context, and holdings where available
    And it has no verified available cash balance
    When the user opens the Attach goal popup
    Then the backend AI summarizes the known monthly commitments and upcoming September payment
    And it explains why ₹6,000 each month would reach the goal
    And it does not invent a cash balance or claim the contribution is affordable
    And the popup labels any missing information that limits the summary

  Scenario: Do not display an inconsistent AI suggestion
    Given the insurance goal requires ₹48,000 across eight months before September
    And the backend AI proposes ₹5,000 per month while claiming that will reach ₹48,000
    When the backend prepares the attachment popup
    Then it does not show the inconsistent claim to the user
    And it displays the validated ₹6,000 monthly minimum with the calculation basis
    And the user can still edit and confirm a different monthly amount

  Scenario: Lowering the monthly amount shows a shortfall and top-up option
    Given the popup suggests ₹6,000 per month for eight months toward ₹48,000
    When the user edits the monthly amount to ₹4,000
    Then it shows ₹32,000 projected by September and a ₹16,000 shortfall
    And it warns that ₹4,000 each month alone will not reach the target
    And it explains that the user can add ₹16,000 across one or more months before September
    And it lets the user keep the lower plan or edit the contribution before confirming

  Scenario: Increasing the monthly amount shows early completion
    Given the popup suggests ₹6,000 per month for eight months toward ₹48,000
    When the user edits the monthly amount to ₹8,000
    Then it shows that six contributions would reach ₹48,000 before September
    And it does not record any contribution before the user actually sets money aside

  Scenario: Confirm an edited plan without recording a contribution
    Given the popup prefills a ₹48,000 target and ₹6,000 monthly contribution
    When the user changes the monthly contribution to ₹5,000
    And confirms the savings pot
    Then one pot is linked to the insurance commitment with the confirmed values
    And the Goals section shows its target date, planned contribution, and ₹8,000 projected shortfall
    And saved progress remains ₹0 until the user records money actually set aside
    And the insurance commitment remains due for its full planned amount

  Scenario: Cancel the attachment popup
    Given the user opened Attach goal for insurance
    When the user closes the popup without confirming
    Then no savings pot or contribution is created
    And the insurance commitment is unchanged

  Scenario: Prevent two active pots for one upcoming payment
    Given an insurance commitment already has a pot for its September 2027 payment
    When the user opens insurance View details
    Then the label offers View goal instead of Attach goal
    And the user can review or edit the existing pot without creating a duplicate

  Scenario: Record money set aside during a monthly check-in
    Given the Goals section shows a ₹48,000 insurance pot with ₹6,000 planned each month
    And no money has been recorded as set aside
    When the user opens the pot in January
    And records that they set aside ₹6,000 on 25 January 2027 in the same account
    Then the pot shows ₹6,000 saved, 12.5 percent progress, and ₹42,000 remaining
    And its history shows the amount, date, and user-entered same-account location
    And no bank transfer or account balance change is claimed

  Scenario: Record savings kept in a different account
    Given the Goals section shows an insurance pot with ₹6,000 saved
    When the user records another ₹6,000 set aside in a different account
    Then the pot shows ₹12,000 saved and 25 percent progress
    And its history distinguishes the user-entered storage locations
    And the app does not claim either location has a verified balance

  Scenario: Miss a monthly contribution without fabricating progress
    Given the insurance pot has ₹6,000 saved after January
    And the user records no contribution in February
    When the user opens the pot in March
    Then saved progress remains ₹6,000
    And the remaining monthly amount needed by September is recalculated from actual savings
    And February is not marked as saved or paid

  Scenario: Keep the pot separate from the eventual payment
    Given the user has a ₹48,000 insurance commitment due today
    And its pot shows ₹36,000 set aside
    When the user opens insurance View details
    Then the commitment still shows a ₹48,000 payment due
    And the pot shows 75 percent savings progress without saying the bill is partly paid
    When the user records the insurance payment
    Then payment history records the actual amount and date
    And the pot asks how much saved money was used before reducing its balance

  Scenario: Keep another user's pot private
    Given another profile has an insurance pot and contribution history
    When the user opens commitment and Goals views
    Then the other profile's pot, amounts, and storage locations are not visible
    And the user cannot change them
