Feature: Monthly Commitment mutual-fund SIP progress
  The Monthly Commitment story distinguishes due and upcoming SIPs and retains the investment runway.

  Scenario: Confirm staggered mutual-fund SIPs and retain the June runway
    Given the backend clock is 15 Apr 2026
    And the user has no mutual funds
    When the user adds these mutual funds with an opening holding:
      | fund       | monthly SIP | SIP date | SIP start month |
      | Small cap  | ₹10,000     | 1        | Apr 2026        |
      | Medium cap | ₹20,000     | 5        | Apr 2026        |
      | Large cap  | ₹30,000     | 10       | Apr 2026        |
    Then the April Monthly Commitment includes ₹60,000 of planned investing
    And the May runway includes the three SIPs for ₹60,000 in total

    When the backend clock moves to 1 May 2026
    And the user opens Monthly Commitment
    Then the Small cap SIP is marked DUE and highlighted red because it is due today
    And the Medium cap and Large cap SIPs are UPCOMING and are not highlighted as due
    When the user opens the due Small cap SIP and confirms its May allocation with a valid amount, date, and NAV
    Then the May Small cap SIP is persisted as CONFIRMED

    When the backend clock moves to 12 May 2026
    And the user opens Monthly Commitment
    Then the Medium cap and Large cap SIPs are due
    And the confirmed Small cap SIP is not offered for confirmation again
    When the user confirms both due SIP allocations with valid amounts, dates, and NAVs
    Then all three May SIP allocations are confirmed

    When the backend clock moves to 21 May 2026
    And the user adds a valid lump-sum investment to one mutual fund
    Then the lump sum changes that fund's holding but not the ₹60,000 monthly SIP commitment
    And the June runway shows all three SIPs as UPCOMING for ₹60,000 in total
