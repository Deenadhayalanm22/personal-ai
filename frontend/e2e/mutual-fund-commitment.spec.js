import { expect, test } from '@playwright/test';

test('real API: staggered SIPs become due by their configured day and retain their runway', async ({ page, request }) => {
  const fixture = await request.post('http://localhost:8080/test/e2e/session');
  expect(fixture.ok()).toBeTruthy();
  const { sessionToken } = await fixture.json();
  const headers = { Cookie: `WEB_SESSION=${sessionToken}` };
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);
  const clock = instant => request.post('http://localhost:8080/test/e2e/clock', { data: { instant } });
  expect((await clock('2026-04-15T09:00:00Z')).ok()).toBeTruthy();
  await page.goto('/dashboard?month=2026-04');
  await page.getByRole('button', { name: /Your money/ }).click();
  await expect(page.getByText('Mutual funds', { exact: true })).toBeVisible();
  await addFundThroughUi(page, 'Parag', 10000, 1);
  await addFundThroughUi(page, 'HDFC', 20000, 5);
  await addFundThroughUi(page, 'Nippon', 30000, 10);
  await expect(page.locator('.mutual-funds-module:not(.current-sips-module):not(.stocks-module) .fund-card')).toHaveCount(3);
  await page.locator('.money-modal > .close').click();

  expect((await clock('2026-05-01T09:00:00Z')).ok()).toBeTruthy();
  let response = await request.get('http://localhost:8080/api/web/mutual-funds', { headers });
  expect(response.ok()).toBeTruthy();
  let { mutualFunds } = await response.json();
  const bySip = amount => mutualFunds.find(fund => Number(fund.activeSip.amount) === amount);
  expect(bySip(10000).currentSip.status).toBe('DUE');
  expect(bySip(20000).currentSip.status).toBe('SCHEDULED');
  expect(bySip(30000).currentSip.status).toBe('SCHEDULED');

  // Exercise the same due/upcoming state in the visible Monthly Commitment journey.
  const mayStories = page.waitForResponse(response => response.url().includes('/api/web/expenses/monthly?month=2026-04') && response.status() === 200);
  await page.goto('/dashboard?month=2026-04');
  await mayStories;
  await page.locator('.story-carousel-card').first().click();
  await expect(page.getByRole('dialog')).toContainText('May');
  await page.getByTestId('view-included-commitments').click();
  const smallCapRow = page.getByTestId(`included-mutual-fund-commitment-${bySip(10000).id}`).locator('..');
  await expect(smallCapRow).toHaveClass(/due-commitment/);
  const mediumCapRow = page.getByTestId(`included-mutual-fund-commitment-${bySip(20000).id}`).locator('..');
  await expect(mediumCapRow).not.toHaveClass(/due-commitment/);
  await page.getByTestId(`included-mutual-fund-commitment-${bySip(10000).id}`).click();
  const reviewedFund = page.locator('.fund-card').filter({ hasText: bySip(10000).schemeName });
  await expect(reviewedFund).toBeFocused();
  await confirmSipThroughUi(page, bySip(10000).id, '11000');
  await page.locator('.money-modal > .close').click();
  await page.locator('.story-carousel-card').first().click();
  await page.getByTestId('view-included-commitments').click();
  const confirmedSmallCapRow = page.getByTestId(`included-mutual-fund-commitment-${bySip(10000).id}`).locator('..');
  await expect(confirmedSmallCapRow).toHaveClass(/confirmed-commitment/);
  await expect(confirmedSmallCapRow.getByRole('button', { name: 'Review' })).toHaveCSS('color', 'rgb(34, 104, 79)');
  await page.getByRole('dialog').filter({ hasText: 'What makes up this month' }).getByRole('button', { name: '×' }).click();

  expect((await clock('2026-05-12T09:00:00Z')).ok()).toBeTruthy();
  await page.reload();
  await page.getByRole('button', { name: /Your money/ }).click();
  response = await request.get('http://localhost:8080/api/web/mutual-funds', { headers });
  ({ mutualFunds } = await response.json());
  expect(bySip(20000).currentSip.status).toBe('DUE');
  expect(bySip(30000).currentSip.status).toBe('DUE');
  await confirmSipThroughUi(page, bySip(20000).id, '20000');
  await confirmSipThroughUi(page, bySip(30000).id, '30000');

  expect((await clock('2026-05-21T09:00:00Z')).ok()).toBeTruthy();
  await page.reload();
  await page.getByRole('button', { name: /Your money/ }).click();
  await expect(page.locator('.fund-card').filter({ hasText: bySip(10000).schemeName })).not.toContainText('Allocated');
  await page.locator('.fund-card').filter({ hasText: bySip(10000).schemeName }).getByRole('button', { name: /Lump sum/ }).click();
  const lumpSumDialog = page.getByRole('dialog').filter({ hasText: 'LUMP-SUM INVESTMENT' });
  await lumpSumDialog.getByLabel('Amount invested').fill('5000');
  await lumpSumDialog.getByLabel('Transaction date').fill('2026-05-21');
  await lumpSumDialog.getByLabel('NAV').fill('100');
  await lumpSumDialog.getByRole('button', { name: 'Add lump sum' }).click();
  await expect(lumpSumDialog).toHaveCount(0);
  await page.getByRole('button', { name: `Open ${bySip(10000).schemeName} details` }).click();
  const detail = page.locator('.fund-detail');
  await expect(detail.getByText('Investment history', { exact: true })).toBeVisible();
  const historyEdits = detail.getByRole('button', { name: 'Edit this investment' });
  await expect(historyEdits).toHaveCount(3);
  await historyEdits.first().click();
  const editDialog = page.getByRole('dialog').filter({ hasText: 'Edit opening holding' });
  await editDialog.getByLabel('Amount invested').fill('1100');
  await editDialog.getByRole('button', { name: 'Save changes' }).click();
  await expect(editDialog).toHaveCount(0);
  await expect(detail).toContainText('Opening holding');
  await expect(detail).toContainText('SIP');
  await expect(detail).toContainText('Lump sum');
  await expect(detail).toContainText('₹1,100');
  await expect(detail).toContainText('₹5,000');
  const stories = await request.get('http://localhost:8080/api/web/expenses/monthly?month=2026-04', { headers });
  expect(stories.ok()).toBeTruthy();
  expect(JSON.stringify(await stories.json())).toContain('60,000');
});

