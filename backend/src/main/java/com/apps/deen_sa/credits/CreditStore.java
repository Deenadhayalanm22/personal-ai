package com.apps.deen_sa.credits;

import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.insights.ExpenseChatModel;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static com.apps.deen_sa.credits.CreditPolicy.error;

/** FIN-EPIC-003: durable reservations, usage ledger and shared chat budget. No provider I/O in transactions. */
@Service
public class CreditStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CreditPolicy policy;
    private final Clock clock;
    public CreditStore(JdbcTemplate jdbc, PlatformTransactionManager manager, CreditPolicy policy, Clock clock) {
        this.jdbc = jdbc; this.tx = new TransactionTemplate(manager); this.policy = policy; this.clock = clock;
    }
    private void ensureWallet(long user) {
        jdbc.update("INSERT INTO ai_credit_wallet(user_id) VALUES (?) ON CONFLICT DO NOTHING", user);
    }
    private Wallet lockWallet(long user) {
        ensureWallet(user);
        return jdbc.queryForObject("SELECT balance,reserved,paused FROM ai_credit_wallet WHERE user_id=? FOR UPDATE",
                (rs, n) -> new Wallet(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBoolean(3)), user);
    }
    public Balance balance(long user) {
        var rows = jdbc.query("SELECT balance,reserved,paused FROM ai_credit_wallet WHERE user_id=?",
                (rs, n) -> new Wallet(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBoolean(3)), user);
        Wallet wallet = rows.isEmpty() ? new Wallet(BigDecimal.ZERO, BigDecimal.ZERO, false) : rows.getFirst();
        return new Balance(wallet.balance, wallet.reserved, wallet.balance.subtract(wallet.reserved), wallet.paused,
                policy.enabled(), policy.configured());
    }
    /** Returns a cached success; a repeated failure or in-flight request never calls the provider again. */
    public String start(long user, UUID id, String fingerprint) {
        return tx.execute(status -> {
            Wallet wallet = lockWallet(user);
            var previous = jdbc.queryForList("SELECT * FROM ai_credit_request WHERE user_id=? AND id=?", user, id);
            if (!previous.isEmpty()) {
                var row = previous.getFirst();
                if (!fingerprint.equals(row.get("fingerprint"))) throw error(HttpStatus.CONFLICT, "AI_REQUEST_CONFLICT", "This request ID was already used for a different question.");
                if ("SUCCEEDED".equals(row.get("status"))) return (String) row.get("response");
                if ("FAILED".equals(row.get("status"))) throw error(HttpStatus.valueOf(((Number) row.get("error_status")).intValue()), (String) row.get("error_code"), (String) row.get("error_message"));
                throw pending();
            }
            policy.requireEnabled();
            // A crash before any reservation spent nothing. A crash after reservation requires reconciliation.
            jdbc.update("""
                    UPDATE ai_credit_request r SET status='FAILED',error_status=503,error_code='CHAT_UNAVAILABLE',
                    error_message='The earlier request stopped. Please send a new question.'
                    WHERE user_id=? AND status='RUNNING' AND created_at < ?
                    AND NOT EXISTS (SELECT 1 FROM ai_credit_call c WHERE c.user_id=r.user_id AND c.request_id=r.id AND c.status='RESERVED')
                    """, user, java.sql.Timestamp.from(clock.instant().minusSeconds(120)));
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ai_credit_request WHERE user_id=? AND status IN ('RUNNING','REVIEW'))", Boolean.class, user))) throw pending();
            if (wallet.paused) throw error(HttpStatus.FORBIDDEN, "AI_ACCESS_PAUSED", "Your AI access is paused. Contact your administrator.");
            if (wallet.balance.subtract(wallet.reserved).signum() <= 0) throw insufficient();
            int recent = jdbc.queryForObject("SELECT count(*) FROM ai_credit_request WHERE user_id=? AND created_at >= ?", Integer.class, user, java.sql.Timestamp.from(clock.instant().minusSeconds(60)));
            if (recent >= policy.requestsPerMinute()) throw error(HttpStatus.TOO_MANY_REQUESTS, "AI_RATE_LIMIT", "Please wait a minute before asking another question.");
            jdbc.update("INSERT INTO ai_credit_request(user_id,id,fingerprint,created_at) VALUES (?,?,?,?)", user, id, fingerprint, java.sql.Timestamp.from(clock.instant()));
            return null;
        });
    }
    public UUID reserve(long user, UUID request, BigDecimal amount) {
        return tx.execute(status -> {
            policy.requireEnabled();
            LocalDate day = LocalDate.now(clock.withZone(ZoneOffset.UTC));
            jdbc.update("INSERT INTO ai_credit_budget(day) VALUES (?) ON CONFLICT DO NOTHING", day);
            BigDecimal globalRemaining = jdbc.queryForObject("SELECT ? - spent - reserved FROM ai_credit_budget WHERE day=? FOR UPDATE", BigDecimal.class, policy.dailyLimit(), day);
            Wallet wallet = lockWallet(user);
            if (wallet.paused) throw error(HttpStatus.FORBIDDEN, "AI_ACCESS_PAUSED", "Your AI access is paused. Contact your administrator.");
            if (wallet.balance.subtract(wallet.reserved).compareTo(amount) < 0) throw insufficient();
            if (globalRemaining.compareTo(amount) < 0) throw error(HttpStatus.TOO_MANY_REQUESTS, "AI_DAILY_LIMIT", "The shared daily chat allowance has been reached. Try again after midnight UTC.");
            BigDecimal used = jdbc.queryForObject("SELECT coalesce(sum(coalesce(charged,reserved)),0) FROM ai_credit_call WHERE user_id=? AND request_id=?", BigDecimal.class, user, request);
            if (used.add(amount).compareTo(policy.requestLimit()) > 0) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "AI_QUESTION_LIMIT", "This question needs more than the per-question allowance. Try a shorter question or a new chat.");
            String state = jdbc.queryForObject("SELECT status FROM ai_credit_request WHERE user_id=? AND id=?", String.class, user, request);
            if (!"RUNNING".equals(state)) throw pending();
            UUID call = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO ai_credit_call(id,user_id,request_id,budget_day,model,input_rate,cached_rate,output_rate,reserved,created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """, call, user, request, day, policy.model(), policy.inputRate(), policy.cachedRate(), policy.outputRate(), amount, java.sql.Timestamp.from(clock.instant()));
            jdbc.update("UPDATE ai_credit_wallet SET reserved=reserved+? WHERE user_id=?", amount, user);
            jdbc.update("UPDATE ai_credit_budget SET reserved=reserved+? WHERE day=?", amount, day);
            return call;
        });
    }
    public void settle(UUID call, ExpenseChatModel.Usage usage) {
        if (usage == null) throw error(HttpStatus.SERVICE_UNAVAILABLE, "AI_USAGE_PENDING", "Provider usage could not be verified. Credits are held for administrator review.");
        var row = call(call);
        var tariff = new CreditPolicy((String) row.get("model"), decimal(row,"input_rate"), decimal(row,"cached_rate"), decimal(row,"output_rate"), policy.dailyLimit(), policy.requestLimit(), policy.requestsPerMinute(), true);
        BigDecimal amount;
        try { amount = tariff.cost(usage.inputTokens(), usage.cachedTokens(), usage.outputTokens()); }
        catch (IllegalArgumentException ex) { throw pending(); }
        if (amount.compareTo(decimal(row,"reserved")) > 0) throw pending();
        settleAmount(call, amount, usage, null, "Provider token usage");
    }
    private Map<String,Object> call(UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM ai_credit_call WHERE id=?", id);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "AI_CALL_NOT_FOUND", "Credit reservation not found.");
        return rows.getFirst();
    }
    private void settleAmount(UUID id, BigDecimal amount, ExpenseChatModel.Usage usage, Long actor, String note) {
        tx.executeWithoutResult(status -> {
            var row = call(id);
            LocalDate day = ((java.sql.Date) row.get("budget_day")).toLocalDate();
            jdbc.queryForObject("SELECT spent FROM ai_credit_budget WHERE day=? FOR UPDATE", BigDecimal.class, day);
            long user = ((Number) row.get("user_id")).longValue();
            lockWallet(user);
            row = jdbc.queryForMap("SELECT * FROM ai_credit_call WHERE id=? FOR UPDATE", id);
            if (!"RESERVED".equals(row.get("status"))) {
                if (amount.compareTo(decimal(row,"charged")) != 0)
                    throw error(HttpStatus.CONFLICT,"AI_REQUEST_CONFLICT","This reservation was already settled with a different cost.");
                return;
            }
            BigDecimal reserved = decimal(row,"reserved");
            if (amount.signum() < 0 || amount.compareTo(reserved) > 0) throw error(HttpStatus.BAD_REQUEST, "INVALID_CREDIT_AMOUNT", "Confirmed usage must be between zero and the held amount.");
            jdbc.update("UPDATE ai_credit_wallet SET balance=balance-?,reserved=reserved-? WHERE user_id=?", amount, reserved, user);
            jdbc.update("UPDATE ai_credit_budget SET spent=spent+?,reserved=reserved-? WHERE day=?", amount, reserved, day);
            jdbc.update("""
                    UPDATE ai_credit_call SET status='SETTLED',charged=?,input_tokens=?,cached_tokens=?,output_tokens=? WHERE id=?
                    """, amount, usage == null ? null : usage.inputTokens(), usage == null ? null : usage.cachedTokens(), usage == null ? null : usage.outputTokens(), id);
            jdbc.update("INSERT INTO ai_credit_ledger(user_id,operation_id,amount,kind,actor_id,note) VALUES (?,?,?,'USAGE',?,?)", user, id, amount.negate(), actor, note);
        });
    }
    public void reconcile(UUID id, BigDecimal amount, long actor, String note) {
        validateAmount(amount, true);
        if (note == null || note.isBlank() || note.length() > 300) throw error(HttpStatus.BAD_REQUEST,"INVALID_CREDIT_NOTE","Describe how provider usage was verified (up to 300 characters).");
        var row = call(id);
        if (((java.sql.Timestamp) row.get("created_at")).toInstant().isAfter(clock.instant().minusSeconds(120))) throw pending();
        settleAmount(id, amount, null, actor, note.trim());
        finish(((Number)row.get("user_id")).longValue(), (UUID)row.get("request_id"), null,
                error(HttpStatus.SERVICE_UNAVAILABLE,"AI_REQUEST_REVIEWED","The earlier request was reviewed. You can send a new question."));
    }
    public boolean finish(long user, UUID request, String response, WebApiException failure) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            lockWallet(user);
            String existing = jdbc.queryForObject("SELECT status FROM ai_credit_request WHERE user_id=? AND id=?", String.class, user, request);
            if (List.of("SUCCEEDED","FAILED").contains(existing)) return false;
            boolean held = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ai_credit_call WHERE user_id=? AND request_id=? AND status='RESERVED')", Boolean.class, user, request));
            String state = held ? "REVIEW" : failure == null ? "SUCCEEDED" : "FAILED";
            jdbc.update("UPDATE ai_credit_request SET status=?,response=?,error_code=?,error_message=?,error_status=? WHERE user_id=? AND id=?",
                    state,response,failure == null ? null : failure.code(),failure == null ? null : failure.getMessage(),failure == null ? null : failure.status().value(),user,request);
            return held;
        }));
    }
    public Balance grant(long user, UUID operation, BigDecimal amount, long actor, String note) {
        validateAmount(amount, false);
        if (operation == null || note == null || note.isBlank() || note.length() > 300) throw error(HttpStatus.BAD_REQUEST,"INVALID_CREDIT_GRANT","A request ID and reason (up to 300 characters) are required.");
        return tx.execute(status -> {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app_user WHERE id=?)",Boolean.class,user))) throw error(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","User not found.");
            lockWallet(user);
            var prior = jdbc.queryForList("SELECT amount,kind,note,actor_id FROM ai_credit_ledger WHERE user_id=? AND operation_id=?",user,operation);
            if (!prior.isEmpty()) {
                var p = prior.getFirst();
                if (!"GRANT".equals(p.get("kind")) || amount.compareTo(decimal(p,"amount")) != 0 || !note.trim().equals(p.get("note")) || ((Number)p.get("actor_id")).longValue()!=actor)
                    throw error(HttpStatus.CONFLICT,"AI_REQUEST_CONFLICT","This grant ID was already used.");
                return balance(user);
            }
            jdbc.update("UPDATE ai_credit_wallet SET balance=balance+? WHERE user_id=?",amount,user);
            jdbc.update("INSERT INTO ai_credit_ledger(user_id,operation_id,amount,kind,actor_id,note) VALUES (?,?,?,'GRANT',?,?)",user,operation,amount,actor,note.trim());
            return balance(user);
        });
    }
    public Balance pause(long user, boolean paused, long actor) {
        return tx.execute(status -> {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app_user WHERE id=?)",Boolean.class,user))) throw error(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","User not found.");
            lockWallet(user);
            jdbc.update("UPDATE ai_credit_wallet SET paused=? WHERE user_id=?",paused,user);
            jdbc.update("INSERT INTO ai_credit_ledger(user_id,operation_id,amount,kind,actor_id,note) VALUES (?,?,0,'ACCESS',?,?)",user,UUID.randomUUID(),actor,paused ? "AI access paused" : "AI access resumed");
            return balance(user);
        });
    }
    public List<Map<String,Object>> users(String search) {
        if (search == null || search.length() > 100) throw error(HttpStatus.BAD_REQUEST,"INVALID_USER_SEARCH","Search must be at most 100 characters.");
        return jdbc.queryForList("""
                SELECT u.id,u.channel,u.external_user_id AS "externalUserId",coalesce(w.balance,0) AS balance,
                       coalesce(w.reserved,0) AS reserved,coalesce(w.balance-w.reserved,0) AS available,coalesce(w.paused,false) AS paused
                FROM app_user u LEFT JOIN ai_credit_wallet w ON w.user_id=u.id
                WHERE position(lower(?) in lower(u.external_user_id)) > 0 ORDER BY u.id LIMIT 100
                """,search.trim());
    }
    public List<Map<String,Object>> ledger(long user) {
        return jdbc.queryForList("SELECT amount,kind,note,created_at AS \"createdAt\" FROM ai_credit_ledger WHERE user_id=? ORDER BY id DESC LIMIT 50",user);
    }
    public List<Map<String,Object>> pendingCalls() {
        return jdbc.queryForList("""
                SELECT c.id,c.user_id AS "userId",u.external_user_id AS "externalUserId",c.model,c.reserved,c.created_at AS "createdAt"
                FROM ai_credit_call c JOIN app_user u ON u.id=c.user_id
                WHERE c.status='RESERVED' AND c.created_at < ? ORDER BY c.created_at LIMIT 100
                """,java.sql.Timestamp.from(clock.instant().minusSeconds(120)));
    }
    private static BigDecimal decimal(Map<String,Object> row,String key) { return (BigDecimal)row.get(key); }
    private static void validateAmount(BigDecimal amount, boolean zero) {
        if (amount == null || amount.scale() > 6 || amount.compareTo(BigDecimal.valueOf(1_000_000)) > 0 || (zero ? amount.signum()<0 : amount.signum()<=0))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_CREDIT_AMOUNT","Enter a valid credit amount with at most six decimal places (maximum 1,000,000).");
    }
    private static WebApiException insufficient() { return error(HttpStatus.PAYMENT_REQUIRED,"AI_CREDITS_EXHAUSTED","You don't have enough available AI credits for this question. Start a shorter chat or contact your administrator for more credits."); }
    private static WebApiException pending() { return error(HttpStatus.CONFLICT,"AI_USAGE_PENDING","An earlier question is processing or awaiting usage review. Please wait or contact your administrator."); }
    private record Wallet(BigDecimal balance,BigDecimal reserved,boolean paused) {}
    public record Balance(BigDecimal balance,BigDecimal reserved,BigDecimal available,boolean paused,boolean enabled,boolean configured) {}
}
