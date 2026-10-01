import { expect, test } from '@playwright/test';

test('Home separates recorded expenses from planned commitments in one overview', async ({ page }) => {
  await page.route('**/api/web/**', route => {
    const path = new URL(route.request().url()).pathname;
    const json = path.endsWith('/demo-profile') ? { demoMode: false, canUseDemoMode: false }
      : path.endsWith('/calendar') ? { currency: 'INR', totalSpend: 0, transactionCount: 0, days: [] }
      : path.endsWith('/monthly-commitment') ? { commitment: {
          storyId: 'monthly-commitment', storyType: 'MONTHLY_COMMITMENT',
          cardFace: { heading: 'Monthly commitment', displayValue: '₹1,000' },
          cards: [{ cardId: 'commitment', sequence: 1, layout: 'COMMITMENT', title: 'Your monthly plan', body: 'One item completed.', components: [
            { type: 'MONEY', label: 'Essential living', value: 1000, currency: 'INR', displayValue: '₹1,000' },
            { type: 'MONEY', label: 'Recurring commitments complete', value: 1000, currency: 'INR', displayValue: '₹1,000' }
          ], actions: [] }]
        } } : path.endsWith('/activity') ? { items: [
          { type: 'COMMITMENT', id: 7, label: 'Internet bill', description: 'Commitment paid', amount: 5000, date: '2026-09-05' },
          { type: 'INVESTMENT', id: 8, label: 'Index fund', description: 'Investment recorded', amount: 2000, date: '2026-09-04' }
        ] } : { items: [], actions: [] };
    return route.fulfill({ json });
  });
  await page.route('**/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.goto('/dashboard?month=2026-09');
  await expect(page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: 'Stories' })).toHaveCount(0);
  await expect(page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: 'Transactions' })).toHaveCount(0);
  const overview = page.getByRole('region', { name: 'This month' });
  await expect(overview.getByRole('button', { name: /Spending & commitments/ })).toBeVisible();
  await overview.getByRole('button', { name: /Spending & commitments/ }).click();
  await expect(overview.getByText('Recorded expenses', { exact: true })).toBeVisible();
  await expect(overview.getByRole('button', { name: /Planned commitments.*₹1,000/ })).toBeVisible();
  await expect(overview.getByText('Planned commitments are upcoming amounts, not money already spent.')).toBeVisible();
  await expect(page.locator('.story-carousel-card')).toHaveCount(0);
  const activity = page.getByRole('region', { name: 'Activity', exact: true });
  await expect(activity).toContainText('Internet bill');
  await expect(activity).toContainText('Commitment paid');
  await expect(activity).toContainText('Index fund');
  await expect(activity).toContainText('Investment recorded');
  await overview.getByRole('button', { name: /Planned commitments/ }).click();
  await expect(page.getByRole('progressbar', { name: 'Essential living: completed' })).toHaveAttribute('aria-valuenow', '100');
});

