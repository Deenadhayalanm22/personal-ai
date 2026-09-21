Feature: Monthly Commitment mutual-fund SIP progress
  The Monthly Commitment story distinguishes due, upcoming, and confirmed SIPs while retaining the investment runway.

  Scenario: Confirm staggered mutual-fund SIPs and retain the June runway
    Given the backend clock is 15 Apr 2026
    And the user has no mutual funds
    When the user adds these mutual funds with an opening holding:
      | fund       | monthly SIP | SIP date | SIP start month |
      | Small cap  | ₹10,000     | 1        | Apr 2026        |
      | Medium cap | ₹20,000     | 5        | Apr 2026        |
      | Large cap  | ₹30,000     | 10       | Apr 2026        |
    Then each new SIP is scheduled from May because it was added during April
    And the May runway includes the three SIPs for ₹60,000 in total

    When the backend clock moves to 1 May 2026
    And the user opens Monthly Commitment
    Then the Small cap SIP is marked DUE and highlighted red because it is due today
    And the Medium cap and Large cap SIPs are UPCOMING and are not highlighted as due
    When the user selects Review for the due Small cap SIP
    Then the user is taken to, scrolled to, and focused on the owning mutual-fund card
    When the user confirms its May allocation with an editable amount, date, and NAV
    Then the May Small cap SIP is persisted as CONFIRMED
    And returning to View included commitments removes the red due highlight and keeps the row neutral

    When the backend clock moves to 12 May 2026
    And the user opens Monthly Commitment
    Then the Medium cap and Large cap SIPs are due
    And the confirmed Small cap SIP is not offered for confirmation again
    When the user confirms both due SIP allocations with valid amounts, dates, and NAVs
    Then all three May SIP allocations are confirmed

    When the backend clock moves to 21 May 2026
    And the user adds a valid lump-sum investment to one mutual fund
    And the user opens Fund details
    Then the history lists the opening holding, SIP, and lump sum with an edit wrench on every row
    When the user corrects a historical investment amount
    Then that fund's holding recalculates without changing the ₹60,000 monthly SIP commitment
    And the June runway shows all three SIPs as UPCOMING for ₹60,000 in total

  Scenario: Add a lump-sum-only fund and set up its first SIP later
    Given the backend clock is 15 Apr 2026
    When the user adds a verified mutual fund and answers No to having an actual SIP
    Then the fund accepts occasional lump sums but is absent from Monthly Commitment

    When the backend clock moves forward five days
    And the user selects the fund's SIP bell and schedules ₹5,000 for 5 May
    Then the new SIP is included in the May Monthly Commitment runway
    When the backend clock moves to 5 May 2026
    Then Monthly Commitment marks the SIP due as a reminder
    And the user can confirm the investment from the owning fund's due action
