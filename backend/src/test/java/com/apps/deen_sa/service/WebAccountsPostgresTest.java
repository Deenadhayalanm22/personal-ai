package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.repository.*;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static com.apps.deen_sa.service.CreditCardBillPostgresTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** FIN-EPIC-005 / FIN-025: account context and expense identity, without a bank ledger. */
@EnabledIfEnvironmentVariable(named="EXPENSE_CHAT_TEST_DB_URL",matches=".+")
class WebAccountsPostgresTest {
    static WebAccountService accounts;
    static WebCreditCardService cardProfiles;
    static WebReferenceMergeService merges;
    @BeforeAll static void setupAccounts() {
        CreditCardBillPostgresTest.setup();
        var repos=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
        var refs=repos.getRepository(UserReferenceEntityRepository.class);var aliases=repos.getRepository(UserReferenceAliasRepository.class);
        cardProfiles=new WebCreditCardService(repos.getRepository(UserCreditCardRepository.class),refs,mock(MonthlyFinancialSnapshotService.class));
        ReflectionTestUtils.setField(cardProfiles,"jdbc",jdbc);
        accounts=new WebAccountService(refs,repos.getRepository(UserCreditCardRepository.class),cardProfiles,jdbc);
        ReflectionTestUtils.setField(accounts,"aliases",aliases);
        merges=new WebReferenceMergeService(refs,aliases,transactions);ReflectionTestUtils.setField(merges,"jdbc",jdbc);
    }
    @BeforeEach void resetAccounts() {
        new CreditCardBillPostgresTest().reset();
        jdbc.update("DELETE FROM account_money_received");jdbc.update("DELETE FROM bank_account_profile");
    }
    @AfterAll static void cleanupAccounts(){CreditCardBillPostgresTest.cleanup();}
    WebAccountService.AccountRequest bank(String name) {return new WebAccountService.AccountRequest(null,name,"BANK",null,null,null);}
    WebAccountService.AccountView create(WebAccountService.AccountRequest r){return tx.execute(status->accounts.create(owner,r));}
    @Test void nameAndTypeAreEnoughAndReuseExpenseIdentityWithoutLedgerWrites() {
        var bank=create(bank("Salary account"));
        assertThat(bank.type()).isEqualTo("BANK");
        assertThat(create(bank("Salary account")).id()).isEqualTo(bank.id());
        tx.executeWithoutResult(status->assertThat(new ExpenseEditOptionsService(new ExpenseTaxonomyRegistry(),new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf)).getRepository(UserReferenceEntityRepository.class)).options(owner).accounts()).anySatisfy(a->assertThat(a.id()).isEqualTo(bank.id())));
        assertThat(jdbc.queryForObject("SELECT opening_amount FROM bank_account_profile WHERE account_reference_id=?",java.math.BigDecimal.class,bank.id())).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_money_received",Long.class)).isZero();
        assertThat(Arrays.stream(WebAccountService.AccountView.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)).containsExactly("id","name","type","cardId","issuerName","statementDay","dueDay","startMonth");
    }
    @Test void newCreditCardIsImmediatelyLinkedAndExistingCardRemainsVisible() {
        var card=create(new WebAccountService.AccountRequest(null,"New Visa","CREDIT_CARD","Bank",1,21,"2026-11"));
        assertThat(card.startMonth()).isEqualTo("2026-11");
        var cleared=tx.execute(status->accounts.configure(owner,card.id(),new WebAccountService.AccountRequest(card.id(),null,"CREDIT_CARD","Bank",1,21,"")));
        assertThat(cleared.startMonth()).isNull();assertThat(card.cardId()).isNotNull();assertThat(card.type()).isEqualTo("CREDIT_CARD");
        tx.executeWithoutResult(status->assertThat(accounts.list(owner).accounts()).anySatisfy(a->{assertThat(a.id()).isEqualTo(account);assertThat(a.cardId()).isEqualTo(cardId);}));
        assertThatThrownBy(()->tx.execute(status->accounts.configure(owner,card.id(),new WebAccountService.AccountRequest(card.id(),null,"BANK",null,null,null)))).isInstanceOf(WebApiException.class);
    }
    @Test void validatesOwnershipAndProtectsConfiguredIdentities() {
        var bank=create(bank("Validation bank"));
        assertThatThrownBy(()->tx.execute(status->accounts.configure(other,bank.id(),bank("Foreign")))).isInstanceOf(WebApiException.class).hasMessageContaining("not found");
        assertThat(tx.execute(status->accounts.list(other)).accounts()).isEmpty();
        assertThatThrownBy(()->tx.execute(status->merges.merge(owner,new WebReferenceMergeService.MergeRequest(UserReferenceEntityType.ACCOUNT,List.of(bank.id(),account),"Validation bank")))).isInstanceOf(WebApiException.class).hasMessageContaining("cannot be merged");
        assertThatThrownBy(()->tx.execute(status->cardProfiles.create(owner,new WebCreditCardService.CardRequest(bank.id(),"Card","Bank",1,21,true)))).isInstanceOf(WebApiException.class).hasMessageContaining("bank/debit");
        assertThatThrownBy(()->create(new WebAccountService.AccountRequest(null,"Bad date","CREDIT_CARD","HDFC",1,21,"2026-13"))).isInstanceOf(WebApiException.class);
        assertThatThrownBy(()->create(bank(""))).isInstanceOf(WebApiException.class);
        assertThatThrownBy(()->create(new WebAccountService.AccountRequest(null,"Invalid card","CREDIT_CARD","Bank",0,21))).isInstanceOf(WebApiException.class);
    }
}
