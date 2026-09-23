Feature: Monthly Commitment mutual-fund SIP progress
  A verified scheme requires an existing holding with positive units and invested amount. Mutual-fund cards stay compact. View details owns plan changes, due payment decisions, and payment history.

  Scenario: Review, pay, skip, edit, and delete named mutual-fund SIPs
    Given the backend clock is 15 Apr 2026
    And the user starts on the home page with no mutual funds
    Then adding a fund without positive opening units and invested amount is rejected
    When the user mistakenly adds Parag Parikh Flexi Cap Fund - Direct Growth with a ₹10,000 monthly SIP due on the 5th
    And the user opens its View details
    Then Edit fund and Delete fund appear in the top-right of View details
    And the compact Parag Parikh card has no Edit or Delete control
    And the scheme name is read-only because the scheme determines its NAV source
    When the user selects Delete fund and confirms deletion
    Then Parag Parikh is absent from Mutual funds immediately and after returning to the home page
    And it has no SIP or investment history remaining

    When the user adds these verified funds on 15 Apr 2026:
      | scheme                                            | SIP amount | SIP day | frequency | opening units | opening invested | opening NAV |
      | Parag Parikh Flexi Cap Fund - Direct Growth       | ₹10,000    | 1       | monthly   | 10            | ₹1,000           | ₹100        |
      | HDFC Mid-Cap Opportunities Fund - Direct Growth  | none       | none    | none      | 10            | ₹1,000           | ₹100        |
      | Nippon India Small Cap Fund - Direct Growth      | ₹30,000    | 10      | monthly   | 10            | ₹1,000           | ₹100        |
    Then Parag Parikh and Nippon India first have scheduled SIPs in May, not April
    And HDFC Mid-Cap has a positive existing holding but no active SIP and contributes ₹0 to the April and May commitments
    When the backend clock moves to 20 Apr 2026
    And the user opens HDFC Mid-Cap View details
    Then its Set up SIP action appears in View details and not on its compact card
    When the user sets a ₹20,000 monthly SIP for the 5th starting in May 2026
    Then the May Monthly Commitment includes:
      | fund          | planned amount | due date    |
      | Parag Parikh  | ₹10,000        | 1 May 2026 |
      | HDFC Mid-Cap  | ₹20,000        | 5 May 2026 |
      | Nippon India  | ₹30,000        | 10 May 2026 |
    And Planned investing totals ₹60,000 for May
    And none of the compact fund cards has Edit, Delete, Pay SIP, or Skip SIP controls
    When the user opens Parag Parikh View details on 20 Apr 2026
    Then only its next payable SIP, ₹10,000 due 1 May 2026, is shown
    And Pay SIP and Skip SIP are visible but disabled before 1 May 2026
    And Edit fund allows only SIP frequency and current NAV changes, not a scheme-name change

    When the backend clock moves to 1 May 2026
    And the user opens the Monthly Commitment story from the home page
    Then Parag Parikh's ₹10,000 SIP is due today and highlighted red
    And HDFC Mid-Cap's ₹20,000 SIP and Nippon India's ₹30,000 SIP are upcoming without a due highlight
    And Review appears in View included commitments, not on the story face
    When the user selects Review for Parag Parikh
    Then the user is taken directly to, scrolled to, and focused on the Parag Parikh card
    And Due now appears beside View details in a compact red due strip; the fund card itself has no red border or focus outline
    When the user opens View details
    Then only the ₹10,000 May SIP due 1 May 2026 is shown as Due now
    And Pay SIP and Skip SIP are enabled
    When the user selects Pay SIP
    Then the payment form asks for actual amount, payment date, and units received, and calculates NAV
    When the user enters ₹11,000, 1 May 2026, and 110 units and confirms
    Then the May Parag Parikh SIP is PAID on 1 May 2026 for ₹11,000 at NAV ₹100 and adds 110 units
    And the red Due now state clears in View details and the May payment appears in history immediately
    And its ₹10,000 planned May commitment amount does not change
    And its May SIP cannot be paid or skipped a second time
    And its included-commitment row loses the red highlight while keeping Review

    When the backend clock moves to 5 May 2026
    And the user opens View included commitments and selects Review for HDFC Mid-Cap
    Then the owning card is focused and its ₹20,000 SIP shows Due now
    When the user opens View details and selects Skip SIP
    Then the confirmation asks whether to skip the 5 May 2026 SIP without a penalty field
    When the user confirms the skip on 5 May 2026
    Then the May HDFC Mid-Cap SIP is SKIPPED with no paid amount, NAV, units, or penalty
    And the 5 Jun 2026 HDFC Mid-Cap SIP remains scheduled for ₹20,000
    And the May HDFC Mid-Cap included-commitment row returns to neutral styling

    When the backend clock moves to 12 May 2026
    Then the May HDFC Mid-Cap SIP remains SKIPPED and cannot be paid again
    And the Nippon India SIP due 10 May 2026 is Due now for ₹30,000
    When the user opens Nippon India View details and pays ₹30,000 on 12 May 2026 for 200 units
    Then its May SIP is PAID for ₹30,000, NAV ₹150, and 200 units on 12 May 2026
    And the May Parag Parikh and Nippon India payments remain separate history entries

    When the backend clock moves to 21 May 2026
    And the user adds a ₹5,000 Parag Parikh lump sum dated 21 May 2026 for 50 units
    Then the compact card has no Lump sum control
    And the Lump sum button is aligned to the right of Total P&L in View details
    And the lump sum adds 50 units without changing the ₹10,000 Parag Parikh SIP plan
    When the user opens Parag Parikh View details
    Then its payment history is shown directly without a separate history button and includes:
      | kind            | date        | amount  | NAV  | units | status |
      | Opening holding | 15 Apr 2026 | ₹1,000  | ₹100 | 10    | PAID   |
      | May SIP         | 1 May 2026  | ₹11,000 | ₹100 | 110   | PAID   |
      | Lump sum        | 21 May 2026 | ₹5,000  | ₹100 | 50    | PAID   |
    And Parag Parikh has 170 invested units and ₹17,000 invested in total
    When the user opens HDFC Mid-Cap View details and reads its payment history
    Then its May SIP history shows ₹20,000 scheduled for 5 May 2026 and SKIPPED on 5 May 2026
    And that skipped SIP shows no penalty, invested amount, NAV, or units
    And before any frequency edit, the June runway still includes ₹10,000, ₹20,000, and ₹30,000 SIPs for ₹60,000 in total

    When the user selects Edit fund in Parag Parikh View details
    Then the scheme name cannot be edited; changing schemes requires Delete fund and a new fund
    And only SIP frequency and current NAV can be edited
    When the user changes the Parag Parikh frequency from monthly to quarterly effective June 2026
    And the user adjusts its current NAV from ₹100 to ₹125
    Then the May PAID SIP and 21 May lump sum remain unchanged in payment history
    And the next Parag Parikh SIP is ₹10,000 due 1 Aug 2026, with no June or July Parag Parikh reminder
    And the June planned SIP total becomes ₹50,000 from HDFC Mid-Cap and Nippon India
    And 170 units at current NAV ₹125 give Parag Parikh a current value of ₹21,250
    And the scheme name remains Parag Parikh Flexi Cap Fund - Direct Growth
