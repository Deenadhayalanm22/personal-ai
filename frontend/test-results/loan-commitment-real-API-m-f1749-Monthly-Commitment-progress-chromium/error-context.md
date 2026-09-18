# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: loan-commitment.spec.js >> real API: mark a due loan EMI paid and refresh Monthly Commitment progress
- Location: e2e/loan-commitment.spec.js:3:1

# Error details

```
Error: expect(locator).toContainText(expected) failed

Locator: getByRole('dialog')
Expected substring: "May"
Received string:    "← Back to storiesPLANNING · MONTHLY COMMITMENTApril 2026 · One updateHome loan exits next₹100,000.00/month is free from July 2026.Debt repayments₹100,000.00Loan payment progress₹100,000.00 paid of ₹100,000.00 · ₹0.00 left · 100%This monthPaid · nothing dueView included commitments←1 of 3Next →"
Timeout: 5000ms

Call log:
  - Expect "toContainText" getByRole('dialog') with timeout 5000ms
  - waiting for getByRole('dialog')
    14 × locator resolved to <section role="dialog" tabindex="-1" aria-modal="true" class="story-detail">…</section>
       - unexpected value "← Back to storiesPLANNING · MONTHLY COMMITMENTApril 2026 · One updateHome loan exits next₹100,000.00/month is free from July 2026.Debt repayments₹100,000.00Loan payment progress₹100,000.00 paid of ₹100,000.00 · ₹0.00 left · 100%This monthPaid · nothing dueView included commitments←1 of 3Next →"

```

```yaml
- dialog:
  - button "← Back to stories"
  - paragraph: PLANNING · MONTHLY COMMITMENT
  - article:
    - paragraph: April 2026 · One update
    - heading "Home loan exits next" [level=1]
    - paragraph: ₹100,000.00/month is free from July 2026.
    - text: Debt repayments
    - strong: ₹100,000.00
    - text: Loan payment progress
    - strong: ₹100,000.00 paid of ₹100,000.00 · ₹0.00 left · 100%
    - text: This month
    - strong: Paid · nothing due
    - button "View included commitments"
  - button "←" [disabled]
  - text: 1 of 3
  - button "Next →"
```

# Test source

