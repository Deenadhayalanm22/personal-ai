import { expect, test } from '@playwright/test';

const api = 'http://localhost:8080';

async function setClock(request, instant) {
  expect((await request.post(`${api}/test/e2e/clock`, { data: { instant } })).ok()).toBeTruthy();
}

async function startFromHome(page, request, instant) {
  await setClock(request, instant);
  const month = instant.slice(0, 7);
  await page.goto(`/dashboard?month=${month}`);
  const expectedMonth = new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(`${month}-01T12:00:00Z`)).toUpperCase();
  await expect(page.locator('.app-heading .micro-label')).toHaveText(expectedMonth);
}

async function addLoan(page, values) {
  await page.getByRole('button', { name: /Add another loan/ }).click();
  await page.getByLabel('Loan name').fill(values.name);
  await page.getByLabel('Loan type').selectOption(values.type);
  await page.getByLabel('Original loan principal').fill(values.principal);
  await page.getByLabel('Monthly EMI amount').fill(values.emi);
  await page.getByLabel('Total tenure in months').fill(values.tenure);
  await page.getByLabel('First EMI due date').fill(values.firstDueDate);
  await page.getByLabel('Bank / lender').fill(values.lender);
  await page.getByRole('button', { name: 'Add loan' }).click();
}

async function payAndVerify(page, button, month, paidAt, amount) {
  const responsePromise = page.waitForResponse(response => response.url().includes(`/emi-occurrences/${month}/paid`) && response.request().method() === 'POST');
  await button.click();
  const response = await responsePromise;
  expect(response.ok()).toBeTruthy();
  const loan = await response.json();
  const occurrence = loan.emiOccurrences.find(entry => entry.month === month);
  expect(occurrence).toMatchObject({ status: 'PAID', paidAt, paidAmount: amount });
}

