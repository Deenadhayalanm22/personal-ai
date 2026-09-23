Feature: Monthly Commitment loan EMI progress
  The Loans card gives a compact progress summary, while View details presents only the next payable EMI.

  Scenario: Review, pay, and later skip a scheduled loan EMI
    Given the backend clock is 15 Apr 2026
    And the user has no loans
    When the user creates a Home loan with:
      | field              | value       |
      | principal          | ₹600,000   |
      | EMI                | ₹100,000   |
      | first EMI due date | 1 Jan 2026 |
      | tenure             | 6 months   |
    Then the Loans section shows 4 of 6 EMI months completed as compact progress
    And View details does not show historical EMI rows
    And View details shows only the next payable EMI, due 1 May 2026
    And its Pay EMI and Skip EMI controls are visible but disabled before 1 May 2026
    And the compact loan card has no Edit or Delete controls
    When the user opens View details for the loan
    Then Edit loan and Delete loan controls appear in the top-right of View details
    When the user selects Edit loan and changes the lender name to Updated Bank
    Then View details and the compact loan card show Updated Bank
    And the April Monthly Commitment includes ₹100,000 for the loan and is marked Paid with nothing due
    And the May runway includes ₹100,000 for the loan

    When the user creates a mistaken Personal loan with a 1 Dec 2026 first EMI due date
    And the user opens View details for the mistaken loan
    Then Edit loan and Delete loan controls appear in the top-right of View details
    When the user selects Delete loan and confirms the deletion
    Then the mistaken loan is absent from active Loans
    And the mistakenly deleted loan is not listed under Closed loans

    When the backend clock moves to 1 May 2026
    And the user opens the Monthly Commitment story
    Then the May loan reminder is shown in red
    And Review loan is not shown on the story face
    When the user selects View included commitments
    Then the May loan is shown in red in the included commitments list
    When the user selects Review loan
    Then the user is taken directly to, scrolled to, and focused on the owning loan row
    And the May EMI is labelled Due now inside the red View details area
    And Due now is on the left and View details is on the right of the same compact row
    And the loan progress remains 4 of 6 EMI months completed until May is paid
    When the user selects View details
    Then View details shows only the May EMI
    And the Pay May EMI and Skip May EMI controls are enabled
    When the user selects Pay May EMI
    Then the backend persists a PAID May occurrence with paid amount ₹100,000 and paid date 1 May 2026
    And the loan progress becomes 5 of 6 EMI months completed
    And the Monthly Commitment marks the May loan payment as Paid with nothing due
    And the May loan in View included commitments returns to its normal presentation

    When the backend clock moves to 1 Jun 2026
    And the user creates a second Personal loan with:
      | field              | value       |
      | principal          | ₹30,000    |
      | EMI                | ₹5,000     |
      | first EMI due date | 1 Jun 2026 |
      | tenure             | 6 months   |
    And the user opens View details for the loan
    Then View details shows only the June EMI
    And the Pay June EMI and Skip June EMI controls are enabled
    And selecting Skip June EMI opens a confirmation with an optional Bank penalty amount text box
    When the user confirms that June EMI is skipped without a penalty amount
    Then the backend persists a SKIPPED June occurrence
    And the loan schedule extends from 6 to 7 EMI months
    And June is not included in the current Monthly Commitment story
    And the next payable EMI is due 1 Jul 2026
    When the user opens View details for the second loan
    Then View details shows only its June EMI
    When the user selects Pay June EMI for the second loan
    Then the backend persists a PAID June occurrence for the second loan with paid amount ₹5,000 and paid date 1 Jun 2026

    When the backend clock moves to 1 Jul 2026
    And the user opens View details for the loan
    Then View details shows only the rescheduled final July EMI
    And the Pay July EMI and Skip July EMI controls are enabled
    When the user selects Pay July EMI
    Then the backend persists a PAID July occurrence with paid amount ₹100,000 and paid date 1 Jul 2026
    And the loan becomes CLOSED with no remaining EMI payments
    And July is not included in the Monthly Commitment story
    And the Loans section has no active loans
    And the closed Home loan appears at the bottom of the Loans section under Closed loans
    When the user expands Closed loans and selects See full payment history for the closed Home loan
    Then the payment history lists every scheduled EMI, including the PAID May and July EMIs and the SKIPPED June EMI
    When the user opens View details for the second loan
    Then View details shows only its July EMI
    When the user selects Pay July EMI for the second loan
    Then the backend persists a PAID July occurrence for the second loan with paid amount ₹5,000 and paid date 1 Jul 2026

    When the backend clock moves to 1 Aug 2026
    And the user opens View details for the second loan
    Then the Pre-close loan option is enabled
    When the user selects Pre-close loan
    Then a confirmation asks for the pre-closure settlement amount
    When the user enters ₹20,000 and confirms pre-closure
    Then the second loan becomes CLOSED immediately
    And the second loan has no remaining EMI payments or future Monthly Commitment entries
    And the closed second loan appears under Closed loans
    When the user selects See full payment history for the closed second loan
    Then the payment history lists only the PAID June and July EMIs and the ₹20,000 pre-closure settlement
    And it does not list the four unpaid planned instalments as future EMI rows

    When the user creates a third Education loan with a ₹10,000 EMI, six-month tenure, and 1 Aug 2026 first EMI due date
    And the user opens View details for the third loan before marking any payment
    Then all loan fields, including principal, EMI, tenure, and first EMI date, are editable
    When the user changes the third loan's principal and EMI details and saves
    Then the updated schedule is shown for the third loan
    When the user selects Pay August EMI for the third loan
    Then the backend persists a PAID August occurrence for the third loan

    When the backend clock moves to 1 Sep 2026
    And the user opens View details for the third loan
    And the user selects Edit loan
    Then the original principal, EMI, tenure, and first EMI date fields are disabled
    And the user is told that payment history cannot be changed
    And a Restructure remaining loan action is offered
    When the user selects Restructure remaining loan
    And the user enters a ₹7,500 future EMI and five remaining months effective from September 2026
    And the user confirms the restructure
    Then the third loan remains one active loan card rather than creating a second child-loan card
    And its August PAID EMI remains unchanged in the payment history
    And its future unpaid EMI schedule is rebuilt from September 2026 at ₹7,500 for five months
    And the payment history records a Restructured from September 2026 entry
    And the Monthly Commitment uses the restructured ₹7,500 EMI from September 2026
