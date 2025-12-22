package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.ValueContainerEntity;
import com.apps.deen_sa.llm.impl.LoanQueryExplainer;
import com.apps.deen_sa.orchestrator.ConversationContext;
import com.apps.deen_sa.orchestrator.SpeechResult;
import com.apps.deen_sa.repo.TransactionRepository;
import com.apps.deen_sa.repo.ValueContainerRepo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LoanAnalysisService {

    private final ValueContainerRepo valueContainerRepo;

    private final TransactionRepository transactionRepo;

    private final LoanQueryExplainer llm;

    public LoanAnalysisService(ValueContainerRepo valueContainerRepo, TransactionRepository transactionRepo, LoanQueryExplainer llm) {
        this.valueContainerRepo = valueContainerRepo;
        this.transactionRepo = transactionRepo;
        this.llm = llm;
    }

    /**
     * Entry point for:
     * "How many EMIs are left?"
     */
    public SpeechResult handleEmiRemaining(String userText, ConversationContext ctx) {

        List<ValueContainerEntity> activeLoans =
                valueContainerRepo.findActiveLoansForUser(1L); // replace userId

        // 1️⃣ No loans at all
        if (activeLoans.isEmpty()) {
            return SpeechResult.info("You don’t have any active loans recorded.");
        }

        // 2️⃣ Multiple loans → ask clarification
        if (activeLoans.size() > 1 && ctx.getMetadata() == null) {

            ctx.setActiveIntent("QUERY");
            ctx.setWaitingForField("loanSelection");
            ctx.setMetadata(Map.of(
                    "loanIds", activeLoans.stream()
                            .map(ValueContainerEntity::getId)
                            .toList()
            ));

            String options = activeLoans.stream()
                    .map(ValueContainerEntity::getName)
                    .collect(Collectors.joining(", "));

            return SpeechResult.followup(
                    "Which loan are you referring to? (" + options + ")",
                    List.of("loanSelection"),
                    null
            );
        }

        // 3️⃣ Single loan or already resolved
        ValueContainerEntity loan = activeLoans.getFirst();

        return computeAndRespond(loan);
    }

    // -------------------------------------------------------
    // CORE COMPUTATION (NO LLM HERE)
    // -------------------------------------------------------

    private SpeechResult computeAndRespond(ValueContainerEntity loan) {

        // A️⃣ Count EMIs already paid
        int emiPaid = transactionRepo.countLoanEmis(loan.getId());

        Map<String, Object> details =
                loan.getDetails() != null ? loan.getDetails() : Map.of();

        BigDecimal emiAmount = getBigDecimal(details, "emiAmount");
        Integer tenureMonths = getInteger(details, "tenureMonths");

        if (emiAmount == null) {
            return SpeechResult.info(
                    "EMI amount is not recorded for this loan."
            );
        }

        // B️⃣ Compute EMIs left
        int emiLeft;

        if (tenureMonths != null) {
            emiLeft = Math.max(tenureMonths - emiPaid, 0);
        } else {
            emiLeft = loan.getCurrentValue()
                    .divide(emiAmount, 0, RoundingMode.CEILING)
                    .intValue();
        }

        // C️⃣ Build summary (compact, factual)
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("loanName", loan.getName());
        summary.put("emiPaid", emiPaid);
        summary.put("emiLeft", emiLeft);
        summary.put("emiAmount", emiAmount);
        summary.put("outstanding", loan.getCurrentValue());
        summary.put("endDate", details.get("endDate"));

        // D️⃣ Ask LLM to explain (ONLY explanation)
        String explanation = llm.explainEmiRemaining(summary);

        return SpeechResult.info(explanation);
    }

    // -------------------------------------------------------
    // SAFE EXTRACTION HELPERS
    // -------------------------------------------------------

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        if (!map.containsKey(key)) return null;
        return new BigDecimal(map.get(key).toString());
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        if (!map.containsKey(key)) return null;
        return Integer.parseInt(map.get(key).toString());
    }
}