test('real API: manages loan payment, skip, closure, pre-closure, and restructure timeline', async ({ page, request }) => {
  test.setTimeout(120000);
  page.setDefaultTimeout(10000);
  const fixture = await request.post(`${api}/test/e2e/session`);
  expect(fixture.ok()).toBeTruthy();
  const { sessionToken } = await fixture.json();
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);

  await startFromHome(page, request, '2026-04-15T09:00:00Z');
  await page.getByRole('button', { name: /Your money/ }).click();
  const loans = page.getByTestId('loans-section');
  await addLoan(page, { name: 'Home loan', type: 'HOME', principal: '600000', emi: '100000', tenure: '6', firstDueDate: '2026-01-01', lender: 'Example Bank' });
  await expect(loans).toContainText('4 of 6 EMI months completed');
  await expect(loans.getByRole('button', { name: /Edit Home loan|Delete Home loan/ })).toHaveCount(0);
  await loans.locator('.loan-row', { hasText: 'Home loan' }).getByRole('button', { name: 'View details' }).click();
  const details = page.locator('.fund-detail').filter({ hasText: 'LOAN DETAILS' });
  await expect(details).toContainText('May 2026 EMI');
  await expect(details).not.toContainText('January 2026 EMI');
  await expect(details.getByRole('button', { name: 'Pay May EMI' })).toBeDisabled();
  await expect(details.getByRole('button', { name: 'Skip May EMI' })).toBeDisabled();
  await expect(details.getByRole('button', { name: 'Edit loan' })).toBeVisible();
  await expect(details.getByRole('button', { name: 'Delete loan' })).toBeVisible();
  await details.getByRole('button', { name: 'Edit loan' }).click();
  await page.getByLabel('Bank / lender').fill('Updated Bank');
  await page.getByRole('button', { name: /Save changes|Update loan/ }).click();
  await expect(loans.locator('.loan-row', { hasText: 'Home loan' })).toContainText('Updated Bank');

  await addLoan(page, { name: 'Mistaken loan', type: 'PERSONAL', principal: '30000', emi: '5000', tenure: '6', firstDueDate: '2026-12-01', lender: 'Example Bank' });
  await loans.locator('.loan-row', { hasText: 'Mistaken loan' }).getByRole('button', { name: 'View details' }).click();
  await expect(page.locator('.fund-detail')).toContainText('Mistaken loan');
  await page.locator('.fund-detail').getByRole('button', { name: 'Delete loan' }).click();
  await page.getByRole('button', { name: 'Confirm delete' }).click();
  await expect(loans).not.toContainText('Mistaken loan');

  await startFromHome(page, request, '2026-05-01T09:00:00Z');
  await page.locator('.story-carousel-card').first().click();
  const story = page.getByRole('dialog');
  await expect(story.getByRole('button', { name: 'Review loan' })).toHaveCount(0);
  await story.getByTestId('view-included-commitments').click();
  const included = page.getByRole('dialog').filter({ hasText: 'INCLUDED COMMITMENTS' });
  const dueLoanEvidence = included.locator('.evidence-row', { hasText: 'Home loan' });
  await expect(dueLoanEvidence).toHaveClass(/due-loan-commitment/);
  await expect(dueLoanEvidence).toHaveCSS('background-color', 'rgb(255, 244, 241)');
  await expect(included.getByRole('button', { name: 'Review loan' })).toBeVisible();
  await included.getByRole('button', { name: 'Review loan' }).click();
  const homeRow = loans.locator('.loan-row', { hasText: 'Home loan' });
  await expect(homeRow).toBeFocused();
  await expect(homeRow.locator('.loan-progress')).toContainText('4 of 6 EMI months completed');
  await expect(homeRow.locator('.loan-progress i')).toHaveAttribute('style', /--loan-progress:\s*67%/);
  const dueDetailsArea = homeRow.locator('.loan-details-area');
  const dueDetails = dueDetailsArea.getByRole('button', { name: 'View details' });
  await expect(dueDetailsArea.locator('.loan-details-due')).toHaveText('Due now');
  await expect(dueDetailsArea).toHaveCSS('background-color', 'rgb(255, 244, 241)');
  await expect(dueDetailsArea).toHaveCSS('border-top-color', 'rgb(228, 164, 156)');
  const layout = await dueDetailsArea.evaluate(element => ({
    area: element.getBoundingClientRect().toJSON(),
    children: [element.querySelector('.view-details-label'), element.querySelector('.loan-details-due')]
      .map(child => child.getBoundingClientRect().toJSON())
  }));
  for (const box of layout.children) {
    expect(box.x, 'View details and Due now must both render inside the red bordered area').toBeGreaterThanOrEqual(layout.area.x + 1);
    expect(box.y).toBeGreaterThanOrEqual(layout.area.y + 1);
    expect(box.x + box.width).toBeLessThanOrEqual(layout.area.x + layout.area.width - 1);
    expect(box.y + box.height).toBeLessThanOrEqual(layout.area.y + layout.area.height - 1);
  }
  const [viewDetailsBox, dueNowBox] = layout.children;
  expect(viewDetailsBox.x).toBeGreaterThan(dueNowBox.x + dueNowBox.width);
  expect(Math.abs((viewDetailsBox.y + viewDetailsBox.height / 2) - (dueNowBox.y + dueNowBox.height / 2))).toBeLessThanOrEqual(2);
  await homeRow.getByRole('button', { name: 'View details' }).click();
  await payAndVerify(page, page.locator('.fund-detail').getByRole('button', { name: 'Pay May EMI' }), '2026-05', '2026-05-01', 100000);
  await expect(page.locator('.fund-detail')).toContainText(/Paid on/);
  await expect(page.locator('.fund-detail')).toContainText('June 2026 EMI');
  await expect(homeRow.locator('.loan-details-due')).toHaveCount(0);
  await expect(homeRow).not.toHaveClass(/due-loan/);
  await expect(homeRow.locator('.loan-progress')).toContainText('5 of 6 EMI months completed');
  await expect(homeRow.locator('.loan-progress i')).toHaveAttribute('style', /--loan-progress:\s*83%/);
  await page.locator('.fund-detail .close').click();
  await page.locator('.money-modal .close').click();
  await page.locator('.story-carousel-card').first().click();
  await page.getByTestId('view-included-commitments').click();
  await expect(page.getByRole('dialog').filter({ hasText: 'INCLUDED COMMITMENTS' }).locator('.evidence-row', { hasText: 'Home loan' })).not.toHaveClass(/due-loan-commitment/);

  await startFromHome(page, request, '2026-06-01T09:00:00Z');
  await page.getByRole('button', { name: /Your money/ }).click();
  await addLoan(page, { name: 'Second loan', type: 'PERSONAL', principal: '30000', emi: '5000', tenure: '6', firstDueDate: '2026-06-01', lender: 'Example Bank' });
  await loans.locator('.loan-row', { hasText: 'Home loan' }).getByRole('button', { name: 'View details' }).click();
  await page.locator('.fund-detail').getByRole('button', { name: 'Skip June EMI' }).click();
  await expect(page.locator('.loan-form').getByLabel('Bank penalty amount')).toBeVisible();
  await page.getByRole('button', { name: 'Confirm skip' }).click();
  await expect(page.locator('.fund-detail')).toContainText('July 2026 EMI');
  await page.locator('.fund-detail .close').click();
  await loans.locator('.loan-row', { hasText: 'Second loan' }).getByRole('button', { name: 'View details' }).click();
  await payAndVerify(page, page.locator('.fund-detail').getByRole('button', { name: 'Pay June EMI' }), '2026-06', '2026-06-01', 5000);

  await startFromHome(page, request, '2026-07-01T09:00:00Z');
  await page.getByRole('button', { name: /Your money/ }).click();
  await loans.locator('.loan-row', { hasText: 'Home loan' }).getByRole('button', { name: 'View details' }).click();
  await payAndVerify(page, page.locator('.fund-detail').getByRole('button', { name: 'Pay July EMI' }), '2026-07', '2026-07-01', 100000);
  await page.locator('.fund-detail .close').click();
  await expect(loans.locator('.loan-row', { hasText: 'Home loan' })).toHaveCount(0);
  await expect(loans.locator('.loan-row', { hasText: 'Second loan' })).toHaveCount(1);
  await page.getByTestId('closed-loans-toggle').click();
  await expect(loans.getByTestId(/closed-loan-/).filter({ hasText: 'Home loan' })).toBeVisible();
  await loans.getByTestId(/closed-loan-/).filter({ hasText: 'Home loan' }).getByRole('button', { name: 'See full payment history' }).click();
  await expect(page.locator('.fund-detail')).toContainText('May 2026 EMI');
  await expect(page.locator('.fund-detail')).toContainText('June 2026 EMI');
  await expect(page.locator('.fund-detail')).toContainText('skipped');
  await expect(page.locator('.fund-detail')).toContainText('July 2026 EMI');
  await page.locator('.fund-detail .close').click();
  await loans.locator('.loan-row', { hasText: 'Second loan' }).getByRole('button', { name: 'View details' }).click();
  await payAndVerify(page, page.locator('.fund-detail').getByRole('button', { name: 'Pay July EMI' }), '2026-07', '2026-07-01', 5000);

  await startFromHome(page, request, '2026-08-01T09:00:00Z');
  await page.getByRole('button', { name: /Your money/ }).click();
  await loans.locator('.loan-row', { hasText: 'Second loan' }).getByRole('button', { name: 'View details' }).click();
  await page.locator('.fund-detail').getByRole('button', { name: 'Pre-close loan' }).click();
  await page.getByLabel('Pre-closure settlement amount').fill('20000');
  await page.getByRole('button', { name: 'Confirm pre-closure' }).click();
  await expect(loans.getByTestId('closed-loans-toggle')).toContainText('Closed loans · 2');
  await page.locator('.fund-detail .close').click();
  await loans.getByTestId('closed-loans-toggle').click();
  await expect(loans.getByTestId(/closed-loan-/).filter({ hasText: 'Second loan' })).toBeVisible();
  await loans.getByTestId(/closed-loan-/).filter({ hasText: 'Second loan' }).getByRole('button', { name: 'See full payment history' }).click();
  await expect(page.locator('.fund-detail')).toContainText('Pre-closure settlement');
  await expect(page.locator('.fund-detail')).not.toContainText('September 2026 EMI');
  await page.locator('.fund-detail .close').click();

  await addLoan(page, { name: 'Education loan', type: 'EDUCATION', principal: '60000', emi: '10000', tenure: '6', firstDueDate: '2026-08-01', lender: 'Example Bank' });
  await loans.locator('.loan-row', { hasText: 'Education loan' }).getByRole('button', { name: 'View details' }).click();
  await page.locator('.fund-detail').getByRole('button', { name: 'Edit loan' }).click();
  await expect(page.getByLabel('Original loan principal')).toBeEnabled();
  await expect(page.getByLabel('Monthly EMI amount')).toBeEnabled();
  await page.getByLabel('Original loan principal').fill('65000');
  await page.getByLabel('Monthly EMI amount').fill('11000');
  await page.getByRole('button', { name: /Save changes|Update loan/ }).click();
  await loans.locator('.loan-row', { hasText: 'Education loan' }).getByRole('button', { name: 'View details' }).click();
  await payAndVerify(page, page.locator('.fund-detail').getByRole('button', { name: 'Pay August EMI' }), '2026-08', '2026-08-01', 11000);

  await startFromHome(page, request, '2026-09-01T09:00:00Z');
  await page.getByRole('button', { name: /Your money/ }).click();
  await loans.locator('.loan-row', { hasText: 'Education loan' }).getByRole('button', { name: 'View details' }).click();
  await page.locator('.fund-detail').getByRole('button', { name: 'Edit loan' }).click();
  await expect(page.getByLabel('Original loan principal')).toBeDisabled();
  await expect(page.getByLabel('Monthly EMI amount')).toBeDisabled();
  await expect(page.getByText(/payment history cannot be changed/i)).toBeVisible();
  await page.getByRole('button', { name: 'Restructure remaining loan' }).click();
  await page.getByLabel('Future monthly EMI amount').fill('7500');
  await page.getByLabel('Remaining tenure in months').fill('5');
  await page.getByRole('button', { name: 'Confirm restructure' }).click();
  await expect(loans.locator('.loan-row', { hasText: 'Education loan' })).toHaveCount(1);
  await expect(page.locator('.fund-detail')).toContainText('Restructured from September 2026');
  await expect(page.locator('.fund-detail')).toContainText('₹7,500');
  const auth = { headers: { Cookie: `WEB_SESSION=${sessionToken}` } };
  const loanList = await (await request.get(`${api}/api/web/loans`, auth)).json();
  const education = loanList.loans.find(loan => loan.loanName === 'Education loan');
  expect(education.restructuredFrom).toBe('2026-09-01');
  expect(education.totalTenureMonths).toBe(6);
  const educationHistory = await (await request.get(`${api}/api/web/loans/${education.id}/history`, auth)).json();
  expect(educationHistory.history.find(entry => entry.month === '2026-08')).toMatchObject({ status: 'PAID', plannedAmount: 11000, paidAmount: 11000, paidAt: '2026-08-01' });
  expect(educationHistory.history.filter(entry => entry.month >= '2026-09')).toHaveLength(5);
  expect(educationHistory.history.filter(entry => entry.month >= '2026-09').every(entry => entry.plannedAmount === 7500)).toBeTruthy();
});
