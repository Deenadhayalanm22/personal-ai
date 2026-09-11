package com.apps.deen_sa.support;

import com.apps.deen_sa.domain.SpendingNature;
import com.apps.deen_sa.entity.FinancialTransactionEntity;
import com.apps.deen_sa.entity.UserReferenceEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Shared recorded facts for unit and real-capture integration regression tests. */
public final class SeptemberStoryFixture {
    private SeptemberStoryFixture() { }
    public static JsonNode json() throws Exception {
        try (var input = new ClassPathResource("money-stories/september-observation-regression.json").getInputStream()) {
            return new ObjectMapper().readTree(input);
        }
    }
    public static List<FinancialTransactionEntity> expenses() throws Exception {
        List<FinancialTransactionEntity> result = new ArrayList<>();
        Map<String, UserReferenceEntity> merchants = new HashMap<>();
        for (JsonNode row : json().path("entries")) {
            var tx = new FinancialTransactionEntity();
            tx.setId(row.path("id").asLong()); tx.setOccurredAt(LocalDate.parse(row.path("occurredOn").asText()));
            tx.setAmount(new BigDecimal(row.path("amount").asText()));
            tx.setCategory(row.path("category").asText()); tx.setSubcategory(row.path("subcategory").asText());
            if (!row.path("spendingNature").isNull()) tx.setSpendingNature(SpendingNature.valueOf(row.path("spendingNature").asText()));
            if (!row.path("merchant").isNull()) tx.setMerchant(merchants.computeIfAbsent(row.path("merchant").asText(), name -> {
                var merchant = new UserReferenceEntity(); merchant.setId((long) merchants.size()+1); merchant.setCanonicalName(name); return merchant;
            }));
            result.add(tx);
        }
        return result;
    }
}
