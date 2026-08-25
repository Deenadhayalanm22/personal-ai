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

  5. details
     - Include extra structured information ONLY if explicitly mentioned.
     - Examples:
       vehicleType, platform, invoiceNumber, cardLast4, litres, peopleCount
     - If none → return {}.
     
  6. sourceAccount
     - Extract the account or wallet from which the payment was made.
     - Can be - "CREDIT_CARD" or "BANK_ACCOUNT" or "WALLET" or "CASH"
     - UPI always implies sourceAccount = BANK_ACCOUNT
     - If unclear → null.

  7. purchased physical items and taxonomy candidates
     - An unfamiliar product name is not a reason to classify a purchase as Miscellaneous.
     - For an identifiable physical item that was bought, purchased, or ordered:
       - Electronics → Shopping / Electronics.
       - Clothing, footwear, or wearable accessories → Shopping / Clothing.
       - Explicit gifts → Shopping / Gifts.
       - Clearly educational or medical items → their applicable Education or Medical subcategory.
       - Otherwise → Shopping / Other Shopping.
     - Examples of the fallback: beach mat, storage box, umbrella, suitcase, water bottle, yoga mat.
     - Miscellaneous is a last resort for expenses that are not purchases of identifiable physical goods.
     - When a broad category is clear but no configured specific subcategory genuinely fits, optionally return
       taxonomyCandidate with a reusable proposed category/subcategory, a short generic itemConcept, and confidence.
     - A taxonomy candidate is a review suggestion only. The regular category and subcategory must still use the
       configured taxonomy and its category-specific Other fallback.
     - Candidate names must be reusable spending concepts. Never use a merchant, brand, person, location, or a
       description unique to this purchase.
     - Do not propose a candidate when an existing configured subcategory is reasonably accurate.

  ---------------------------------------------------
  ### NON-NEGOTIABLE RULES

  - Never guess or hallucinate.
  - Never create new categories or subcategories.
  - If a field is unclear → set it to null.
  - Do NOT include fields not defined in the schema.
  - Do NOT add explanations or text outside JSON.
  - This extractor handles EXPENSES ONLY.
  - details is reserved only for structured fields defined by subcategory contracts.

  ---------------------------------------------------