test('Home calendar shows planned dots and date activity across financial sources', async ({ page }) => {
  await page.route('**/api/web/**', route => {
    const path = new URL(route.request().url()).pathname;
    const json = path.endsWith('/demo-profile') ? { demoMode: false, canUseDemoMode: false }
      : path.endsWith('/calendar') ? { currency: 'INR', totalSpend: 300, transactionCount: 1, days: [{ date: '2026-09-05', totalSpend: 300, transactionCount: 1, intensity: 3 }] }
      : path.endsWith('/recurring-commitments') ? { items: [{ id: 1, label: 'Chit fund', status: 'ACTIVE', planningAmount: 5000, nextExpectedDate: '2026-09-05', currentOccurrence: { dueDate: '2026-09-05', status: 'DUE' } }] }
      : path.endsWith('/loans') ? { loans: [{ id: 2, loanName: 'Home loan', loanType: 'HOME', lenderName: 'Bank', firstEmiDueDate: '2026-09-05', totalTenureMonths: 12, status: 'ACTIVE', monthlyEmiAmount: 10000, emiOccurrences: [{ dueDate: '2026-09-05', status: 'DUE', plannedAmount: 10000 }] }] }
      : path.endsWith('/mutual-funds') ? { mutualFunds: [{ id: 3, schemeName: 'Index fund', activeSip: { day: 5, amount: 2000, nextDueDate: '2026-09-05' }, currentSip: { scheduledMonth: '2026-09', status: 'DUE' } }] }
      : path.endsWith('/mutual-funds/3') ? { id: 3, schemeName: 'Index fund', history: [], units: 10, invested: 2000, currentValue: 2000, currentNav: 200, averageNav: 200, profitOrLoss: 0, profitOrLossPercent: 0 }
      : path.endsWith('/recurring-commitments/savings') ? []
      : path.endsWith('/activity') ? { items: [{ type: 'EXPENSE', id: 4, label: 'Groceries', description: 'Recorded expense', amount: 300, date: '2026-09-05' }] }
      : { items: [], actions: [] };
    return route.fulfill({ json });
  });
  await page.route('**/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.goto('/dashboard?month=2026-09');
  const day = page.getByRole('button', { name: '2026-09-05: 1 recorded expenses, 3 planned payments' });
  await expect(day).toHaveClass(/level-3/);
  await expect(day.locator('.plan-dot')).toHaveCount(1);
  await day.click();
  const activity = page.locator('.calendar-activity');
  await expect(activity).toContainText('Chit fund');
  await expect(activity).toContainText('Home loan');
  await expect(activity).toContainText('Index fund');
  await expect(activity).toContainText('Groceries');
  await activity.getByRole('button', { name: 'Review Index fund', exact: true }).click();
  const fundDetails = page.getByRole('dialog').filter({ hasText: 'FUND DETAILS' });
  await expect(fundDetails).toContainText('Index fund');
  await expect(fundDetails.getByRole('button', { name: 'Pay SIP', exact: true })).toBeEnabled();
});

test('Today prioritizes dues and reviewing a commitment records its payment in the same activity list', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-10-02T09:00:00Z'));
  let paid = false;
  const commitment = () => ({ id: 1, label: 'Chit fund', status: 'ACTIVE', planningAmount: 5000, recurrenceUnit: 'MONTH', recurrenceInterval: 1, nextExpectedDate: paid ? '2026-11-01' : '2026-10-01', currentOccurrence: { month: '2026-10', dueDate: '2026-10-01', status: paid ? 'COMPLETED' : 'DUE' } });
  const loan = { id: 2, loanName: 'Home loan', loanType: 'HOME', lenderName: 'Bank', firstEmiDueDate: '2026-10-01', totalTenureMonths: 12, originalPrincipal: 120000, status: 'ACTIVE', monthlyEmiAmount: 10000, emiOccurrences: [{ month: '2026-10', dueDate: '2026-10-01', status: 'DUE', plannedAmount: 10000 }] };
  await page.route('**/api/web/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/occurrences/complete')) {
      expect(route.request().postDataJSON()).toMatchObject({ actualAmount: 5000, completedAt: '2026-10-02' });
      paid = true;
      return route.fulfill({ json: commitment() });
    }
    const json = path.endsWith('/demo-profile') ? { demoMode: false, canUseDemoMode: false }
      : path.endsWith('/calendar') ? { currency: 'INR', timezone: 'Asia/Kolkata', totalSpend: 300, transactionCount: 1, days: [] }
      : path.endsWith('/recurring-commitments') ? { items: [commitment()] }
      : path.endsWith('/recurring-commitments/savings') ? []
      : path.endsWith('/recurring-commitments/1/history') ? { id: 1, label: 'Chit fund', planningAmount: 5000, history: [] }
      : path.endsWith('/loans') ? { loans: [loan] }
      : path.endsWith('/loans/2/history') ? { id: 2, loanName: 'Home loan', history: loan.emiOccurrences }
      : path.endsWith('/activity') ? { items: [
        ...(paid ? [{ type: 'COMMITMENT', id: 10, label: 'Chit fund', description: 'Commitment paid', amount: 5000, date: '2026-10-02' }] : []),
        { type: 'EXPENSE', id: 4, label: 'Groceries', description: 'Recorded expense', amount: 300, date: '2026-10-02' },
        { type: 'EXPENSE', id: 5, label: 'Coffee', description: 'Recorded expense', amount: 100, date: '2026-10-01' }
      ] } : { items: [], actions: [], mutualFunds: [], stocks: [] };
    return route.fulfill({ json });
  });
  await page.route('**/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.goto('/dashboard?month=2026-10');
  const activity = page.getByRole('region', { name: 'Activity', exact: true });
  await expect(activity.getByRole('heading', { name: 'Today', exact: true })).toBeVisible();
  const rows = activity.locator('.unified-activity-row');
  await expect(rows).toHaveCount(4);
  await expect(rows.nth(0)).toContainText('Due now');
  await expect(rows.nth(1)).toContainText('Due now');
  await expect(rows.nth(2)).toContainText('Groceries');
  await expect(rows.nth(3)).toContainText('Coffee');
  const chit = rows.filter({ hasText: 'Chit fund' });
  await expect(chit).toHaveCSS('border-top-color', 'rgb(228, 164, 156)');
  await chit.getByRole('button', { name: 'Review', exact: true }).click();
  const details = page.getByRole('dialog').filter({ hasText: 'COMMITMENT DETAILS' });
  await expect(details).toContainText('Chit fund');
  await details.getByRole('button', { name: 'Paid', exact: true }).click();
  const completion = page.getByRole('dialog').filter({ hasText: 'COMPLETION' });
  await completion.getByLabel('Completed on').fill('2026-10-02');
  await completion.getByRole('button', { name: 'Save completion' }).click();
  await page.locator('.money-modal > .close').click();
  await expect(rows.filter({ hasText: 'Chit fund' })).toHaveCount(1);
  await expect(chit).toContainText('Commitment paid');
  await expect(chit).not.toHaveClass(/due-commitment/);
  await expect(rows.first()).toContainText('Home loan');
  await rows.first().getByRole('button', { name: 'Review Home loan' }).click();
  await expect(page.getByRole('dialog').filter({ hasText: 'LOAN DETAILS' })).toContainText('Home loan');
});
