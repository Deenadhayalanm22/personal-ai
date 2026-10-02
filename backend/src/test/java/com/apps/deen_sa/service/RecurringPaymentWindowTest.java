package com.apps.deen_sa.service;
import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RecurringPaymentWindowTest {
 @Test void monthlyAndWeeklyOccurrencesShareAdvanceWindow() {
  for(CommitmentRecurrenceUnit unit: List.of(CommitmentRecurrenceUnit.MONTH,CommitmentRecurrenceUnit.WEEK)) {
   AppUserEntity user=new AppUserEntity(); user.setId(1L);
   UserRecurringCommitmentEntity commitment=new UserRecurringCommitmentEntity();
   commitment.setId(2L); commitment.setUser(user); commitment.setLabel("Support");
   commitment.setAmountMode(CommitmentAmountMode.FIXED); commitment.setPlanningAmount(BigDecimal.TEN);
   commitment.setEffectiveMonth(LocalDate.of(2026,10,1)); commitment.setDueDay(5);
   commitment.setRecurrenceUnit(unit); commitment.setFirstExpectedDate(LocalDate.of(2026,10,5));
   commitment.setNextExpectedDate(LocalDate.of(2026,10,5));
   commitment.setCreatedAt(Instant.parse("2026-09-01T00:00:00Z"));
   var rules=mock(UserRecurringCommitmentRepository.class);
   when(rules.findAllOwned(1L)).thenReturn(List.of(commitment));
   var service=new WebRecurringCommitmentService(rules,null,null,mock(RecurringCommitmentOccurrenceRepository.class),null,
      Clock.fixed(Instant.parse("2026-10-01T09:00:00Z"),ZoneId.of("Asia/Kolkata")),null);
   var response=service.list(user).items().get(0);
   assertEquals("UPCOMING",response.currentOccurrence().status());
   assertTrue(response.currentOccurrence().actionAvailable());
   assertEquals(LocalDate.of(2026,10,5),response.currentOccurrence().dueDate());
   if(unit==CommitmentRecurrenceUnit.WEEK) assertEquals("UPCOMING",response.currentOccurrences().get(1).status());
  }
 }
}
