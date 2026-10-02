import { test, expect } from '@playwright/test';

for (const phase of ['upcoming', 'due', 'overdue']) {
  const due = phase !== 'upcoming';
  test(`Advance buttons enabled with ${phase} reminders`, async ({ page }) => {
    await page.clock.setFixedTime(new Date(phase==='overdue'?'2026-10-06T09:00:00Z':due ? '2026-10-05T09:00:00Z' : '2026-10-01T09:00:00Z'));
    const occurrence = { month: '2026-10', dueDate: '2026-10-05', status: due ? 'DUE' : 'UPCOMING', actionAvailable: true };
    const commitment = { id: 1, label: 'Internet bill', status: 'ACTIVE', planningAmount: 1000, recurrenceUnit: 'MONTH', recurrenceInterval: 1, nextExpectedDate: '2026-10-05', currentOccurrence: occurrence };
    const loan = { id: 2, loanName: 'Home loan', loanType: 'HOME', lenderName: 'Bank', firstEmiDueDate: '2026-10-05', totalTenureMonths: 12, originalPrincipal: 120000, monthlyEmiAmount: 10000, status: 'ACTIVE', emiOccurrences: [{ ...occurrence, plannedAmount: 10000 }] };
    const fund = { id: 3, schemeName: 'Index fund', invested: 0, currentValue: 0, profitOrLoss: 0, profitOrLossPercent: 0, activeSip: { amount: 1000, day: 5, startMonth: '2026-10', nextDueDate: '2026-10-05' }, currentSip: { scheduledMonth: '2026-10', status: due ? 'DUE' : 'SCHEDULED', actionAvailable: true } };
    const stock = { id: 4, name: 'ETF plan', symbol: 'ETF', invested: 0, quantity: 0, currentValue: 0, profitOrLoss: 0, profitOrLossPercent: 0, activeMonthlyPlan: { amount: 1000, day: 5, startMonth: '2026-10', status: 'ACTIVE' }, currentMonthlyPlan: { month: '2026-10', status: due ? 'DUE' : 'SCHEDULED', actionAvailable: true } };
    await page.route('**/api/web/**', route => {
      const path = new URL(route.request().url()).pathname;
      const json = path.endsWith('/demo-profile') ? { demoMode: false, canUseDemoMode: false }
        : path.endsWith('/calendar') ? { currency: 'INR', timezone: 'Asia/Kolkata', totalSpend: 0, transactionCount: 0, days: [] }
        : path.endsWith('/monthly-commitment') ? { commitment: null }
        : path.endsWith('/recurring-commitments') ? { items: [commitment] }
        : path.endsWith('/recurring-commitments/1/history') ? { id: 1, label: 'Internet bill', planningAmount: 1000, history: [] }
        : path.endsWith('/recurring-commitments/savings') ? []
        : path.endsWith('/loans') ? { loans: [loan] }
        : path.endsWith('/loans/2/history') ? { id: 2, loanName: 'Home loan', history: loan.emiOccurrences }
        : path.endsWith('/mutual-funds') ? { mutualFunds: [fund] }
        : path.endsWith('/mutual-funds/3') ? { ...fund, units: 0, averageNav: 0, currentNav: 0, history: [] }
        : path.endsWith('/stocks') ? { stocks: [stock] }
        : path.endsWith('/stocks/4') ? { stock, history: [] }
        : { items: [], actions: [], loans: [], mutualFunds: [], stocks: [] };
      return route.fulfill({ json });
    });
    await page.route('**/health', route => route.fulfill({ json: { status: 'UP' } }));
    await page.goto('/dashboard?month=2026-10');
    const day=page.locator('.unified-calendar .calendar button').filter({ has: page.locator('span').filter({ hasText: /^5$/ }) });
    if(phase==='overdue') {
      await expect(day).toHaveClass(/overdue/);
      await expect(day).toHaveAttribute('aria-label', /4 overdue payments/);
      await expect(day).toHaveCSS('background-color', 'rgb(246, 216, 211)');
    } else await expect(day).not.toHaveClass(/overdue/);
    for (const [label, marker, pay, skip] of [
      ['Internet bill', 'COMMITMENT DETAILS', 'Paid', 'Skip'],
      ['Home loan', 'LOAN DETAILS', 'Pay October EMI', 'Skip October EMI'],
      ['Index fund', 'FUND DETAILS', 'Pay SIP', 'Skip SIP'],
      ['ETF plan', 'ETF plan', 'Confirm allocation', 'Skip purchase']
    ]) {
      const row = page.getByRole('region', { name: 'Activity', exact: true }).locator('.unified-activity-row').filter({ hasText: label });
      await expect(row).toBeVisible();
      await expect(row).toContainText(due ? 'Due now' : 'Upcoming');
      if (due) await expect(row).toHaveClass(/due-commitment/);
      else {
        await expect(row).not.toContainText('Due now');
        await expect(row).not.toHaveClass(/due-commitment/);
      }
      await row.getByRole('button', { name: 'Review', exact: true }).click();
      const details = page.getByRole('dialog').filter({ hasText: marker }).last();
      await expect(details.getByRole('button', { name: pay, exact: true })).toBeEnabled();
      await expect(details.getByRole('button', { name: skip, exact: true })).toBeEnabled();
      if (!due) {
        await expect(details).not.toContainText('Due now');
        await expect(details.locator('.due-sip, .due')).toHaveCount(0);
      }
      await details.locator('.close').first().click();
      await page.locator('.money-modal > .close').click();
    }
  });
}

