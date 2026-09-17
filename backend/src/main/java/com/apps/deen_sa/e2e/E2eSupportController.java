package com.apps.deen_sa.e2e;

import com.apps.deen_sa.config.E2eClockConfiguration.AdjustableClock;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.WebSessionEntity;
import com.apps.deen_sa.repository.AppUserRepository;
import com.apps.deen_sa.repository.WebSessionRepository;
import com.apps.deen_sa.service.LoanClosureReminderService;
import com.apps.deen_sa.service.MagicLinkService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

/** E2E-only fixture API. This class is unavailable unless SPRING_PROFILES_ACTIVE=e2e. */
@RestController @Profile("e2e") @RequestMapping("/test/e2e")
public class E2eSupportController {
  private final AppUserRepository users; private final WebSessionRepository sessions;
  private final LoanClosureReminderService reminders; private final AdjustableClock clock;
  public E2eSupportController(AppUserRepository users, WebSessionRepository sessions, LoanClosureReminderService reminders, AdjustableClock clock) { this.users=users;this.sessions=sessions;this.reminders=reminders;this.clock=clock; }
  @PostMapping("/session") @Transactional public Grant session() {
    clock.set(Instant.parse("2026-04-15T09:00:00Z"));
    Instant now=clock.instant(); AppUserEntity user=new AppUserEntity(); user.setChannel("E2E");user.setExternalUserId(UUID.randomUUID().toString());user.setCreatedAt(now); user=users.save(user);
    String token=UUID.randomUUID().toString(); WebSessionEntity session=new WebSessionEntity();session.setUserId(user.getId());session.setTokenHash(MagicLinkService.hash(token));session.setCreatedAt(now);session.setExpiresAt(now.plus(java.time.Duration.ofDays(365)));sessions.save(session); return new Grant(token);
  }
  @PostMapping("/clock") public void setClock(@org.springframework.web.bind.annotation.RequestBody ClockRequest request) { clock.set(Instant.parse(request.instant())); reminders.createDueReminders(); }
  public record Grant(String sessionToken) {}
  public record ClockRequest(String instant) {}
}
