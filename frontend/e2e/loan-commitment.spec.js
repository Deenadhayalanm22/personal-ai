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
  await expect(page.getByTestId('loans-section')).toContainText('No active loans.');
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
  await expect(loans).not.toContainText('April EMI');
  await loans.getByRole('button', { name: 'View details' }).click();
  await expect(loans.getByRole('button', { name: 'View details' })).toHaveClass(/view-details-link/);
  const initialLoanHistory = page.locator('.fund-detail').filter({ hasText: 'EMI timeline' });
  await expect(initialLoanHistory.locator('.investment-history')).toBeVisible();
  await expect(initialLoanHistory).toContainText('April 2026 EMI');
  await expect(initialLoanHistory).toContainText('May 2026 EMI');
  await expect(initialLoanHistory).toContainText('June 2026 EMI');
  await initialLoanHistory.getByRole('button', { name: '×' }).click();
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
  await reviewedLoan.getByRole('button', { name: 'View details' }).click();
  const loanHistory = page.locator('.fund-detail').filter({ hasText: 'EMI timeline' });
  const mayEmi = loanHistory.locator('.investment-history-row', { hasText: 'May 2026 EMI' });
  await expect(mayEmi).toHaveClass(/due/);
  await expect(mayEmi).toContainText('Due now');
  await expect(mayEmi).toContainText(/(?:1 May|May 1)/);
  await expect(mayEmi).toContainText(/₹1,?00,000/);
  await expect(mayEmi.locator('.due-reminder')).toContainText('Due now');
  await expect(mayEmi.locator('.due-reminder')).toHaveCSS('background-color', 'rgb(255, 244, 241)');
  await expect(mayEmi.getByRole('button', { name: /Mark May.*EMI paid/ })).toBeVisible();

  const paymentResponse = page.waitForResponse(response => response.url().includes('/api/web/loans/') && response.url().includes('/emi-occurrences/2026-05/paid') && response.request().method() === 'POST');
  await mayEmi.getByRole('button', { name: /Mark May.*EMI paid/ }).click();
  expect((await paymentResponse).status()).toBe(200);
  await page.reload();
  await page.getByRole('button', { name: /Your money/ }).click();
  await reviewedLoan.getByRole('button', { name: 'View details' }).click();
  const persistedMayEmi = page.locator('.fund-detail .investment-history-row', { hasText: 'May 2026 EMI' });
  await expect(persistedMayEmi).toContainText(/Paid on (?:5 May|May 5)/);
  await expect(persistedMayEmi.getByRole('button', { name: /Mark May.*EMI paid/ })).toHaveCount(0);
  await expect(loanHistory).toContainText('May 2026 EMI');
  await expect(loanHistory).toContainText(/Paid on (?:5 May|May 5)/);
  await loanHistory.getByRole('button', { name: '×' }).click();
  await page.locator('.money-modal > .close').click();

  const paidStories = page.waitForResponse(response => response.url().includes('/api/web/expenses/monthly?month=2026-04') && response.status() === 200);
  await page.reload();
  await paidStories;
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
  await reviewedLoan.getByRole('button', { name: 'View details' }).click();
  await expect(page.locator('.fund-detail .investment-history-row', { hasText: 'May 2026 EMI' })).toContainText(/Paid on/);
  const juneEmi = page.locator('.fund-detail .investment-history-row', { hasText: 'June 2026 EMI' });
  await expect(juneEmi).toContainText('Due now');
  const finalPaymentResponse = page.waitForResponse(response => response.url().includes('/api/web/loans/') && response.url().includes('/emi-occurrences/2026-06/paid') && response.request().method() === 'POST');
  await juneEmi.getByRole('button', { name: /Mark June.*EMI paid/ }).click();
  expect((await finalPaymentResponse).status()).toBe(200);
  await page.reload();
  await page.getByRole('button', { name: /Your money/ }).click();
  await expect(loans).toContainText('No active loans.');
  await page.getByTestId('closed-loans-toggle').click();
  const closedLoan = loans.getByTestId(/closed-loan-/);
  await expect(closedLoan).toContainText('Home loan');
  await expect(closedLoan).toContainText('CLOSED');
  await page.locator('.money-modal > .close').click();

  // June is the sixth and final scheduled month. The active record remains available
  // for payment history, but its tenure cannot create a July EMI or commitment.
  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-07-01T09:00:00Z' } })).ok()).toBeTruthy();
  const julyStories = page.waitForResponse(response => response.url().includes('/api/web/expenses/monthly?month=2026-04') && response.status() === 200);
  await page.reload();
  await julyStories;
  await page.locator('.story-carousel-card').first().click();
  const julyStory = page.getByRole('dialog');
  await expect(julyStory).toContainText('July');
  await expect(julyStory).not.toContainText('₹100,000');
  await expect(page.getByTestId('view-included-commitments')).toHaveCount(0);
});

test('real API: delete a mistakenly created loan from its loan section', async ({ page, request }) => {
  const fixture = await request.post('http://localhost:8080/test/e2e/session');
  expect(fixture.ok()).toBeTruthy();
  const { sessionToken } = await fixture.json();
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);

  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-04-15T09:00:00Z' } })).ok()).toBeTruthy();
  await page.goto('/dashboard?month=2026-04');
  await page.getByRole('button', { name: /Your money/ }).click();
  const loans = page.getByTestId('loans-section');
  await page.getByRole('button', { name: /Add another loan/ }).click();
  await page.getByLabel('Loan name').fill('Mistaken personal loan');
  await page.getByLabel('Loan type').selectOption('PERSONAL');
  await page.getByLabel('Original loan principal').fill('30000');
  await page.getByLabel('Monthly EMI amount').fill('5000');
  await page.getByLabel('Total tenure in months').fill('6');
  await page.getByLabel('First EMI due date').fill('2026-05-01');
  await page.getByLabel('Bank / lender').fill('Example Bank');
  await page.getByRole('button', { name: 'Add loan' }).click();
  await expect(loans).toContainText('Mistaken personal loan');

  const deleteResponse = page.waitForResponse(response => response.url().includes('/api/web/loans/') && response.request().method() === 'DELETE');
  await loans.getByRole('button', { name: 'Delete Mistaken personal loan' }).click();
  expect((await deleteResponse).status()).toBe(204);
  await expect(loans).not.toContainText('Mistaken personal loan');
  await expect(loans).toContainText('No active loans.');

  await page.reload();
  await page.getByRole('button', { name: /Your money/ }).click();
  await expect(page.getByTestId('loans-section')).toContainText('No active loans.');
});