test('real API: delete a mistakenly created mutual fund from its card', async ({ page, request }) => {
  const fixture = await request.post('http://localhost:8080/test/e2e/session');
  expect(fixture.ok()).toBeTruthy();
  const { sessionToken } = await fixture.json();
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);
  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-04-15T09:00:00Z' } })).ok()).toBeTruthy();

  await page.goto('/dashboard?month=2026-04');
  await page.getByRole('button', { name: /Your money/ }).click();
  await addFundThroughUi(page, 'Parag', 10000, 5);
  const funds = page.locator('.mutual-funds-module:not(.stocks-module) .fund-card');
  await expect(funds).toHaveCount(1);
  const mistakenFund = funds.first();
  const deleteResponse = page.waitForResponse(response => response.url().includes('/api/web/mutual-funds/') && response.request().method() === 'DELETE');
  await mistakenFund.getByRole('button', { name: /^Delete / }).click();
  expect((await deleteResponse).status()).toBe(204);
  await expect(funds).toHaveCount(0);
  await expect(page.getByText('Choose one verified scheme, then add only the details needed for your SIP.')).toBeVisible();

  await page.reload();
  await page.getByRole('button', { name: /Your money/ }).click();
  await expect(page.locator('.mutual-funds-module:not(.stocks-module) .fund-card')).toHaveCount(0);
});

async function addFundThroughUi(page, query, amount, sipDay) {
  await page.getByRole('button', { name: /Add a mutual fund/ }).click();
  const picker = page.getByLabel('Search and select scheme');
  await picker.fill(query);
  await page.locator('.scheme-menu button').first().click();
  await page.getByLabel('Monthly SIP amount').fill(String(amount));
  await page.getByLabel('SIP date').selectOption(String(sipDay));
  await page.getByLabel('Start month').fill('2026-04');
  await page.getByLabel('Current units').fill('10');
  await page.getByLabel('Total amount invested').fill('1000');
  await page.getByRole('button', { name: 'Create SIP plan' }).click();
  await expect(page.getByText('Your SIP is set up', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Done' }).click();
}

async function confirmSipThroughUi(page, fundId, allocationAmount) {
  const sip = page.getByTestId(`mutual-fund-sip-${fundId}`);
  await expect(sip).toBeVisible();
  await expect(sip).toContainText('Due now');
  await sip.getByRole('button', { name: 'Confirm allocation' }).click();
  const dialog = page.getByRole('dialog').filter({ hasText: 'Was this SIP processed?' });
  const amount = dialog.getByLabel('Amount invested');
  await expect(amount).toHaveValue(/\d+/);
  await amount.fill(allocationAmount);
  await dialog.getByLabel('NAV').fill('100');
  await dialog.getByRole('button', { name: 'Confirm estimate' }).click();
  await expect(dialog).toHaveCount(0);
}
