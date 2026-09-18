import { expect, test } from '@playwright/test';

test('real API: mark a due loan EMI paid and refresh Monthly Commitment progress', async ({ page, request }) => {
  const fixture = await request.post('http://localhost:8080/test/e2e/session');
  expect(fixture.ok()).toBeTruthy();
  const { sessionToken } = await fixture.json();
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);

  // 15 April makes January through April historical payment months. April is already history;
  // May becomes the first independently payable occurrence when the clock advances.
  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-04-15T09:00:00Z' } })).ok()).toBeTruthy();
  await page.goto('/dashboard?month=2026-04');
  await page.getByRole('button', { name: /Your money/ }).click();
  await expect(page.getByTestId('loans-section')).toContainText('No loans added yet.');
  await page.getByRole('button', { name: /Add another loan/ }).click();
  await page.getByLabel('Loan name').fill('Home loan');
  await page.getByLabel('Loan type').selectOption('HOME');
  await page.getByLabel('Original loan principal').fill('600000');
  await page.getByLabel('Monthly EMI amount').fill('100000');
  await page.getByLabel('Total tenure in months').fill('6');
  await page.getByLabel('First EMI due date').fill('2026-01-01');
  await page.getByLabel('Bank / lender').fill('Example Bank');
  await page.getByRole('button', { name: 'Add loan' }).click();
  const loans = page.getByTestId('loans-section');
  await expect(loans).toContainText('Home loan');
  await expect(loans).toContainText('4 of 6 historical EMI months completed');
  await expect(loans).toContainText('April EMI');
  await expect(loans).toContainText('PAID');
  await expect(loans).toContainText('May');
  await expect(loans).not.toContainText('June');
  await expect(loans).toContainText('UPCOMING');
  await page.locator('.money-modal > .close').click();

  // April's current card and May runway include the loan, but April has no outstanding payment.
  await page.locator('.story-carousel-card').first().click();
  const aprilStory = page.getByRole('dialog');
  await expect(aprilStory).toContainText('₹100,000');
  await expect(aprilStory).toContainText('Paid · nothing due');
  await page.getByRole('button', { name: 'Next →' }).click();
  await expect(aprilStory).toContainText('May');
  await expect(aprilStory).toContainText('₹100,000');
  await page.getByRole('button', { name: '← Back to stories' }).click();

  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-05-05T09:00:00Z' } })).ok()).toBeTruthy();
  const mayStories = page.waitForResponse(response => response.url().includes('/api/web/expenses/monthly?month=2026-04') && response.status() === 200);
  await page.reload();
  await mayStories;

  // May is due with a zero-progress commitment story and one route to its source row.
  await page.locator('.story-carousel-card').first().click();
  const mayStory = page.getByRole('dialog');
  await expect(mayStory).toContainText('May');
  await expect(mayStory).toContainText('₹0.00 paid of ₹100,000');
  await expect(mayStory).toContainText('₹100,000.00 left');
  await expect(mayStory).toContainText('0%');
  await expect(mayStory.locator('.story-action')).toHaveCount(1);
  await page.getByTestId('view-included-commitments').click();
  await expect(page.getByTestId('included-loan-commitment')).toBeVisible();
  await page.getByTestId('included-loan-commitment').click();
  await expect(loans).toContainText('Home loan');
  const reviewedLoan = loans.locator('.loan-row', { hasText: 'Home loan' });
  await expect(reviewedLoan).toBeFocused();
  const mayEmi = loans.getByTestId('loan-emi-2026-05');
  await expect(mayEmi).toHaveClass(/due/);
  await expect(mayEmi).toContainText('DUE');
  await expect(mayEmi).toContainText(/(?:1 May|May 1),? 2026/);
  await expect(mayEmi).toContainText(/₹1,?00,000/);
  await expect(mayEmi.getByRole('button', { name: 'Mark May EMI paid' })).toBeVisible();

  await mayEmi.getByRole('button', { name: 'Mark May EMI paid' }).click();
  await page.reload();
  await page.getByRole('button', { name: /Your money/ }).click();
  const persistedMayEmi = loans.getByTestId('loan-emi-2026-05');
  await expect(persistedMayEmi).toContainText('PAID');
  await expect(persistedMayEmi).toContainText(/Paid amount: ₹1,?00,000/);
  await expect(persistedMayEmi).toContainText(/Paid on: (?:5 May|May 5),? 2026/);
  await expect(persistedMayEmi.getByRole('button', { name: 'Mark May EMI paid' })).toHaveCount(0);
  await page.locator('.money-modal > .close').click();

  await page.locator('.story-carousel-card').first().click();
  await expect(page.getByRole('dialog')).toContainText('₹100,000.00 paid of ₹100,000.00');
  await expect(page.getByRole('dialog')).toContainText('₹0.00 left');
  await expect(page.getByRole('dialog')).toContainText('100%');
  await expect(page.getByRole('dialog')).toContainText('Paid · nothing due');
  await page.getByRole('button', { name: '← Back to stories' }).click();

  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-06-01T09:00:00Z' } })).ok()).toBeTruthy();
  const juneStories = page.waitForResponse(response => response.url().includes('/api/web/expenses/monthly?month=2026-04') && response.status() === 200);
  await page.reload();
  await juneStories;
  await page.locator('.story-carousel-card').first().click();
  const juneStory = page.getByRole('dialog');
  await expect(juneStory).toContainText('June');
  await expect(juneStory).toContainText('₹0.00 paid of ₹100,000');
  await expect(juneStory).toContainText('₹100,000.00 left');
  await expect(juneStory).toContainText('0%');
  await page.getByTestId('view-included-commitments').click();
  await page.getByTestId('included-loan-commitment').click();
  await expect(loans.getByTestId('loan-emi-2026-05')).toContainText('PAID');
  await expect(loans.getByTestId('loan-emi-2026-06')).toContainText('DUE');
});
