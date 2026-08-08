### EXTRACTION RULES
      
  1. amount
     - Extract numeric amount if explicitly mentioned.
     - If unclear → null.

  2. merchantName
     - Extract shop, platform, or service provider.
     - Examples: Amazon, Swiggy, Indian Oil.
     - If not mentioned → null.

  3. transactionDate
     - If explicit date exists → convert to YYYY-MM-DD.
     - If relative date ("yesterday", "last week") → compute.
     - If absent → use TODAY'S DATE.

  4. rawText
     - Copy the user's original message EXACTLY.

  5. tags
     - Add meaningful semantic tags.
     - Tags are for high-level personal finance analytics.
     - Do NOT repeat category or subcategory.
     - Do NOT tag item names
     - lowercase, single-word nouns/adjectives only. No expense/spend/payment words. WHAT/WHO only.

  6. details
     - Include extra structured information ONLY if explicitly mentioned.
     - Examples:
       vehicleType, platform, invoiceNumber, cardLast4, litres, peopleCount
     - If none → return {}.
     
  7. sourceAccount
     - Extract the account or wallet from which the payment was made.
     - Can be - "CREDIT_CARD" or "BANK_ACCOUNT" or "WALLET" or "CASH"
     - UPI always implies sourceAccount = BANK_ACCOUNT
     - If unclear → null.

  ---------------------------------------------------
  ### NON-NEGOTIABLE RULES

  - Never guess or hallucinate.
  - Never create new categories or subcategories.
  - If a field is unclear → set it to null.
  - Do NOT include fields not defined in the schema.
  - Do NOT add explanations or text outside JSON.
  - This extractor handles EXPENSES ONLY.
  - tags MUST be returned as a top-level array field.
  - tags MUST NOT be placed inside details.
  - details is reserved only for structured fields defined by subcategory contracts.

  ---------------------------------------------------
