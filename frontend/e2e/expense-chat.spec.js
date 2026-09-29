import { expect, test } from '@playwright/test';

const answer = {
  answer: 'Your recorded food expenses were ₹750 across 2 records.',
  evidence: [{ query: { startDate: '2026-09-01', endDate: '2026-10-01', groupBy: ['category'], filters: [] }, currency: 'INR', matchingCount: 2, matchingTotal: 750, rows: [{ category: 'Food', total: 750, count: 2 }], truncated: false }]
};
async function dashboard(page) {
  await page.route('**/api/web/**', route => {
    const path = new URL(route.request().url()).pathname;
    const json = path.endsWith('demo-profile') ? { demoMode: false, canUseDemoMode: true }
      : path.endsWith('/calendar') ? { currency: 'INR', totalSpend: 750, transactionCount: 2, days: [] }
      : path.endsWith('/monthly') ? { stories: [] } : { items: [], actions: [], commitments: [], loans: [], funds: [], stocks: [] };
    return route.fulfill({ json });
  });
  await page.route('**/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.goto('/dashboard?month=2026-09');
  await page.getByRole('button', { name: 'Ask about expenses', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Where did my money go?' })).toBeEnabled();
}

test('starter question, evidence, follow-up history and new chat', async ({ page }) => {
  await dashboard(page);
  const requests = [];
  await page.route('**/api/web/expense-chat', route => {
    requests.push(route.request().postDataJSON());
    return route.fulfill({ json: answer });
  });
  await page.getByRole('button', { name: 'Where did my money go?' }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
  expect(requests[0]).toEqual({ message: 'Where did my money go?', month: '2026-09', history: [] });
  await page.getByText('Based on 1 expense query').click();
  await expect(page.getByText('2 matching records', { exact: false })).toBeVisible();
  await page.getByLabel('Your expense question').fill('And excluding rent?');
  await page.getByRole('button', { name: 'Send question' }).click();
  await expect.poll(() => requests.length).toBe(2);
  expect(requests[1].history).toEqual([{ role: 'user', content: requests[0].message }, { role: 'assistant', content: answer.answer }]);
  await expect(page.getByRole('button', { name: 'New chat' })).toBeEnabled();
  await page.getByRole('button', { name: 'New chat' }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toHaveCount(0);
  await page.screenshot({ path: 'test-results/expense-chat-desktop.png' });
});

test('failed question remains editable and retry does not duplicate history', async ({ page }) => {
  await dashboard(page);
  await page.route('**/api/web/expense-chat', route => route.fulfill({ status: 503, json: { message: 'Expense chat is not configured yet.' } }));
  await page.getByRole('button', { name: 'Which were my largest expenses?' }).click();
  await expect(page.getByRole('alert')).toContainText('not configured');
  await expect(page.getByLabel('Your expense question')).toHaveValue('Which were my largest expenses?');
  await page.route('**/api/web/expense-chat', route => {
    expect(route.request().postDataJSON().history).toEqual([]);
    return route.fulfill({ json: answer });
  });
  await page.getByRole('button', { name: 'Send question' }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
});

test('fits mobile, disables offline questions and clears on profile switch', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await dashboard(page);
  await page.route('**/api/web/expense-chat', route => route.fulfill({ json: answer }));
  await page.getByRole('button', { name: 'Where did my money go?' }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
  const box = await page.getByRole('region', { name: 'Expense assistant' }).boundingBox();
  expect(box.x).toBeGreaterThanOrEqual(0);
  expect(box.x + box.width).toBeLessThanOrEqual(390);
  await page.screenshot({ path: 'test-results/expense-chat-mobile.png' });
  await page.evaluate(() => window.dispatchEvent(new Event('offline')));
  await expect(page.getByLabel('Your expense question')).toBeDisabled();
  await page.getByRole('button', { name: 'Close expense chat' }).click();
  // Switching the active profile unmounts the chat component and drops its history.
  await page.route('**/api/web/auth/demo-profile', route => route.fulfill({ json: { demoMode: true, canUseDemoMode: true } }));
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: 'You' }).click();
  // The V1 header exposes the profile toggle as a checkbox.
  const toggle = page.getByLabel(/demo mode/i);
  await toggle.check();
  await page.getByRole('button', { name: 'Ask about expenses', exact: true }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toHaveCount(0);
});
