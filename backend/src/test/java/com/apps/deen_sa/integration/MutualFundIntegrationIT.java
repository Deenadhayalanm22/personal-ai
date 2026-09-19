package com.apps.deen_sa.integration;

import com.apps.deen_sa.repository.AppUserRepository;
import com.apps.deen_sa.repository.InvestmentTransactionRepository;
import com.apps.deen_sa.repository.UserInvestmentRepository;
import com.apps.deen_sa.service.WebAuthenticationService;
import com.apps.deen_sa.service.MfApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;


@SpringBootTest(properties = {"openai.api-key=", "app.aggregation.scheduling-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
class MutualFundIntegrationIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository users;
    @Autowired private UserInvestmentRepository investments;
    @Autowired private InvestmentTransactionRepository transactions;
    @MockBean private WebAuthenticationService authentication;
    @MockBean private MfApiService mfApi;

    @Test
    void it_mutual_fund_001_tracksOpeningHoldingSipAndLumpSumInOneInvestment() throws Exception {
        MutualFundScenarioDriver scenario = new MutualFundScenarioDriver(mockMvc, users, investments, transactions, authentication, mfApi);
        scenario.startUser();

        // A user begins with an existing holding and commits to a monthly SIP.
        scenario.addFundWithMonthlySipAndOpeningHolding();
        scenario.assertOpeningHoldingAndDueSip();

        // They make one NAV-estimated lump-sum investment, then correct another using their statement.
        scenario.recordNavEstimatedLumpSum();
        scenario.recordStatementVerifiedLumpSum();

        // The fund keeps all activity in one ledger and derives the combined holding from confirmed entries only.
        scenario.assertCombinedHoldingIsAccurate();
        scenario.assertFundCardShowsReturnsAndHoldingMetrics();
    }

    @Test
    void it_mutual_fund_002_deletesAMistakenlyCreatedFundAndItsTransactions() throws Exception {
        MutualFundScenarioDriver scenario = new MutualFundScenarioDriver(mockMvc, users, investments, transactions, authentication, mfApi);
        scenario.startUser();
        scenario.addFundWithMonthlySipAndOpeningHolding();

        scenario.deleteFund();
        scenario.assertFundAndTransactionsAreDeleted();
    }
}
