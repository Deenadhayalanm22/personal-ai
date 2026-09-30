import { expect, test } from '@playwright/test';

test('Home separates recorded expenses from planned commitments in one overview', async ({ page }) => {
  await page.route('**/api/web/**', route => {
    const path = new URL(route.request().url()).pathname;
    const json = path.endsWith('/demo-profile') ? { demoMode: false, canUseDemoMode: false }
      : path.endsWith('/calendar') ? { currency: 'INR', totalSpend: 0, transactionCount: 0, days: [] }
      : path.endsWith('/monthly-commitment') ? { commitment: {
          storyId: 'monthly-commitment', storyType: 'MONTHLY_COMMITMENT',
          cardFace: { heading: 'Monthly commitment', displayValue: '₹1,000' }
        } } : { items: [], actions: [] };
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
});
