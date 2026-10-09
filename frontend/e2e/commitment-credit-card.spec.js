import { test, expect } from '@playwright/test';

const api = 'http://localhost:8080';

// Real authenticated API and persisted records; no financial responses are mocked.
for (const width of [1280, 375]) {
  test(`commitment card payment and extra increase next-month bill once at ${width}px`, async ({ page, request }) => {
    test.setTimeout(180_000);
    await page.setViewportSize({ width, height: 900 });
    await page.clock.install({ time: new Date('2026-10-09T09:00:00Z') });
    const session = await request.post(`${api}/test/e2e/session`);
    expect(session.ok()).toBeTruthy();
    const { sessionToken } = await session.json();
    expect((await request.post(`${api}/test/e2e/clock`, { data: { instant: '2026-10-09T09:00:00Z' } })).ok()).toBeTruthy();
    const headers = { Cookie: `WEB_SESSION=${sessionToken}` };
    await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);
    async function read(path) {
      const response = await request.get(`${api}/api/web/${path}`, { headers });
      expect(response.ok(), await response.text()).toBeTruthy();
      return response.json();
    }
    async function post(path, data) {
      const response = await request.post(`${api}/api/web/${path}`, { headers, data });
      expect(response.ok(), await response.text()).toBeTruthy();
      return response.json();
    }
    const account = await post('accounts', { name: 'Commitment credit card', type: 'CREDIT_CARD', issuerName: 'HDFC', statementDay: 1, dueDay: 21 });
    const commitment = await post('recurring-commitments', {
      label: 'Card-funded internet', amountMode: 'FIXED', planningAmount: 1000,
      recurrenceUnit: 'MONTH', recurrenceInterval: 1, nextExpectedDate: '2026-10-09', effectiveMonth: '2026-10'
    });
    const before = await read('expenses/calendar?month=2026-10');
    const cardBill = async month => (await read(`credit-card-bills?month=${month}`)).bills.find(bill => bill.cardId === account.cardId);
    const octoberBefore = (await cardBill('2026-10')).projectedAmount;
    const novemberBefore = (await cardBill('2026-11')).projectedAmount;

    await page.goto('/dashboard?month=2026-10');
    await page.getByRole('button', { name: /Your money.*Explore/ }).click();
    const card = page.getByTestId(`commitment-${commitment.id}`);
    await card.getByRole('button', { name: 'View details' }).click();
    await page.getByRole('dialog').filter({ hasText: 'COMMITMENT DETAILS' }).getByRole('button', { name: 'Paid', exact: true }).click();
    const completion = page.getByRole('dialog').filter({ hasText: 'COMPLETION' });
    await completion.getByLabel('Actual amount').fill('900');
    await expect(completion.getByLabel('Source account (optional)')).toBeEnabled();
    await completion.getByLabel('Source account (optional)').selectOption(String(account.id));
    const paymentResponse = page.waitForResponse(r => r.url().endsWith('/occurrences/complete') && r.request().method() === 'POST');
    await completion.getByRole('button', { name: 'Save completion' }).click();
    const response = await paymentResponse;
    expect(response.ok(), await response.text()).toBeTruthy();
    const command = response.request().postDataJSON();
    expect(command.sourceAccountId).toBe(account.id);
    const paid = await response.json();
    const retried = await post(`recurring-commitments/${commitment.id}/occurrences/complete`, command);
    expect(retried.transactionId).toBe(paid.transactionId);
    await expect(completion).toHaveCount(0);
    await expect(card).toContainText('Completed');
    expect((await cardBill('2026-10')).projectedAmount).toBe(octoberBefore);
    expect((await cardBill('2026-11')).projectedAmount).toBe(novemberBefore + 900);

    await card.getByRole('button', { name: 'View details' }).click();
    await page.getByRole('dialog').filter({ hasText: 'COMMITMENT DETAILS' }).getByRole('button', { name: '＋ Add extra' }).click();
    const extra = page.getByRole('dialog').filter({ hasText: 'EXTRA PAYMENT' });
    await extra.getByLabel('Extra amount').fill('100');
    await extra.getByLabel('Reason').fill('Extra data recharge');
    await expect(extra.getByLabel('Source account (optional)')).toBeEnabled();
    await extra.getByLabel('Source account (optional)').selectOption(String(account.id));
    const extraResponse = page.waitForResponse(r => r.url().endsWith('/extra') && r.request().method() === 'POST');
    await extra.getByRole('button', { name: 'Save extra' }).click();
    const extraResult = await extraResponse;
    expect(extraResult.ok(), await extraResult.text()).toBeTruthy();
    const extraCommand = extraResult.request().postDataJSON();
    expect(extraCommand.sourceAccountId).toBe(account.id);
    await post(`recurring-commitments/${commitment.id}/occurrences/2026-10/extra`, extraCommand);
    await expect(extra).toHaveCount(0);
    const after = await read('expenses/calendar?month=2026-10');
    expect(after.totalSpend).toBe(before.totalSpend + 1000);
    expect(after.creditCardSpend).toBe(before.creditCardSpend + 1000);
    expect(after.transactionCount).toBe(before.transactionCount + 2);
    const history = await read(`recurring-commitments/${commitment.id}/history`);
    expect(history.planningAmount).toBe(1000);
    expect(history.history[0]).toMatchObject({ actualAmount: 900, extraAmount: 100 });
    expect(history.history[0].extras).toHaveLength(1);
    const bill = await cardBill('2026-11');
    expect(bill).toMatchObject({ projectedAmount: novemberBefore + 1000, paidAmount: 0, remaining: novemberBefore + 1000 });

    await page.goto('/dashboard?month=2026-11');
    await page.getByRole('button', { name: 'View credit-card bills →' }).click();
    const accountCard = page.getByRole('region', { name: 'Accounts', exact: true }).locator('.account-card').filter({ has: page.getByRole('heading', { name: account.name, exact: true }) });
    await expect(accountCard).toContainText('₹1,000.00 remaining');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });
}
