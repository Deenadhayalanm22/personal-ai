Feature: Stock holding and monthly purchase progress
  Stock cards stay compact. View details owns plan changes, due decisions, manual purchases, adjustments, history, and deletion.

  Scenario: Review, pay, skip, adjust, and delete listed stocks
    Given the backend clock is 15 Apr 2026
    And the signed-in user opens Your money with no stocks
    When the user adds 10 ITC shares with ₹4,000 invested and 5 Reliance shares with ₹6,000 invested
    Then the latest prices are ₹425.50 and ₹1,500.00 respectively
    And the combined stock value is ₹11,755 with ₹1,755 overall P&L
    And neither compact card has a delete icon, bell, or purchase action
    When the user opens ITC View details
    Then ITC is the title and Edit stock and Delete stock appear below it on the right
    And the monthly-plan bell is inside View details
    And investment history shows the opening holding directly
    When the user sets a ₹5,000 monthly purchase for the 1st starting May 2026
    Then the May Monthly Commitment includes ₹5,000 of planned investing
    And Confirm allocation and Skip purchase are disabled before 1 May
    When the backend clock moves to 1 May 2026
    And the user reviews ITC from View included commitments
    Then the due commitment has the same red border and background as due funds and loans
    And Due now and View details share one red-bordered strip on the stock card
    And View details shows the ₹5,000 purchase as Due now
    And Confirm allocation and Skip purchase are enabled
    When the user confirms ₹5,500 invested at ₹425.50 on 1 May 2026
    Then the occurrence is confirmed once and appears in investment history
    And the planned May commitment remains ₹5,000
    And the red due styling clears
    When the user manually adds ₹1,000 of ITC shares with a date and positive units in View details
    Then the new purchase appears in investment history and recalculates the holding
    When the user selects Edit stock and corrects the opening holding amount, date, and shares
    Then the corrected opening holding appears in history and totals without changing the monthly plan
    And individual investment rows have no Edit investment button
    When the user opens Reliance View details and sets a ₹2,000 monthly purchase for 5 Jun 2026
    Then its Confirm allocation and Skip purchase controls are disabled before the due date
    When the backend clock moves to 5 Jun 2026
    And the user skips the Reliance purchase from View details
    Then its June occurrence is SKIPPED without invested shares
    And the following monthly purchase remains scheduled
    And the June commitment row returns to neutral styling
    When the user deletes ITC from View details
    Then ITC and its investment history disappear immediately and after reloading Your money