test('Overdue calendar warning clears only after every payment on the day is resolved', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-10-07T09:00:00Z'));
  let resolved = 0;
  const occurrence = (status) => ({ month: '2026-10', dueDate: '2026-10-05', status, actionAvailable: status==='DUE' });
  await page.route('**/api/web/**', route => {
    const path = new URL(route.request().url()).pathname;
    const json = path.endsWith('/demo-profile') ? { demoMode: false, canUseDemoMode: false }
      : path.endsWith('/calendar') ? { currency: 'INR', timezone: 'Asia/Kolkata', totalSpend: 500, transactionCount: 1, days: [{ date: '2026-10-05', totalSpend: 500, transactionCount: 1, intensity: 4 }] }
      : path.endsWith('/monthly-commitment') ? { commitment: null }
      : path.endsWith('/recurring-commitments') ? { items: [{ id: 1, label: 'Internet bill', status: 'ACTIVE', planningAmount: 1000, recurrenceUnit: 'MONTH', recurrenceInterval: 1, nextExpectedDate: '2026-10-05', currentOccurrence: occurrence(resolved ? 'COMPLETED' : 'DUE') }] }
      : path.endsWith('/loans') ? { loans: [{ id: 2, loanName: 'Home loan', loanType: 'HOME', lenderName: 'Bank', firstEmiDueDate: '2026-10-05', totalTenureMonths: 12, originalPrincipal: 120000, monthlyEmiAmount: 10000, status: 'ACTIVE', emiOccurrences: [{ ...occurrence(resolved===2?'SKIPPED':'DUE'), plannedAmount: 10000 }] }] }
      : path.endsWith('/recurring-commitments/savings') ? []
      : { items: [], actions: [], loans: [], mutualFunds: [], stocks: [] };
    return route.fulfill({ json });
  });
  await page.route('**/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.goto('/dashboard?month=2026-10');
  const day = page.locator('.unified-calendar .calendar button').filter({ has: page.locator('span').filter({ hasText: /^5$/ }) });
  await expect(day).toHaveAttribute('aria-label', /2 overdue payments/);
  await expect(day).toHaveCSS('background-color', 'rgb(246, 216, 211)');
  await day.click();
  const activity=page.getByRole('region', { name: 'Activity', exact: true });
  await expect(activity).toContainText('Internet bill');
  await expect(activity).toContainText('Home loan');
  resolved=1;
  await page.reload();
  await expect(day).toHaveAttribute('aria-label', /1 overdue payments/);
  await expect(day).toHaveClass(/overdue/);
  resolved=2;
  await page.reload();
  await expect(day).not.toHaveClass(/overdue/);
  await expect(day).toHaveClass(/level-4/);
  await expect(day).toHaveCSS('background-color', 'rgb(39, 106, 80)');
});
