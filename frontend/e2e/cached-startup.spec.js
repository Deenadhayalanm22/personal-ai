import { expect, test } from '@playwright/test';

const month = new Date().toISOString().slice(0, 7);
const cache = {
  version: 3, month, updatedAt: new Date().toISOString(),
  sections: {
    calendar: { currency: 'INR', totalSpend: 1234, transactionCount: 2, days: [] },
    recent: { items: [{ id: 1, merchant: 'Saved merchant', amount: 400 }] },
    commitment: { commitment: { storyType: 'MONTHLY_COMMITMENT', cardFace: { heading: 'Saved commitment', displayValue: '₹400' } } }
  }
};

test.beforeEach(async ({ page }) => {
  await page.addInitScript(({ cache }) => {
    localStorage.setItem('money-stories.last-verified-profile.v1', JSON.stringify({ demoMode: false }));
    localStorage.setItem('money-stories.dashboard-cache.v3.real', JSON.stringify(cache));
  }, { cache });
});

test('shows saved data while profile verification waits, then loads the dashboard', async ({ page }) => {
  let resolveProfile;
  const profileReady = new Promise(resolve => { resolveProfile = resolve; });
  const featureRequests = [];
  await page.route('**/api/web/**', async route => {
    featureRequests.push(route.request().url());
    await route.fulfill({ json: {} });
  });
  await page.route('**/api/web/auth/demo-profile', async route => {
    await profileReady;
    await route.fulfill({ json: { demoMode: false, canUseDemoMode: false } });
  });
  await page.route('**/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.goto(`/dashboard?month=${month}`);
  await expect(page.getByText('Showing your last saved view')).toBeVisible();
  await expect(page.getByText('Saved merchant')).toBeVisible();
  expect(featureRequests).toHaveLength(0);
  resolveProfile();
  await expect(page.getByRole('heading', { name: /Good (morning|afternoon|evening)/ })).toBeVisible();
});

test('expired session redirects without loading dashboard features', async ({ page }) => {
  const featureRequests = [];
  await page.route('**/api/web/**', route => { featureRequests.push(route.request().url()); return route.fulfill({ json: {} }); });
  await page.route('**/api/web/auth/demo-profile', route => route.fulfill({ status: 401, json: {} }));
  await page.goto(`/dashboard?month=${month}`);
  await expect(page).toHaveURL(/\/portal\?/);
  expect(featureRequests).toHaveLength(0);
});
