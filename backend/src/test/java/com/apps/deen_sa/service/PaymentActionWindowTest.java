package com.apps.deen_sa.service;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class PaymentActionWindowTest {
 private final PaymentActionWindow window = new PaymentActionWindow();
 @Test void boundaries() {
  LocalDate due=LocalDate.of(2026,10,15);
  assertFalse(window.available(due,due.minusDays(6)));
  assertTrue(window.available(due,due.minusDays(5)));
  assertFalse(window.due(due,due.minusDays(5)));
  assertTrue(window.due(due,due));
  assertTrue(window.available(due,due));
  assertTrue(window.available(due,LocalDate.of(2026,10,31)));
  assertFalse(window.available(due,LocalDate.of(2026,11,1)));
  assertFalse(window.available(LocalDate.of(2026,10,1),LocalDate.of(2026,9,28)));
  assertTrue(window.available(LocalDate.of(2026,10,5),LocalDate.of(2026,10,1)));
  assertFalse(window.available(LocalDate.of(2027,1,1),LocalDate.of(2026,12,31)));
  assertFalse(window.available(null,due));
 }
 @Test void configuration() {
  LocalDate due=LocalDate.of(2026,10,15);
  ReflectionTestUtils.setField(window,"advanceDays",2);
  assertFalse(window.available(due,due.minusDays(3)));
  assertTrue(window.available(due,due.minusDays(2)));
  ReflectionTestUtils.setField(window,"advanceDays",0);
  assertFalse(window.available(due,due.minusDays(1)));
  assertTrue(window.available(due,due));
 }
}
