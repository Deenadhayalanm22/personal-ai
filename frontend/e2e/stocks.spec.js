import { expect, test } from '@playwright/test';

test('real API: add two stocks and show their latest prices and total portfolio value', async ({ page, request }) => {
  const fixture = await request.post('http://localhost:8080/test/e2e/session');
  expect(fixture.ok()).toBeTruthy();
  const { sessionToken } = await fixture.json();
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);

  await page.goto('/dashboard?month=2026-04');
  await page.getByRole('button', { name: /Your money/ }).click();
  const stocks = page.locator('.stocks-module');
  await expect(stocks).toContainText('Search a listed stock');

  await addStockThroughUi(page, 'ITC', 'ITC Limited', 10, 4000);
  await addStockThroughUi(page, 'Reliance', 'Reliance Industries', 5, 6000);

  await expect(stocks.locator('.fund-card')).toHaveCount(2);
  await expect(stocks).toContainText('Total stock value');
  await expect(stocks).toContainText('₹11,755');
  await expect(stocks).toContainText('+₹1,755 overall P&L');

  const itc = stocks.locator('.fund-card', { hasText: 'ITC Limited' });
  await expect(itc).toContainText('Latest price ₹425.50');

  const reliance = stocks.locator('.fund-card', { hasText: 'Reliance Industries' });
  await expect(reliance).toContainText('Latest price ₹1,500.00');
});

test('real API: delete a mistakenly added stock from its card', async ({ page, request }) => {
  const fixture = await request.post('http://localhost:8080/test/e2e/session');
  expect(fixture.ok()).toBeTruthy();
  const { sessionToken } = await fixture.json();
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);

  await page.goto('/dashboard?month=2026-04');
  await page.getByRole('button', { name: /Your money/ }).click();
  const stocks = page.locator('.stocks-module');
  await addStockThroughUi(page, 'ITC', 'ITC Limited', 10, 4000);
  await expect(stocks.locator('.fund-card')).toHaveCount(1);

  const deleteResponse = page.waitForResponse(response => response.url().includes('/api/web/stocks/') && response.request().method() === 'DELETE');
  await stocks.getByRole('button', { name: 'Delete ITC Limited' }).click();
  expect((await deleteResponse).status()).toBe(204);
  await expect(stocks.locator('.fund-card')).toHaveCount(0);
  await expect(stocks).toContainText('Search a listed stock');

  await page.reload();
  await page.getByRole('button', { name: /Your money/ }).click();
  await expect(page.locator('.stocks-module .fund-card')).toHaveCount(0);
});

test('real API: confirm a due ETF monthly plan and refresh its commitment and holding', async ({ page, request }) => {
  const fixture = await request.post('http://localhost:8080/test/e2e/session');
  expect(fixture.ok()).toBeTruthy();
  const { sessionToken } = await fixture.json();
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);

  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-04-15T09:00:00Z' } })).ok()).toBeTruthy();
  await page.goto('/dashboard?month=2026-04');
  await page.getByRole('button', { name: /Your money/ }).click();
  await addStockThroughUi(page, 'ITC', 'ITC Limited', 10, 4000);
  const stockCard = page.locator('.stocks-module .fund-card', { hasText: 'ITC Limited' });
  await expect(stockCard.getByRole('button', { name: 'Set monthly recurring plan for ITC Limited' })).toBeVisible();
  await page.getByRole('button', { name: 'Set monthly recurring plan for ITC Limited' }).click();
  const plan = page.getByRole('dialog').filter({ has: page.getByRole('heading', { name: 'Plan ITC Limited' }) });
  await plan.getByLabel('Monthly amount').fill('5000');
  await plan.getByLabel('Investment day').fill('1');
  await plan.getByLabel('Start month').fill('2026-05');
  await plan.getByRole('button', { name: 'Save monthly plan' }).click();
  await expect(plan).toHaveCount(0);
  await page.locator('.money-modal > .close').click();

  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-05-01T09:00:00Z' } })).ok()).toBeTruthy();
  await page.reload();
  // Opening Stocks is the user-facing read that materializes and promotes the current monthly occurrence.
  const stocksLoaded = page.waitForResponse(response => response.url().includes('/api/web/stocks') && response.request().method() === 'GET');
  await page.getByRole('button', { name: /Your money/ }).click();
  const stocksResponse = await stocksLoaded;
  expect(stocksResponse.status()).toBe(200);
  const { stocks: dueStocks } = await stocksResponse.json();
  expect(dueStocks.find(stock => stock.name === 'ITC Limited').currentMonthlyPlan.status).toBe('DUE');
  await expect(page.getByTestId(/stock-monthly-plan-/)).toContainText('Due now');
  await page.locator('.money-modal > .close').click();
  await page.locator('.story-carousel-card').first().click();
  await page.getByTestId('view-included-commitments').click();
  const includedPlan = page.getByTestId(/included-stock-commitment-/).locator('..');
  await expect(includedPlan).toHaveClass(/due-commitment/);
  await page.getByTestId(/included-stock-commitment-/).click();
  await expect(stockCard).toBeFocused();
  await expect(page.getByTestId(/stock-monthly-plan-/)).toContainText('Due now');
  await page.getByRole('button', { name: 'Confirm allocation' }).click();
  const confirmation = page.getByRole('dialog').filter({ has: page.getByRole('heading', { name: 'Was this investment processed?' }) });
  await confirmation.getByLabel('Amount invested').fill('5000');
  await confirmation.getByLabel('Executed market price').fill('425.5');
  await confirmation.getByRole('button', { name: 'Confirm investment' }).click();
  await expect(confirmation).toHaveCount(0);
  await expect(page.locator('.stocks-module')).toContainText('₹9K');
  await page.locator('.money-modal > .close').click();
  await page.locator('.story-carousel-card').first().click();
  await expect(page.getByRole('dialog')).toContainText('₹5,000');
  await page.getByRole('button', { name: '← Back to stories' }).click();
  await page.getByRole('button', { name: /Your money/ }).click();
  await stockCard.getByRole('button', { name: /View details/ }).click();
  const detail = page.getByRole('dialog').filter({ has: page.getByRole('heading', { name: 'ITC Limited' }) });
  await expect(detail.getByText('Investment history', { exact: true })).toBeVisible();
  await expect(detail).toContainText('Opening holding');
  await expect(detail).toContainText('Monthly ETF purchase');
  await expect(detail).toContainText('₹5,000');
});

async function addStockThroughUi(page, query, expectedName, quantity, invested) {
  await page.getByRole('button', { name: '＋ Add a stock' }).click();
  const dialog = page.getByRole('dialog').filter({ has: page.getByRole('heading', { name: 'Add a stock' }) });
  await dialog.getByLabel('Search and select stock').fill(query);
  await dialog.locator('.scheme-menu button').filter({ hasText: expectedName }).click();
  await dialog.getByLabel('Shares held').fill(String(quantity));
  await dialog.getByLabel('Total amount invested').fill(String(invested));
  await dialog.getByRole('button', { name: 'Add stock' }).click();
  await expect(dialog).toHaveCount(0);
}
