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
  await expect(overview.getByRole('button', { name: /Recorded expenses.*₹0/ })).toBeVisible();
  await expect(overview.getByRole('button', { name: /Planned commitments.*₹1,000/ })).toBeVisible();
  await expect(overview.getByText('Planned commitments are upcoming amounts, not money already spent.')).toBeVisible();
  await expect(page.locator('.story-carousel-card')).toHaveCount(0);
  const activity = page.locator('.home-section').filter({ has: page.getByRole('heading', { name: 'Everything recorded' }) });
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
      : path.endsWith('/loans') ? { loans: [{ id: 2, loanName: 'Home loan', status: 'ACTIVE', monthlyEmiAmount: 10000, emiOccurrences: [{ dueDate: '2026-09-05', status: 'DUE', plannedAmount: 10000 }] }] }
      : path.endsWith('/mutual-funds') ? { mutualFunds: [{ id: 3, schemeName: 'Index fund', activeSip: { day: 5, amount: 2000, nextDueDate: '2026-09-05' }, currentSip: { scheduledMonth: '2026-09', status: 'DUE' } }] }
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
});