```ts
  1  | import { expect, test } from '@playwright/test';
  2  | 
  3  | test('real API: mark a due loan EMI paid and refresh Monthly Commitment progress', async ({ page, request }) => {
  4  |   const fixture = await request.post('http://localhost:8080/test/e2e/session');
  5  |   expect(fixture.ok()).toBeTruthy();
  6  |   const { sessionToken } = await fixture.json();
  7  |   await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);
  8  | 
  9  |   // 15 April makes January through April historical payment months. April is already history;
  10 |   // May becomes the first independently payable occurrence when the clock advances.
  11 |   expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-04-15T09:00:00Z' } })).ok()).toBeTruthy();
  12 |   await page.goto('/dashboard?month=2026-04');
  13 |   await page.getByRole('button', { name: /Your money/ }).click();
  14 |   await expect(page.getByTestId('loans-section')).toContainText('No loans added yet.');
  15 |   await page.getByRole('button', { name: /Add another loan/ }).click();
  16 |   await page.getByLabel('Loan name').fill('Home loan');
  17 |   await page.getByLabel('Loan type').selectOption('HOME');
  18 |   await page.getByLabel('Original loan principal').fill('600000');
  19 |   await page.getByLabel('Monthly EMI amount').fill('100000');
  20 |   await page.getByLabel('Total tenure in months').fill('6');
  21 |   await page.getByLabel('First EMI due date').fill('2026-01-01');
  22 |   await page.getByLabel('Bank / lender').fill('Example Bank');
  23 |   await page.getByRole('button', { name: 'Add loan' }).click();
  24 |   const loans = page.getByTestId('loans-section');
  25 |   await expect(loans).toContainText('Home loan');
  26 |   await expect(loans).toContainText('4 of 6 historical EMI months completed');
  27 |   await expect(loans).toContainText('April EMI');
  28 |   await expect(loans).toContainText('PAID');
  29 |   await expect(loans).toContainText('May');
  30 |   await expect(loans).not.toContainText('June');
  31 |   await expect(loans).toContainText('UPCOMING');
  32 |   await page.locator('.money-modal > .close').click();
  33 | 
  34 |   // April's current card and May runway include the loan, but April has no outstanding payment.
  35 |   await page.locator('.story-carousel-card').first().click();
  36 |   const aprilStory = page.getByRole('dialog');
  37 |   await expect(aprilStory).toContainText('₹100,000');
  38 |   await expect(aprilStory).toContainText('Paid · nothing due');
  39 |   await page.getByRole('button', { name: 'Next →' }).click();
  40 |   await expect(aprilStory).toContainText('May');
  41 |   await expect(aprilStory).toContainText('₹100,000');
  42 |   await page.getByRole('button', { name: '← Back to stories' }).click();
  43 | 
  44 |   expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-05-05T09:00:00Z' } })).ok()).toBeTruthy();
  45 |   await page.reload();
  46 | 
  47 |   // May is due with a zero-progress commitment story and one route to its source row.
  48 |   await page.locator('.story-carousel-card').first().click();
  49 |   const mayStory = page.getByRole('dialog');
> 50 |   await expect(mayStory).toContainText('May');
     |                          ^ Error: expect(locator).toContainText(expected) failed
  51 |   await expect(mayStory).toContainText('₹0.00 paid of ₹100,000');
  52 |   await expect(mayStory).toContainText('₹100,000.00 left');
  53 |   await expect(mayStory).toContainText('0%');
  54 |   await expect(mayStory.locator('.story-action')).toHaveCount(1);
  55 |   await page.getByTestId('view-included-commitments').click();
  56 |   await expect(page.getByTestId('included-loan-commitment')).toBeVisible();
  57 |   await page.getByTestId('included-loan-commitment').click();
  58 |   await expect(loans).toContainText('Home loan');
  59 |   const mayEmi = loans.getByTestId('loan-emi-2026-05');
  60 |   await expect(mayEmi).toHaveClass(/due/);
  61 |   await expect(mayEmi).toContainText('DUE');
  62 |   await expect(mayEmi).toContainText(/(?:1 May|May 1),? 2026/);
  63 |   await expect(mayEmi).toContainText(/₹1,?00,000/);
  64 |   await expect(mayEmi.getByRole('button', { name: 'Mark May EMI paid' })).toBeVisible();
  65 | 
  66 |   await mayEmi.getByRole('button', { name: 'Mark May EMI paid' }).click();
  67 |   await page.reload();
  68 |   await page.getByRole('button', { name: /Your money/ }).click();
  69 |   const persistedMayEmi = loans.getByTestId('loan-emi-2026-05');
  70 |   await expect(persistedMayEmi).toContainText('PAID');
  71 |   await expect(persistedMayEmi).toContainText(/Paid amount: ₹1,?00,000/);
  72 |   await expect(persistedMayEmi).toContainText(/Paid on: (?:5 May|May 5),? 2026/);
  73 |   await expect(persistedMayEmi.getByRole('button', { name: 'Mark May EMI paid' })).toHaveCount(0);
  74 |   await page.locator('.money-modal > .close').click();
  75 | 
  76 |   await page.locator('.story-carousel-card').first().click();
  77 |   await expect(page.getByRole('dialog')).toContainText('₹100,000.00 paid of ₹100,000.00');
  78 |   await expect(page.getByRole('dialog')).toContainText('₹0.00 left');
  79 |   await expect(page.getByRole('dialog')).toContainText('100%');
  80 |   await expect(page.getByRole('dialog')).toContainText('Paid · nothing due');
  81 |   await page.getByRole('button', { name: '← Back to stories' }).click();
  82 | 
  83 |   expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-06-01T09:00:00Z' } })).ok()).toBeTruthy();
  84 |   const juneStories = page.waitForResponse(response => response.url().includes('/api/web/expenses/monthly?month=2026-04') && response.status() === 200);
  85 |   await page.reload();
  86 |   await juneStories;
  87 |   await page.locator('.story-carousel-card').first().click();
  88 |   const juneStory = page.getByRole('dialog');
  89 |   await expect(juneStory).toContainText('June');
  90 |   await expect(juneStory).toContainText('₹0.00 paid of ₹100,000');
  91 |   await expect(juneStory).toContainText('₹100,000.00 left');
  92 |   await expect(juneStory).toContainText('0%');
  93 |   await page.getByTestId('view-included-commitments').click();
  94 |   await page.getByTestId('included-loan-commitment').click();
  95 |   await expect(loans.getByTestId('loan-emi-2026-05')).toContainText('PAID');
  96 |   await expect(loans.getByTestId('loan-emi-2026-06')).toContainText('DUE');
  97 | });
  98 | 
```