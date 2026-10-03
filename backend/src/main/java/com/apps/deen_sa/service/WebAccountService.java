package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.repository.*;
import com.apps.deen_sa.exception.WebApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** FIN-EPIC-005 / FIN-025: typed accounts share the reference identity used by expenses. */
@Service
@RequiredArgsConstructor
public class WebAccountService {
    private final UserReferenceEntityRepository references;
    private final UserCreditCardRepository cards;
    private final WebCreditCardService creditCards;
    private final JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private UserReferenceAliasRepository aliases;

    @Transactional(readOnly=true)
    public AccountList list(AppUserEntity user) {
        return new AccountList(user.getCurrency(), references.findByUserIdAndEntityTypeAndActiveTrueOrderByCanonicalNameAsc(user.getId(),UserReferenceEntityType.ACCOUNT)
                .stream().map(ref -> view(user,ref)).toList());
    }

    @Transactional
    public AccountView create(AppUserEntity user, AccountRequest request) {
        validate(user,request);
        // Serialize account-name creation with expense reference configuration for this profile.
        jdbc.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",Long.class,user.getId());
        var ref = request.accountReferenceId() == null
                ? references.findByUserIdAndEntityTypeAndCanonicalNameIgnoreCase(user.getId(),UserReferenceEntityType.ACCOUNT,request.name().trim()).orElse(null)
                : owned(user,request.accountReferenceId());
        if(ref==null && aliases!=null) {
            var matches=references.findByUserIdAndEntityTypeAndActiveTrue(user.getId(),UserReferenceEntityType.ACCOUNT).stream()
                    .filter(candidate -> aliases.findByReferenceEntityIdAndAliasTextIgnoreCase(candidate.getId(),request.name().trim()).isPresent()).toList();
            if(matches.size()>1)throw invalid("This account alias is ambiguous; choose the existing account");
            if(matches.size()==1)ref=matches.getFirst();
        }
        if (ref == null) {
            ref=new UserReferenceEntity();ref.setUser(user);ref.setEntityType(UserReferenceEntityType.ACCOUNT);ref.setCanonicalName(request.name().trim());
            references.saveAndFlush(ref);
        } else if (!ref.isActive()) throw invalid("Choose an active account name");
        return configure(user,ref,request);
    }

    @Transactional
    public AccountView configure(AppUserEntity user, Long id, AccountRequest request) {
        validate(user,request);
        var ref=owned(user,id);
        references.findAllByIdForUpdate(List.of(id));
        return configure(user,ref,request);
    }

    private AccountView configure(AppUserEntity user,UserReferenceEntity ref,AccountRequest request) {
        references.findAllByIdForUpdate(List.of(ref.getId()));
        var existing=view(user,ref);
        if(request.type().equals("BANK") && jdbc.queryForObject("SELECT COUNT(*) FROM user_credit_card WHERE account_reference_id=?",Long.class,ref.getId())>0)
            throw invalid("This identity belongs to a credit card; use a separate bank/debit account");
        if (!existing.type().equals("UNCONFIGURED") && !existing.type().equals(request.type()))
            throw invalid("A configured account cannot change between bank/debit and credit card");
        if (request.type().equals("CREDIT_CARD")) {
            var details=new WebCreditCardService.CardRequest(ref.getId(),ref.getCanonicalName(),request.issuerName(),request.statementDay(),request.dueDay(),true,request.startMonth());
            if(existing.cardId()==null)creditCards.create(user,details);else creditCards.update(user,existing.cardId(),details);
        } else if (existing.type().equals("UNCONFIGURED")) {
            jdbc.update("INSERT INTO bank_account_profile(account_reference_id) VALUES (?)",ref.getId());
        }
        return view(user,ref);
    }

    private AccountView view(AppUserEntity user,UserReferenceEntity ref) {
        var card=cards.findByUserIdAndActiveTrueOrderByCreatedAtDesc(user.getId()).stream().filter(c->c.getAccountReference().getId().equals(ref.getId())).findFirst();
        if(card.isPresent()) {
            var c=card.get();return new AccountView(ref.getId(),ref.getCanonicalName(),"CREDIT_CARD",c.getId(),c.getIssuerName(),c.getStatementDay(),c.getDueDay(),c.getStartMonth()==null?null:java.time.YearMonth.from(c.getStartMonth()).toString());
        }
        boolean bank=jdbc.queryForObject("SELECT COUNT(*) FROM bank_account_profile WHERE account_reference_id=?",Long.class,ref.getId())>0;
        return new AccountView(ref.getId(),ref.getCanonicalName(),bank?"BANK":"UNCONFIGURED",null,null,null,null,null);
    }

    private UserReferenceEntity owned(AppUserEntity user,Long id) {
        return references.findByIdAndUserIdAndEntityTypeAndActiveTrue(id,user.getId(),UserReferenceEntityType.ACCOUNT)
                .orElseThrow(()->new WebApiException(HttpStatus.NOT_FOUND,"ACCOUNT_NOT_FOUND","Account not found"));
    }
    private void validate(AppUserEntity user,AccountRequest r) {
        if(r==null || r.type()==null || !List.of("BANK","CREDIT_CARD").contains(r.type()))throw invalid("Choose bank/debit or credit card");
        if(r.accountReferenceId()==null && (r.name()==null || r.name().trim().isEmpty() || r.name().trim().length()>120))throw invalid("Enter an account name of 1–120 characters");
        if(r.type().equals("CREDIT_CARD") && (r.issuerName()==null || r.issuerName().trim().isEmpty() || r.issuerName().trim().length()>120 || r.statementDay()==null || r.statementDay()<1 || r.statementDay()>28 || r.dueDay()==null || r.dueDay()<1 || r.dueDay()>28))
            throw invalid("Enter the issuer and bill generation/due days from 1–28");
    }
    private WebApiException invalid(String message){return new WebApiException(HttpStatus.BAD_REQUEST,"INVALID_ACCOUNT",message);}
    public record AccountRequest(Long accountReferenceId,String name,String type,String issuerName,Integer statementDay,Integer dueDay,String startMonth){
        public AccountRequest(Long accountReferenceId,String name,String type,String issuerName,Integer statementDay,Integer dueDay){this(accountReferenceId,name,type,issuerName,statementDay,dueDay,null);}
    }
    public record AccountView(Long id,String name,String type,Long cardId,String issuerName,Integer statementDay,Integer dueDay,String startMonth){}
    public record AccountList(String currency,List<AccountView> accounts){}
}
