Feature: Monthly Commitment ETF monthly-plan progress
  The Monthly Commitment story distinguishes a due ETF plan and takes the user to its owning stock before confirmation.

  Scenario: Confirm a due ETF monthly plan and view its history
    Given the backend clock is 15 Apr 2026
    And the user adds an ITC ETF holding
    Then the ITC card shows a bell for setting its monthly plan
    When the user selects the bell and saves a ₹5,000 monthly plan for 1 May 2026
    Then the May Monthly Commitment runway includes ₹5,000 as Planned investing

    When the backend clock moves to 1 May 2026
    And the user opens Monthly Commitment and View included commitments
    Then the ITC ETF plan is highlighted red because it is due today
    When the user selects Review for the due ETF plan
    Then the user is taken to, scrolled to, and focused on the owning ITC stock card
    And the due monthly investment control is visible
    When the user enters the actual ₹5,000 amount and execution price and confirms the investment
    Then the May ETF plan is persisted as CONFIRMED
    And the Monthly Commitment reports ₹5,000 of planned investing as complete without changing its intended total

    When the user selects View details on the ITC card
    Then a stock-details window shows the opening holding and confirmed monthly ETF purchase in Investment history
