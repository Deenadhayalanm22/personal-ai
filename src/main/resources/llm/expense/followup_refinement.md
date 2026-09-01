Refine the missing field for the user's transaction.

PREVIOUS PARTIAL DATA:
%s

REQUESTED FIELD OR FOLLOW-UP MODE:
%s

USER ANSWER:
"%s"

RULE:
- When the mode is expenseCompletion or expenseCorrection, extract every explicitly supported expense field present in the answer.
- When the mode is expenseDetails, extract only explicitly stated sourceAccount and supported optional details.
- Otherwise, extract only the requested field and its dependent classification fields (e.g., category→subcategory).
- Treat the answer as a patch: omit stable previous facts that the user did not mention.
- Never invent values.
- Return ONLY explicitly updated fields in JSON.
