import { expect, test } from '@playwright/test';

test('all stories stay on Home without a separate Stories menu', async ({ page }) => {
  const stories = Array.from({ length: 4 }, (_, index) => ({
    storyId: `story-${index + 1}`,
    source: 'Spending',
    cardFace: { heading: `Story ${index + 1}`, displayValue: `₹${index + 1}00` }
  }));
  await page.route('**/api/web/**', route => {
    const path = new URL(route.request().url()).pathname;
    const json = path.endsWith('/demo-profile') ? { demoMode: false, canUseDemoMode: false }
      : path.endsWith('/calendar') ? { currency: 'INR', totalSpend: 0, transactionCount: 0, days: [] }
      : path.endsWith('/monthly') ? { stories } : { items: [], actions: [] };
    return route.fulfill({ json });
  });
  await page.route('**/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.goto('/dashboard?month=2026-09');
  await expect(page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: 'Stories' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'See all' })).toHaveCount(1);
  await expect(page.getByRole('button', { name: 'Show story 4 of 4: Story 4' })).toBeVisible();
  await page.getByRole('button', { name: 'Show story 4 of 4: Story 4' }).click();
  await expect(page.locator('.story-carousel-card.current')).toContainText('Story 4');
});
