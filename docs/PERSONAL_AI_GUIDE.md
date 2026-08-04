# Personal AI Finance Application

## What This Project Is

Personal AI is a conversational finance assistant. It helps a person record financial activity, keep track of accounts and balances, and ask questions about their spending using ordinary language instead of forms or spreadsheets.

A person can interact with it primarily through WhatsApp or another connected text interface. For example, they can write:

> “I spent ₹500 on groceries.”

The application understands the message, records the relevant information, asks for anything important that is missing, and updates the appropriate financial account when enough information is available.

## What It Helps With

The application is designed to support these everyday activities:

- Recording expenses, income, transfers, investments, and payments toward loans or credit accounts.
- Setting up financial accounts such as cash, bank accounts, credit accounts, loans, and other value-holding resources.
- Answering questions about spending, transactions, categories, merchants, and time periods.
- Summarising spending for a day, month, category, or other selected period.
- Explaining financial information in clear, natural language.
- Continuing a conversation when a transaction needs more details.
- Keeping an audit trail of changes made to account balances.

## How a Person Uses It

The experience is conversational:

1. The person sends a message through WhatsApp or another supported interface.
2. The application determines what the person is trying to do, such as record spending, ask a question, or set up an account.
3. It extracts useful information from the message, such as the amount, category, merchant, date, or account involved.
4. If an important detail is missing, it asks a short follow-up question.
5. It stores the transaction and updates the relevant balance when the information is complete.
6. It sends back a confirmation, answer, or summary.

### Example: Recording an Expense

**Person:** “I spent ₹500 on groceries.”

**Application:** “Which account did you pay from?”

**Person:** “Cash.”

**Application:** “Recorded a ₹500 grocery expense from Cash.”

The first message is useful even though it does not contain every detail. The application saves the incomplete information and completes it as the person replies.

### Example: Asking a Question

**Person:** “How much did I spend on food last month?”

The application identifies the category and time period, finds the matching records, calculates the total, and presents the result in natural language.

### Example: Setting Up an Account

**Person:** “I have a personal loan of ₹5 lakh and my monthly payment is ₹15,000.”

The application records the loan and its relevant details so that future payments and questions can be associated with it.

## How It Is Integrated with the LLM

The application uses OpenAI’s GPT-4.1 Mini as a language understanding and language generation component.

The LLM is used for tasks that benefit from understanding everyday language:

- Recognising the purpose of a message.
- Extracting financial details from free-form text.
- Understanding answers to follow-up questions.
- Interpreting questions about past transactions.
- Matching informal labels to standard categories or tags.
- Turning already-calculated information into a natural-language explanation.

The prompts that guide the LLM are stored separately from the application code. They define the expected meanings, rules, examples, and response format for each task. This makes the language behaviour easier to review and improve without changing the main business logic.

### Structured Responses

For tasks such as intent recognition and data extraction, the LLM is instructed to return structured JSON. The application validates and converts that response into information it can store or process.

The integration uses a low creativity setting so that responses are consistent and predictable. Invalid or incomplete responses are treated as errors rather than silently being accepted.

### What the LLM Does Not Do

The LLM does not decide the final financial truth. It does not calculate balances, totals, remaining loan payments, or other financial results. Those calculations are performed by the application using stored data and fixed rules.

The LLM may explain a result, but it does not replace the underlying calculation. This separation helps keep financial information accurate, repeatable, and testable.

## How the Application Works Internally

At a high level, the system has five responsibilities:

1. **Receive messages** from a supported channel such as WhatsApp or an API.
2. **Understand the message** using the LLM and structured prompts.
3. **Apply business rules** to determine what can be saved and what balance changes are required.
4. **Store information** in a PostgreSQL database.
5. **Respond clearly** with a confirmation, follow-up question, answer, or summary.

The application is built with Java and Spring Boot. PostgreSQL is used as the durable source of truth for transactions, accounts, balances, and audit information.

## Conversations Can Continue Over Multiple Messages

A transaction does not have to be fully described in one message. The application keeps track of the current conversation and remembers the information already provided.

For example:

- “Spent ₹500” provides the amount.
- “Food” provides the category.
- “Cash” identifies the account used.

The application combines these answers into one transaction. Once the required information is present, it applies the financial effect and completes the conversation.

This approach makes the assistant feel more natural and avoids forcing a person to provide a long, rigid form-like message.

## How Financial Information Is Kept Correct

Financial accuracy is treated as a core requirement.

### No Duplicate Balance Changes

A transaction can affect an account balance at most once. If the same message is retried or processed again, it must not debit or credit the account a second time.

### Clear Account Behaviour

- For cash and bank accounts, spending reduces the available value and incoming money increases it.
- For credit accounts, spending increases the amount owed and a payment reduces the amount owed.
- Account limits and over-limit conditions are tracked where applicable.
- Asset accounts are not allowed to become negative unless an explicitly supported rule permits it.

### Auditability

Every balance change is linked to the transaction that caused it. This makes it possible to understand why a balance changed and to investigate problems.

### Database as the Source of Truth

Balances and financial calculations are based on saved database records, not on an LLM response. Retrying an operation is expected to be safe, and important financial rules are verified through automated tests.

## Information the Application Stores

The application keeps the following kinds of information:

- Transactions, including amounts, dates, categories, merchants, notes, and original text.
- Financial accounts and their current values or outstanding amounts.
- Links between transactions and the accounts they affect.
- Flexible additional details, such as loan payment information.
- Standard categories and tags used to keep reporting consistent.
- An audit record for each account balance change.

The design allows additional account types, transaction types, and financial features to be added over time without changing the overall conversational experience.

## Main Integrations

### WhatsApp

WhatsApp can deliver incoming messages to the application through a webhook. The application processes the message and sends a reply back through the connected messaging setup.

### OpenAI

OpenAI provides the language model used for classification, extraction, matching, and explanations. An OpenAI API key is required for this integration.

### PostgreSQL

PostgreSQL stores the application’s long-term data. It preserves transactions, account balances, financial relationships, and audit records between conversations.

### REST API

A REST interface is also available for submitting natural-language messages, checking application health, and requesting summaries. This allows other applications or future user interfaces to use the same capabilities as WhatsApp.

## What Is Needed to Run It

The application requires:

- Java 21.
- A PostgreSQL database.
- An OpenAI API key when LLM features are enabled.
- Configuration for the database, OpenAI connection, and application server.

It is built and started as a Spring Boot application using Maven.

## Current Scope and Future Possibilities

The current focus is personal financial tracking through conversation. The design can be extended to support:

- Budgets and spending limits.
- Recurring transactions.
- More financial account types.
- Multi-currency support.
- Import and export of financial data.
- Dashboards and richer analytics.
- Mobile applications and additional messaging channels.
- Notifications and financial reminders.

## In One Sentence

Personal AI is a conversational finance assistant that uses an LLM to understand natural language, uses application rules to keep financial data accurate, and uses a database to remember transactions, accounts, balances, and history.
