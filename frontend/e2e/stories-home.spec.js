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
  const overview = page.getByRole('region', { name: 'This month' });
  await expect(overview.getByRole('heading', { name: 'Spending & commitments' })).toBeVisible();
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
