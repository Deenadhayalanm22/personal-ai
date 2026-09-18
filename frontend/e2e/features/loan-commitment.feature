Feature: Monthly Commitment loan EMI progress
  The Monthly Commitment story explains a loan EMI without hiding its payment state.

  Scenario: Mark a due loan EMI paid and refresh Monthly Commitment progress
    Given the backend clock is 15 Apr 2026
    And the user has no loans
    When the user creates a Home loan with:
      | field              | value       |
      | principal          | ₹600,000   |
      | EMI                | ₹100,000   |
      | first EMI due date | 1 Jan 2026 |
      | tenure             | 6 months   |
    Then the Loans section shows 4 of 6 historical EMI months completed
    And it shows only the April paid EMI and the next May upcoming EMI
    And the April Monthly Commitment includes ₹100,000 for the loan
    And the May runway includes ₹100,000 for the loan
    And April is marked Paid with nothing due

    When the backend clock moves to 5 May 2026
    And the user reloads Monthly Commitment
    Then the May loan occurrence is marked DUE
    And the Monthly Commitment shows loan payment progress of ₹0 paid of ₹100,000, ₹100,000 left, and 0%
    When the user selects View included commitments and Review
    Then the user is taken to, scrolled to, and focused on the owning loan row
    And the user sees Mark May EMI paid
    When the user marks May EMI paid
    Then the real backend persists a PAID May occurrence with paid amount ₹100,000 and paid date 5 May 2026
    And the May story shows ₹100,000 paid of ₹100,000, ₹0 left, and 100%

    When the backend clock moves to 1 Jun 2026
    And Monthly Commitment reloads
    Then the June EMI becomes DUE independently
    And May remains PAID
