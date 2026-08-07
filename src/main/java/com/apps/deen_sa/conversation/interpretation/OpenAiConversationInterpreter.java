package com.apps.deen_sa.conversation.interpretation;

import com.apps.deen_sa.config.ApplicationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.apps.deen_sa.llm.AiCallTelemetry;

@Service
@RequiredArgsConstructor
public class OpenAiConversationInterpreter implements ConversationInterpreter {
    private static final String SYSTEM_PROMPT = """
            Interpret one multilingual bookkeeping turn (including English, Tamil, and romanized Tamil) into JSON.
            Never invent facts or authorize database changes. Current input can answer lastQuestion; prefer
            ANSWER_TO_PENDING_EVENT for that case. NEW_EVENT means a distinct activity stated now.

            Shape:
            {"turnType":"NEW_EVENT|ANSWER_TO_PENDING_EVENT|CORRECTION|COMMAND|QUERY|NEW_EVENTS|AMBIGUOUS",
             "intent":"EXPENSE|INCOME|TRANSFER|LIABILITY_PAYMENT|ACCOUNT_SETUP|ASSET_BUY|ASSET_SELL|QUERY|UNKNOWN",
             "language":"en-IN|ta-IN|ta-Latn|other",
             "targetEventId":null,"events":[{"eventId":null,"eventType":"EXPENSE","fields":{},
             "unresolvedFields":[],"ambiguities":[],"evidence":[{"field":"amount","value":"35","evidence":"35","confidence":0.99}]}],
             "command":null,"query":"NONE",
             "ambiguities":[],"confidence":0.0}

            Financial fields: amount, category, subcategory, merchantName, sourceAccount, destinationAccount,
            sourceBalance, creditLimit,
            creditCardDueDay (1-31), transactionDate (YYYY-MM-DD), tags, rawText.
            Rules:
            - Unknown fields are null; never use placeholder strings or invented dates.
            - Every non-null field needs exact supporting evidence copied from the CURRENT message. Context evidence may
              resolve a pending event but must never create a new event.
            - A NEW_EVENT amount must be explicitly evidenced in the current message.
            - Existing accounts are candidates only. Set sourceAccount only when stated now or answering that question.
              Canonical payment-source mapping: UPI, bank transfer, debit card, FASTag linked to bank, and auto-debit
              from bank => BANK_ACCOUNT; cash => CASH; credit card or card EMI => CREDIT_CARD; wallet => WALLET.
              When any payment source is explicit, ALWAYS emit sourceAccount and copy the exact payment phrase into its
              evidence. This mapping applies equally to English, Tamil, and romanized Tamil.
            - Account declarations and setup requests are NEW_EVENT with intent and eventType ACCOUNT_SETUP. Examples:
              "create my HDFC salary bank account with balance 20000", "add an ICICI credit card with a 1 lakh
              limit", and "என் வங்கி கணக்கில் 40000 இருப்பு உள்ளது". Account balances, outstanding amounts, limits,
              and due days are setup attributes, NOT transaction amounts. For ACCOUNT_SETUP, leave amount null and
              place the complete current message in rawText; the account setup handler owns detailed extraction and
              follow-up. Never return AMBIGUOUS merely because a setup sentence contains a balance.
            - Incoming money is NEW_EVENT with intent and eventType INCOME. This includes salary, customer receipts,
              refunds, interest, gifts, and any other money credited to the user. Put the receiving account's exact
              stated name in destinationAccount. For example, "salary credited to my HDFC salary account" means
              destinationAccount="HDFC salary account". Never put the receiving account in sourceAccount.
            - Paying a credit-card bill or loan is NEW_EVENT with intent and eventType LIABILITY_PAYMENT, never
              EXPENSE. Examples: "pay my HDFC card bill from my salary account", "credit card payment", "clear the
              SBI card outstanding", and "pay my loan EMI". Buying goods or services USING a card remains EXPENSE.
              Preserve the exact named source and liability from the current message in rawText; the liability-payment
              handler performs detailed extraction and exact container resolution.
            - Use broad category and specific subcategory. Resolve relative dates with timezone.
            - query is always required. Use NONE for non-query turns. Questions about existing data are QUERY with no
              events and the matching canonical period. Examples: "today"/"இன்று"/"inniku" => TODAY;
              "this month"/"இந்த மாதம்"/"intha month" => THIS_MONTH.
            - Greetings/help are COMMAND HELP. Controls: SKIP_PENDING, CANCEL_PENDING, UNDO_LAST.
            """;

    private final OpenAIClient client;
    private final ObjectMapper mapper;
    private final ApplicationProperties properties;

    @Override
    public TurnInterpretation interpret(String userMessage, InterpretationContext context) {
        try {
            String input = mapper.writeValueAsString(java.util.Map.of(
                    "userMessage", userMessage,
                    "context", context
            ));
            String instructions = SYSTEM_PROMPT + pendingInstruction(context);
            TurnInterpretation primary = callModel(input, instructions, properties.openai().model(),
                    "conversation_interpretation");
            String escalationModel = properties.openai().escalationModel();
            double confidence = primary.confidence() == null ? 0 : primary.confidence();
            if (confidence < properties.openai().escalationConfidence()
                    && escalationModel != null && !escalationModel.isBlank()
                    && !escalationModel.equals(properties.openai().model())) {
                return callModel(input, instructions, escalationModel, "conversation_interpretation_escalation");
            }
            return primary;
        } catch (Exception exception) {
            throw new ConversationInterpretationException("Unable to interpret conversation turn", exception);
        }
    }

    private TurnInterpretation callModel(String input, String instructions, String model, String purpose) {
        long startedNanos = System.nanoTime();
        try {
            StructuredResponseCreateParams<TurnInterpretation> params = com.openai.models.responses.ResponseCreateParams.builder()
                    .model(model)
                    .instructions(instructions)
                    .input(input)
                    .text(TurnInterpretation.class)
                    .build();
            StructuredResponse<TurnInterpretation> response = client.responses().create(params);
            response.usage().ifPresentOrElse(usage -> AiCallTelemetry.success(
                            purpose, model, usage.inputTokens(),
                            usage.inputTokensDetails().cachedTokens(), usage.outputTokens(), startedNanos),
                    () -> AiCallTelemetry.success(purpose, model, 0, 0, 0, startedNanos));
            return response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Interpreter returned no structured output"));
        } catch (Exception exception) {
            AiCallTelemetry.failure(purpose, model, startedNanos);
            if (exception instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Model call failed", exception);
        }
    }

    private String pendingInstruction(InterpretationContext context) {
        if (context.pendingEvents() == null || context.pendingEvents().isEmpty()) return "";
        PendingEvent pending = context.pendingEvents().getLast();
        if (pending.unresolvedFields() == null || pending.unresolvedFields().isEmpty()) return "";
        String field = pending.unresolvedFields().getFirst();
        return """

                CURRENT TURN OVERRIDE: A transaction is pending and needs field `%s`. Interpret the latest message
                primarily as ANSWER_TO_PENDING_EVENT targeting that event. A short noun phrase or description can be a
                complete answer and does not need an amount or transaction verb. For category, examples such as
                "coffee and snacks", "மாலை சிற்றுண்டி", or "petrol ku" are category answers. Use NEW_EVENT only when
                the current message clearly states a separate activity with its own amount. If the field is not answered,
                return AMBIGUOUS without inventing values.
                """.formatted(field);
    }
}
