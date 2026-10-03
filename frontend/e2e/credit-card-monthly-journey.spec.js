import { test, expect } from '@playwright/test';

const api = 'http://localhost:8080';
const bankName = 'May salary bank';
const cardName = 'May HDFC credit card';

// Real browser, real authenticated API, persisted expenses and the e2e application clock.
// No financial responses are mocked. Each session creates an isolated E2E profile.
test('May card purchases become the June bill; paying it clears the due without another expense', async ({ page, request }) => {
  test.setTimeout(180_000);
  page.setDefaultTimeout(10_000);
  const session = await request.post(`${api}/test/e2e/session`);
  expect(session.ok(), await session.text()).toBeTruthy();
  const { sessionToken } = await session.json();
  const headers = { Cookie: `WEB_SESSION=${sessionToken}` };
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);
  await page.clock.install({ time: new Date('2026-05-01T09:00:00Z') });

  async function date(day) {
    const response = await request.post(`${api}/test/e2e/clock`, { data: { instant: `${day}T09:00:00Z` } });
    expect(response.ok()).toBeTruthy();
    await page.clock.setSystemTime(new Date(`${day}T09:00:00Z`));
    await page.goto(`/dashboard?month=${day.slice(0, 7)}`);
    await expect(page.getByRole('button', { name: 'Add expense', exact: true })).toBeEnabled();
  }
  async function read(path) {
    const response = await request.get(`${api}/api/web/${path}`, { headers });
    expect(response.ok(), await response.text()).toBeTruthy();
    return response.json();
  }
  async function openAccounts() {
    await page.getByRole('button', { name: /Your money.*Explore/ }).click();
    const section = page.getByRole('region', { name: 'Accounts', exact: true });
    await expect(section.getByRole('button', { name: 'Add account' })).toBeEnabled();
    return section;
  }
  const accountCard = (section, name) => section.locator('.account-card').filter({ has: page.getByRole('heading', { name, exact: true }) });
  async function expense(day, amount, account, merchant) {
    await date(day);
    await page.getByRole('button', { name: 'Add expense', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Enter manually' })).toHaveAttribute('aria-pressed', 'true');
    await page.getByLabel('Amount', { exact: true }).fill(String(amount));
    await page.getByLabel('Date', { exact: true }).fill(day);
    await page.getByLabel('Category', { exact: true }).selectOption('Food & Dining');
    await page.getByLabel('Subcategory', { exact: true }).selectOption('Groceries');
    await page.getByLabel('Account (optional)').fill(account);
    await page.getByLabel('Merchant (optional)').fill(merchant);
    await page.getByRole('button', { name: 'Review expense', exact: true }).click();
    await expect(page.getByRole('region', { name: 'Expense preview' })).toContainText(account);
    const confirmation = page.waitForResponse(r => r.url().endsWith('/confirm') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Record expense', exact: true }).click();
    expect((await confirmation).ok()).toBeTruthy();
    await expect(page.getByRole('status').filter({ hasText: 'has been recorded' })).toBeVisible();
    await page.getByRole('button', { name: 'View expense', exact: true }).click();
  }

  await test.step('May 1: add a bank and a card with generation day 1 / due day 21', async () => {
    await date('2026-05-01');
    const accounts = await openAccounts();
    await accounts.getByRole('button', { name: 'Add account' }).click();
    const form = page.getByRole('dialog', { name: 'Add account' });
    await form.getByLabel('Account name', { exact: true }).fill(bankName);
    await form.getByRole('button', { name: 'Save account' }).click();
    await accounts.getByRole('button', { name: 'Add account' }).click();
    await form.getByLabel('Account name', { exact: true }).fill(cardName);
    await form.getByLabel('Account type').selectOption('CREDIT_CARD');
    await form.getByLabel('Bank / issuer').fill('HDFC');
    await form.getByLabel('Start tracking bills from (optional)').fill('2026-06');
    await form.getByLabel('Bill generation day').fill('1');
    await form.getByLabel('Payment due day').fill('21');
    await form.getByRole('button', { name: 'Save account' }).click();
    await expect(accountCard(accounts, cardName)).toContainText('Day 21');
    const options = await read('expenses/options');
    expect(options.accounts.map(a => a.name)).toEqual(expect.arrayContaining([bankName, cardName]));
    await page.locator('.money-modal > .close').click();
  });

  await test.step('May purchases: ₹4,000 on credit plus ₹600 on debit; both remain May expenses', async () => {
    await expense('2026-05-02', 480, cardName, 'May first groceries');
    await expense('2026-05-15', 1200, cardName, 'May middle groceries');
    await expense('2026-05-20', 600, bankName, 'May debit groceries');
    await expense('2026-05-31', 2320, cardName, 'May final groceries');
    const calendar = await read('expenses/calendar?month=2026-05');
    expect(calendar).toMatchObject({ transactionCount: 4, totalSpend: 4600, creditCardSpend: 4000 });
    const accounts = await openAccounts();
    await expect(accounts.getByText(/Tracked balance|Money received|Linked recorded spending/)).toHaveCount(0);
    const metadata = (await read('accounts')).accounts;
    for (const account of metadata) {
      expect(account).not.toHaveProperty('trackedBalance');
      expect(account).not.toHaveProperty('openingAmount');
      expect(account).not.toHaveProperty('moneyReceived');
    }
    await page.locator('.money-modal > .close').click();
    const june = (await read('credit-card-bills?month=2026-06')).bills.find(b => b.cardName === cardName);
    expect(june).toMatchObject({ periodStart: '2026-05-01', statementEnd: '2026-05-31', statementGeneratedAt: '2026-06-01', dueDate: '2026-06-21', projectedAmount: 4000, remaining: 4000, statementClosed: false });
    expect((await read('credit-card-bills?month=2026-05')).bills.find(b => b.cardName === cardName)).toBeUndefined();
    // Preview June before its statement opens; settlement must stay disabled.
    await page.getByLabel('Calendar month').fill('2026-06');
    await page.getByRole('button',{name:'View credit-card bills →'}).click();
    await expect(page.getByRole('region', { name: 'Credit-card bills', exact: true })).toContainText('₹4,000.00 remaining');
    await expect(page.getByRole('button', { name: 'Record bill payment' })).toBeDisabled();
  });

  await test.step('June 1: May bill closes; generation-day purchase belongs to July', async () => {
    await expense('2026-06-01', 900, cardName, 'June first groceries');
    const june = (await read('credit-card-bills?month=2026-06')).bills.find(b => b.cardName === cardName);
    expect(june).toMatchObject({ monthlyPurchaseAmount: 900, projectedAmount: 4000, remaining: 4000, statementClosed: true, dueDate: '2026-06-21' });
    expect((await read('credit-card-bills?month=2026-07')).bills.find(b => b.cardName === cardName)).toMatchObject({ projectedAmount: 900, dueDate: '2026-07-21', statementClosed: false });
    expect((await read('expenses/calendar?month=2026-06')).totalSpend).toBe(900);
    expect((await read('monthly-commitment?month=2026-06')).overview.stillToPay).toBe(4000);
  });

  await test.step('June 21: record ₹1,000 and then ₹3,000 paid; due clears without another expense', async () => {
    await date('2026-06-21');
    await page.getByRole('button',{name:'View credit-card bills →'}).click();
    const bills = page.getByRole('region', { name: 'Credit-card bills', exact: true });
    for (const [amount, remaining] of [[1000, 3000], [3000, 0]]) {
      await bills.getByRole('button', { name: 'Record bill payment' }).click();
      const payment = page.getByRole('dialog', { name: 'Record bill payment' });
      await expect(payment.getByLabel('Payment date')).toHaveValue('2026-06-21');
      await payment.getByLabel('Amount paid').fill(String(amount));
      await payment.getByRole('button', { name: 'Confirm bill payment' }).click();
      await expect(payment).toHaveCount(0);
      await expect(bills).toContainText(`₹${remaining.toLocaleString('en-IN')}.00 remaining`);
      expect((await read('monthly-commitment?month=2026-06')).overview.stillToPay).toBe(remaining);
      expect((await read('expenses/calendar?month=2026-06'))).toMatchObject({ totalSpend: 900, transactionCount: 1 });
    }
    await expect(bills).toContainText('Settled');
    await expect(bills.getByRole('button', { name: 'Record bill payment' })).toHaveCount(0);
    await bills.getByText('Payment history', { exact: true }).click();
    await expect(bills).toContainText('₹1,000.00 paid');
    await expect(bills).toContainText('₹3,000.00 paid');
    const feed = await read('activity?month=2026-06');
    expect(feed.items.filter(i => i.type === 'CARD_PAYMENT')).toHaveLength(2);
    expect(feed.items.filter(i => i.type === 'EXPENSE')).toHaveLength(1);
  });

  await test.step('Reload retains settlements and May spending; July still carries the June purchase', async () => {
    await page.reload();
    await expect(page.getByRole('region',{name:'Credit-card bills',exact:true})).toHaveCount(0);
    await page.getByRole('button',{name:'View credit-card bills →'}).click();
    await expect(page.getByRole('region', { name: 'Credit-card bills', exact: true })).toContainText('Settled');
    expect((await read('expenses/calendar?month=2026-05'))).toMatchObject({ totalSpend: 4600, transactionCount: 4, creditCardSpend: 4000 });
    const june = (await read('credit-card-bills?month=2026-06')).bills.find(b => b.cardName === cardName);
    expect(june).toMatchObject({ projectedAmount: 4000, paidAmount: 4000, remaining: 0 });
    expect(june.payments).toHaveLength(2);
    expect((await read('credit-card-bills?month=2026-07')).bills.find(b => b.cardName === cardName).remaining).toBe(900);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });
});
