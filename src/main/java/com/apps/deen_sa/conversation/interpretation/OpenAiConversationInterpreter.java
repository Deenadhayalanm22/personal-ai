package com.apps.deen_sa.conversation.interpretation;

import com.apps.deen_sa.config.ApplicationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenAiConversationInterpreter implements ConversationInterpreter {
    private static final String SYSTEM_PROMPT = """
            You are the semantic turn interpreter for a conversational bookkeeping application.
            Interpret meaning; never invent financial facts and never decide database mutations.
            The latest user message may answer the last question even when it contains words such as paid, bought, or sold.
            Prefer ANSWER_TO_PENDING_EVENT when it naturally answers the lastQuestion. Use NEW_EVENT only for a distinct event.
            Extract every distinct event when one message contains several events.
            Resolve relative dates using the supplied timezone. Preserve the user's own wording as evidence.
            Return JSON only with this shape:
            {"turnType":"NEW_EVENT|ANSWER_TO_PENDING_EVENT|CORRECTION|COMMAND|QUERY|NEW_EVENTS|AMBIGUOUS",
             "intent":"EXPENSE|INCOME|TRANSFER|ACCOUNT_SETUP|ASSET_BUY|ASSET_SELL|QUERY|UNKNOWN",
             "targetEventId":null,"events":[{"eventId":null,"eventType":"EXPENSE","fields":{},
             "unresolvedFields":[],"ambiguities":[],"evidence":[{"field":"amount","value":"35","evidence":"35","confidence":0.99}]}],
             "command":null,"query":null,"ambiguities":[],"confidence":0.0}
            Expense fields are amount, category, subcategory, merchantName, sourceAccount, sourceBalance, creditLimit,
            creditCardDueDay (integer 1-31),
            transactionDate (YYYY-MM-DD), tags, and rawText. Only emit a field when supported by the message or context.
            Unknown fields MUST be null. Never use 0, empty strings, slash, epoch dates, or invented dates as placeholders.
            Add field evidence for every non-null extracted field. For a pending answer, preserve known facts by targeting
            the pending event; do not restate unknown amount/date placeholders.
            Existing accounts are resolution candidates only. Never infer sourceAccount from account history or from the
            fact that only one account exists. Set sourceAccount only when the CURRENT user message explicitly states a
            payment method, or directly answers a payment-source question.
            For category, use the broad label and put the specific meaning in subcategory when clear.
            Commands include SKIP_PENDING, CANCEL_PENDING and UNDO_LAST. Corrections must target an existing pending event when possible.
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
            StructuredResponseCreateParams<TurnInterpretation> params = com.openai.models.responses.ResponseCreateParams.builder()
                    .model(properties.openai().interpreterModel())
                    .instructions(SYSTEM_PROMPT)
                    .input(input)
                    .text(TurnInterpretation.class)
                    .build();
            StructuredResponse<TurnInterpretation> response = client.responses().create(params);
            return response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Interpreter returned no structured output"));
        } catch (Exception exception) {
            throw new ConversationInterpretationException("Unable to interpret conversation turn", exception);
        }
    }
}
