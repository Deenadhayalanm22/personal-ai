package com.apps.deen_sa.integration;

import com.apps.deen_sa.domain.InputType;
import com.apps.deen_sa.domain.MessageSource;
import com.apps.deen_sa.domain.SpendingNature;
import com.apps.deen_sa.domain.TransactionDraftStatus;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.FinancialTransactionEntity;
import com.apps.deen_sa.entity.TransactionDraftEntity;
import com.apps.deen_sa.entity.UserReferenceEntity;
import com.apps.deen_sa.repository.AppUserRepository;
import com.apps.deen_sa.repository.FinancialTransactionRepository;
import com.apps.deen_sa.repository.TransactionDraftRepository;
import com.apps.deen_sa.repository.UserReferenceEntityRepository;
import com.apps.deen_sa.service.ExpenseDailyAggregationService;
import com.apps.deen_sa.service.WebAuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"openai.api-key=", "app.aggregation.scheduling-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
class NormalizationTestIT {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 10);

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository users;
    @Autowired private UserReferenceEntityRepository references;
    @Autowired private TransactionDraftRepository drafts;
    @Autowired private FinancialTransactionRepository transactions;
    @Autowired private ExpenseDailyAggregationService aggregation;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private WebAuthenticationService authentication;

    @Test
    void it_norm_001() throws Exception {
        AppUserEntity user = createUser("normalization-merchant-user");
        UserReferenceEntity coffeeShop = merchant(user, "Brew House");
        UserReferenceEntity coffeeHouse = merchant(user, "BrewHouse Cafe");
        for (int index = 0; index < 10; index++) {
            transaction(user, "merchant", index, index < 5 ? coffeeShop : coffeeHouse, null);
        }

        aggregation.rebuild(DATE);
        assertThat(referenceAggregateCount(user)).isEqualTo(2);

        when(authentication.authenticate("normalization-session")).thenReturn(user);
        mockMvc.perform(post("/api/web/reference-preferences/merge")
                        .cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "normalization-session"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entityType": "MERCHANT",
                                  "referenceIds": [%d, %d],
                                  "canonicalName": "Brew House"
                                }
                                """.formatted(coffeeShop.getId(), coffeeHouse.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canonicalReference.id").value(coffeeShop.getId()))
                .andExpect(jsonPath("$.mergedReferenceIds[0]").value(coffeeHouse.getId()))
                .andExpect(jsonPath("$.updatedTransactionCount").value(10));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM financial_transaction
                WHERE user_id = ? AND merchant_id = ? AND deleted_at IS NULL
                """, Integer.class, user.getId(), coffeeShop.getId())).isEqualTo(10);
        assertThat(references.findById(coffeeHouse.getId()).orElseThrow().isActive()).isFalse();

        aggregation.rebuild(DATE);
        assertThat(referenceAggregateCount(user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT transaction_count FROM expense_daily_reference_aggregate
                WHERE user_id = ? AND aggregate_date = ? AND reference_entity_id = ? AND reference_type = 'MERCHANT'
                """, Integer.class, user.getId(), DATE, coffeeShop.getId())).isEqualTo(10);
    }

    @Test
    void it_norm_002() throws Exception {
        AppUserEntity user = createUser("normalization-account-user");
        UserReferenceEntity primary = reference(user, UserReferenceEntityType.ACCOUNT, "HDFC Primary");
        UserReferenceEntity salary = reference(user, UserReferenceEntityType.ACCOUNT, "HDFC Salary");
        for (int index = 0; index < 5; index++) {
            transaction(user, "account", index, null, index < 3 ? primary : salary);
        }

        aggregation.rebuild(DATE);
        when(authentication.authenticate("account-session")).thenReturn(user);
        mockMvc.perform(post("/api/web/reference-preferences/merge")
                        .cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "account-session"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entityType":"ACCOUNT","referenceIds":[%d,%d],"canonicalName":"HDFC Primary"}
                                """.formatted(primary.getId(), salary.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedTransactionCount").value(5));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM financial_transaction
                WHERE user_id = ? AND source_account_id = ? AND deleted_at IS NULL
                """, Integer.class, user.getId(), primary.getId())).isEqualTo(5);
        assertThat(references.findById(salary.getId()).orElseThrow().isActive()).isFalse();
        aggregation.rebuild(DATE);
        assertThat(transactionAggregateCount(user)).isEqualTo(1);
    }

    @Test
    void it_norm_003() throws Exception {
        AppUserEntity user = createUser("normalization-beneficiary-user");
        UserReferenceEntity first = reference(user, UserReferenceEntityType.BENEFICIARY, "Asha");
        UserReferenceEntity second = reference(user, UserReferenceEntityType.BENEFICIARY, "Asha Patel");
        for (int index = 0; index < 5; index++) {
            transaction(user, "beneficiary", index, null, null);
        }

        aggregation.rebuild(DATE);
        when(authentication.authenticate("beneficiary-session")).thenReturn(user);
        mockMvc.perform(post("/api/web/reference-preferences/merge")
                        .cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "beneficiary-session"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entityType":"BENEFICIARY","referenceIds":[%d,%d],"canonicalName":"Asha"}
                                """.formatted(first.getId(), second.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedTransactionCount").value(0));

        assertThat(references.findById(second.getId()).orElseThrow().isActive()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_transaction WHERE user_id = ?",
                Integer.class, user.getId())).isEqualTo(5);
        aggregation.rebuild(DATE);
        assertThat(transactionAggregateCount(user)).isEqualTo(1);
    }

    private AppUserEntity createUser(String externalUserId) {
        AppUserEntity user = new AppUserEntity();
        user.setChannel("WHATSAPP");
        user.setExternalUserId(externalUserId);
        user.setCreatedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private UserReferenceEntity merchant(AppUserEntity user, String name) {
        return reference(user, UserReferenceEntityType.MERCHANT, name);
    }

    private UserReferenceEntity reference(AppUserEntity user, UserReferenceEntityType type, String name) {
        UserReferenceEntity merchant = new UserReferenceEntity();
        merchant.setUser(user);
        merchant.setEntityType(type);
        merchant.setCanonicalName(name);
        merchant.setActive(true);
        merchant.setCreatedAt(Instant.now());
        merchant.setUpdatedAt(Instant.now());
        return references.saveAndFlush(merchant);
    }

    private void transaction(AppUserEntity user, String scenario, int index,
            UserReferenceEntity merchant, UserReferenceEntity sourceAccount) {
        TransactionDraftEntity draft = new TransactionDraftEntity();
        draft.setUser(user);
        draft.setInputType(InputType.TEXT);
        draft.setSource(MessageSource.WHATSAPP);
        draft.setSourceMessageId("normalization-" + scenario + "-" + index);
        draft.setRawText("coffee " + index);
        draft.setStatus(TransactionDraftStatus.CONSUMED);
        draft.setCreatedAt(Instant.now());
        draft.setUpdatedAt(Instant.now());
        draft = drafts.saveAndFlush(draft);

        FinancialTransactionEntity transaction = new FinancialTransactionEntity();
        transaction.setUser(user);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setOccurredAt(DATE);
        transaction.setCategory("Food");
        transaction.setSubcategory("Dining");
        transaction.setSpendingNature(SpendingNature.DISCRETIONARY);
        transaction.setMerchant(merchant);
        transaction.setSourceAccount(sourceAccount);
        transaction.setSourceDraft(draft);
        transaction.setCreatedAt(Instant.now());
        transaction.setUpdatedAt(Instant.now());
        transactions.saveAndFlush(transaction);
    }

    private int referenceAggregateCount(AppUserEntity user) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM expense_daily_reference_aggregate
                WHERE user_id = ? AND aggregate_date = ? AND reference_type = 'MERCHANT'
                """, Integer.class, user.getId(), DATE);
    }

    private int transactionAggregateCount(AppUserEntity user) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM expense_daily_aggregate
                WHERE user_id = ? AND aggregate_date = ?
                """, Integer.class, user.getId(), DATE);
    }
}
