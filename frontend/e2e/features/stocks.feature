Feature: Stock portfolio valuation
  The Stocks section values each holding from its latest available market price.

  Scenario: Add two listed stocks and view the combined portfolio value
    Given the signed-in user opens Your money
    When the user searches for and adds 10 ITC shares with ₹4,000 invested
    And the user searches for and adds 5 Reliance shares with ₹6,000 invested
    Then the Stocks section shows ITC's latest price of ₹425.50
    And it shows Reliance's latest price of ₹1,500.00
    And it shows a total stock value of ₹11,755 and overall P&L of ₹1,755

  Scenario: Delete a mistakenly added stock
    Given the signed-in user adds ITC through the stock picker
    When the user selects Delete ITC Limited from its card
    Then the ITC card is absent immediately
    And it remains absent after the user reloads Your money
