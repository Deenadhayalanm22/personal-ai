import { expect, test } from '@playwright/test';

test('Home shares one compact card with calendar before spending and unpaid bills', async ({ page }) => {
  await page.route('**/api/web/**', route => {
    const path = new URL(route.request().url()).pathname;
    const json = path.endsWith('/demo-profile') ? { demoMode: false, canUseDemoMode: false }
      : path.endsWith('/calendar') ? { currency: 'INR', totalSpend: 5000, commitmentSpend: 5000, transactionCount: 1, days: [] }
      : path.endsWith('/monthly-commitment') ? { overview: { stillToPay: 2000, plannedInvesting: 2000, plannedSavings: 0 }, commitment: {
          storyId: 'monthly-commitment', storyType: 'MONTHLY_COMMITMENT',
          cardFace: { heading: 'Monthly commitment', displayValue: '₹1,000' },
          evidence: { byCard: { 'next-commitment': { totalAmount: { displayValue: '₹1,500' } } } },
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
  const overview = page.getByRole('region', { name: 'Month overview' });
  const monthCard = page.getByRole('region', { name: 'Month at a glance', exact: true });
  await expect(monthCard.getByRole('heading', { name: 'Calendar', exact: true })).toBeVisible();
  await expect(monthCard.getByLabel('Calendar month')).toHaveValue('2026-09');
  await expect(page.locator('.app-heading')).not.toContainText('September 2026');
  await expect(page.getByText('Here’s how your month is unfolding.', { exact: true })).toHaveCount(0);
  await expect(page.getByText('MONTH AT A GLANCE', { exact: true })).toHaveCount(0);
  await expect(overview.locator('[aria-expanded]')).toHaveCount(0);
  await expect(overview).toContainText('₹2,000');
  await expect(overview).toContainText('Includes investing ₹2,000');
  await expect(overview).toContainText('Includes ₹5,000 in commitment payments');
  await expect(overview).not.toContainText('₹1,500');
  await expect(overview.getByText('Spent this month', { exact: true })).toBeVisible();
  await expect(overview.getByText('Still to pay this month', { exact: true })).toBeVisible();
  await expect(overview).toContainText('₹5,000');
  expect(await overview.evaluate(widget => {
    const card = widget.closest('.month-at-a-glance');
    const calendar = card.querySelector('.unified-calendar');
    return !!(calendar.compareDocumentPosition(widget) & Node.DOCUMENT_POSITION_FOLLOWING)
      && getComputedStyle(calendar).borderTopWidth === '0px'
      && getComputedStyle(card).borderTopWidth === '1px';
  })).toBe(true);
  await page.locator('.calendar button').first().click();
  await expect(overview).toContainText('₹5,000');
  await page.getByRole('button', { name: 'Back to today' }).click();
  await page.setViewportSize({ width: 320, height: 720 });
  expect(await monthCard.evaluate(widget => {
    const bounds = widget.getBoundingClientRect();
    return widget.scrollWidth <= widget.clientWidth && bounds.left >= 0 && bounds.right <= window.innerWidth;
  })).toBe(true);
  await page.setViewportSize({ width: 1280, height: 720 });
  await expect(page.locator('.story-carousel-card')).toHaveCount(0);
  const activity = page.getByRole('region', { name: 'Activity', exact: true });
  await expect(activity).toContainText('Internet bill');
  await expect(activity).toContainText('Commitment paid');
  await expect(activity).toContainText('Index fund');
  await expect(activity).toContainText('Investment recorded');
  await overview.getByRole('button', { name: 'View monthly plan' }).click();
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
      : path.endsWith('/calendar') ? { currency: 'INR', timezone: 'Asia/Kolkata', totalSpend: paid ? 5300 : 300, commitmentSpend: paid ? 5000 : 0, transactionCount: paid ? 2 : 1, days: [] }
      : path.endsWith('/monthly-commitment') ? { commitment: { storyType: 'MONTHLY_COMMITMENT', cardFace: { heading: 'Monthly plan', displayValue: '₹15,000' } }, overview: { stillToPay: paid ? 10000 : 15000, plannedInvesting: 0, plannedSavings: 0 } }
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
  const summary = page.getByRole('region', { name: 'Month overview' });
  await expect(summary).toContainText('₹5,300');
  await expect(summary).toContainText('₹10,000');
  await expect(rows.first()).toContainText('Home loan');
  await rows.first().getByRole('button', { name: 'Review Home loan' }).click();
  await expect(page.getByRole('dialog').filter({ hasText: 'LOAN DETAILS' })).toContainText('Home loan');
});

test('Historical month overview omits unavailable planning figures', async ({ page }) => {
  await page.route('**/api/web/**', route => {
    const path = new URL(route.request().url()).pathname;
    const json = path.endsWith('/demo-profile') ? { demoMode: false, canUseDemoMode: false }
      : path.endsWith('/calendar') ? { currency: 'INR', totalSpend: 850, days: [] }
      : path.endsWith('/monthly-commitment') ? { commitment: null }
      : { items: [], actions: [] };
    return route.fulfill({ json });
  });
  await page.route('**/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.goto('/dashboard?month=2026-08');
  const overview = page.getByRole('region', { name: 'Month overview' });
  await expect(overview).toContainText('₹850');
  await expect(overview.getByText('Still to pay this month')).toHaveCount(0);
  await expect(overview.getByText(/Next month planned/)).toHaveCount(0);
  await expect(overview.getByRole('button', { name: 'View monthly plan' })).toHaveCount(0);
});

test('Paid can attach an expense already recorded without increasing spending again', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-10-01T09:00:00Z'));
  let paid = false;
  const item = () => ({ id: 1, label: 'Internet bill', status: 'ACTIVE', planningAmount: 1000, recurrenceUnit: 'MONTH', recurrenceInterval: 1, nextExpectedDate: paid ? '2026-11-01' : '2026-10-01', currentOccurrence: { month: '2026-10', dueDate: '2026-10-01', status: paid ? 'COMPLETED' : 'DUE' } });
  await page.route('**/api/web/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/occurrences/complete')) {
      expect(route.request().postDataJSON()).toMatchObject({ actualAmount: 900, completedAt: '2026-10-01', transactionId: 70 });
      paid = true;
      return route.fulfill({ json: { transactionId: 70 } });
    }
    const json = path.endsWith('/demo-profile') ? { demoMode: false, canUseDemoMode: false }
      : path.endsWith('/calendar') ? { currency: 'INR', timezone: 'Asia/Kolkata', totalSpend: 900, commitmentSpend: paid ? 900 : 0, transactionCount: 1, days: [] }
      : path.endsWith('/monthly-commitment') ? { commitment: { storyType: 'MONTHLY_COMMITMENT', cardFace: { heading: 'Monthly plan', displayValue: '₹1,000' } }, overview: { stillToPay: paid ? 0 : 1000, plannedInvesting: 0, plannedSavings: 0 } }
      : path.endsWith('/recurring-commitments') ? { items: [item()] }
      : path.endsWith('/recurring-commitments/1/history') ? { id: 1, label: 'Internet bill', planningAmount: 1000, history: [] }
      : path.endsWith('/recurring-commitments/savings') ? []
      : path.endsWith('/expenses') ? { items: [{ id: 70, amount: 900, merchant: 'Internet provider', transactionTime: '2026-10-01T00:00:00Z', commitmentPayment: paid }] }
      : path.endsWith('/activity') ? { items: [{ type: paid ? 'COMMITMENT' : 'EXPENSE', id: 70, label: 'Internet provider', description: paid ? 'Commitment paid' : 'Recorded expense', amount: 900, date: '2026-10-01' }] }
      : { items: [], loans: [], mutualFunds: [], stocks: [], actions: [] };
    return route.fulfill({ json });
  });
  await page.route('**/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.goto('/dashboard?month=2026-10');
  const summary = page.getByRole('region', { name: 'Month overview' });
  await expect(summary).toContainText('₹900');
  await page.getByRole('button', { name: 'Review Internet bill', exact: true }).click();
  await page.getByRole('dialog').filter({ hasText: 'COMMITMENT DETAILS' }).getByRole('button', { name: 'Paid', exact: true }).click();
  const completion = page.getByRole('dialog').filter({ hasText: 'COMPLETION' });
  await completion.getByLabel('Payment record').selectOption('70');
  await expect(completion.getByLabel('Actual amount')).toHaveValue('900');
  await expect(completion).toContainText('without adding spending again');
  await completion.getByRole('button', { name: 'Save completion' }).click();
  await page.locator('.money-modal > .close').click();
  await expect(summary).toContainText('Includes ₹900 in commitment payments');
  await expect(summary.locator('.month-overview-stat').nth(1)).toContainText('₹0');
  await expect(page.getByRole('region', { name: 'Activity', exact: true }).locator('.unified-activity-row')).toHaveCount(1);
});

test('Stock monthly plans appear in calendar and due activity before opening Money', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-10-02T09:00:00Z'));
  const stock = (id, status, day = 1, startMonth = '2026-10') => ({ id, name: `ETF ${id}`, symbol: `ETF${id}`, quantity: 10, invested: 1000, latestPrice: 100, currentValue: 1000, profitOrLoss: 0, profitOrLossPercent: 0,
    activeMonthlyPlan: { amount: 10000, day, startMonth, status: 'ACTIVE' }, currentMonthlyPlan: { month: '2026-10', status, amount: 10000 } });
  const stocks = [stock(1, 'DUE'), stock(2, 'CONFIRMED'), stock(3, 'SKIPPED'), stock(4, 'SCHEDULED', 7), stock(5, 'SCHEDULED', 1, '2026-11')];
  await page.route('**/api/web/**', route => {
    const path = new URL(route.request().url()).pathname;
    const json = path.endsWith('/demo-profile') ? { demoMode: false, canUseDemoMode: false }
      : path.endsWith('/calendar') ? { currency: 'INR', totalSpend: 0, transactionCount: 0, days: [] }
      : path.endsWith('/stocks/1') ? { stock: stocks[0], history: [] }
      : path.endsWith('/stocks') ? { stocks }
      : { items: [], loans: [], mutualFunds: [], actions: [] };
    return route.fulfill({ json });
  });
  await page.route('**/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.goto('/dashboard?month=2026-10');
  const activity = page.getByRole('region', { name: 'Activity', exact: true });
  await expect(activity).toContainText('ETF 1');
  await expect(activity).toContainText('Stock monthly plan · Due now');
  await expect(activity).toContainText('₹10,000');
  for (const id of [2, 3, 4, 5]) await expect(activity.getByRole('button', { name: `Review ETF ${id}`, exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: /2026-10-01:.*2 planned payments/ }).click();
  await expect(activity.getByRole('button', { name: 'Review ETF 1', exact: true })).toBeVisible();
  await activity.getByRole('button', { name: 'Review ETF 1', exact: true }).click();
  const detail = page.getByRole('dialog').filter({ has: page.getByRole('heading', { name: 'ETF 1', exact: true }) });
  await expect(detail.getByRole('button', { name: 'Confirm allocation' })).toBeEnabled();
  await detail.getByRole('button', { name: '×', exact: true }).click();
  await page.locator('.money-modal > .close').click();
  await page.getByRole('button', { name: /2026-10-07:.*1 planned payments/ }).click();
  await expect(activity.getByRole('button', { name: 'Review ETF 4', exact: true })).toBeVisible();
  await expect(activity).toContainText('Stock monthly plan · Upcoming');
});
