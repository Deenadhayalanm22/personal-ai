import { expect, test } from '@playwright/test';

test.describe.configure({ mode: 'serial' });

test.describe('flexible commitment recurrence', () => {
  async function openAuthenticatedMoney(page, request, instant = '2026-09-21T09:00:00Z') {
    const fixture = await request.post('http://localhost:8080/test/e2e/session');
    expect(fixture.ok()).toBeTruthy();
    const { sessionToken } = await fixture.json();
    await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);
    expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant } })).ok()).toBeTruthy();
    await page.goto('/dashboard?month=2026-09');
    await page.getByRole('button', { name: /Your money/ }).click();
    return page.getByTestId('commitments-section');
  }

  async function addFlexibleCommitment(page, commitments, { label, amount, intervalMonths, nextExpectedDate, flexible = false }) {
    await commitments.getByRole('button', { name: '＋ Add a commitment' }).click();
    await page.getByLabel('Name').fill(label);
    await page.getByLabel('Planning amount').fill(amount);
    await page.getByLabel('Frequency').selectOption('MONTH');
    await page.getByLabel('Every').fill(String(intervalMonths));
    await page.getByLabel('Next expected date').fill(nextExpectedDate);
    if (flexible) await page.getByLabel('Flexible reminder').check();
    await page.getByRole('button', { name: 'Add commitment' }).click();
  }

  test('records an early bike service while retaining its plan and chosen next date', async ({ page, request }) => {
    const commitments = await openAuthenticatedMoney(page, request);
    await addFlexibleCommitment(page, commitments, { label: 'Bike service', amount: '2000', intervalMonths: 4, nextExpectedDate: '2027-01-15' });
    const bikeService = commitments.locator('[data-testid^="commitment-"]', { hasText: 'Bike service' });
    await expect(bikeService).toContainText('₹2,000');
    await expect(bikeService).toContainText('Every 4 months');
    await expect(bikeService).toContainText(/Next expected:.*2027/);

    await bikeService.getByRole('button', { name: 'Mark completed' }).click();
    await page.getByLabel('Actual amount').fill('2400');
    await page.getByLabel('Completed on').fill('2026-09-21');
    await page.getByLabel('Next expected date').fill('2027-01-21');
    await page.getByRole('button', { name: 'Save completion' }).click();

    await expect(bikeService).toContainText('₹2,000');
    await expect(bikeService).toContainText('Every 4 months');
    await expect(bikeService).toContainText(/Next expected:.*2027/);
    await bikeService.getByRole('button', { name: 'View details' }).click();
    const history = page.locator('.fund-detail');
    await expect(history).toContainText('₹2,400');
    await expect(history).toContainText(/Done on.*21/);
  });

  test('allows a two-month internet recharge without overwriting its usual three-month cadence', async ({ page, request }) => {
    const commitments = await openAuthenticatedMoney(page, request);
    await addFlexibleCommitment(page, commitments, { label: 'Internet recharge', amount: '799', intervalMonths: 3, nextExpectedDate: '2026-12-15', flexible: true });
    const recharge = commitments.locator('[data-testid^="commitment-"]', { hasText: 'Internet recharge' });
    await expect(recharge).toContainText('Usually every 3 months');

    await recharge.getByRole('button', { name: 'Mark completed' }).click();
    await page.getByLabel('Actual amount').fill('699');
    await page.getByLabel('Completed on').fill('2026-09-21');
    await page.getByLabel('Next expected date').fill('2026-11-21');
    await page.getByRole('button', { name: 'Save completion' }).click();

    await expect(recharge).toContainText('Usually every 3 months');
    await expect(recharge).toContainText(/Next expected:.*2026/);
    await recharge.getByRole('button', { name: 'View details' }).click();
    const history = page.locator('.fund-detail');
    await expect(history).toContainText('₹699');
    await expect(history).toContainText(/Done on.*21/);
  });
});

test('real API: edit and delete a commitment from Your money', async ({ page, request }) => {
  const fixture = await request.post('http://localhost:8080/test/e2e/session');
  expect(fixture.ok()).toBeTruthy();
  const { sessionToken } = await fixture.json();
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);

  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-04-15T09:00:00Z' } })).ok()).toBeTruthy();
  await page.goto('/dashboard?month=2026-04');
  await page.getByRole('button', { name: /Your money/ }).click();
  const commitments = page.getByTestId('commitments-section');

  await commitments.getByRole('button', { name: '＋ Add a commitment' }).click();
  await page.getByLabel('Name').fill('Home rent');
  await page.getByLabel('Planning amount').fill('15000');
  await page.getByLabel('Next expected date').fill('2026-04-05');
  await page.getByRole('button', { name: 'Add commitment' }).click();
  await expect(commitments).toContainText('Home rent');
  await expect(commitments).toContainText('₹15,000');

  await commitments.getByRole('button', { name: 'Edit Home rent' }).click();
  await expect(page.getByRole('heading', { name: 'Edit commitment' })).toBeVisible();
  await expect(page.getByLabel('Name')).toHaveValue('Home rent');
  await page.getByLabel('Planning amount').fill('16000');
  await page.getByLabel('Next expected date').fill('2026-04-07');
  const updateResponse = page.waitForResponse(response => response.url().includes('/api/web/recurring-commitments/') && response.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'Save changes' }).click();
  expect((await updateResponse).status()).toBe(200);
  await expect(commitments).toContainText('₹16,000');
  await expect(commitments).toContainText(/Next expected:.*7.*2026/);

  const deleteResponse = page.waitForResponse(response => response.url().includes('/api/web/recurring-commitments/') && response.request().method() === 'DELETE');
  await commitments.getByRole('button', { name: 'Delete Home rent' }).click();
  expect((await deleteResponse).status()).toBe(204);
  await expect(commitments).not.toContainText('Home rent');
  await expect(commitments).toContainText('No commitments yet.');

  await page.reload();
  await page.getByRole('button', { name: /Your money/ }).click();
  await expect(page.getByTestId('commitments-section')).toContainText('No commitments yet.');
});

test('real API: a due commitment can record its actual completion', async ({ page, request }) => {
  const fixture = await request.post('http://localhost:8080/test/e2e/session');
  expect(fixture.ok()).toBeTruthy();
  const { sessionToken } = await fixture.json();
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);

  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-04-04T09:00:00Z' } })).ok()).toBeTruthy();
  await page.goto('/dashboard?month=2026-04');
  await page.getByRole('button', { name: /Your money/ }).click();
  const commitments = page.getByTestId('commitments-section');
  await commitments.getByRole('button', { name: '＋ Add a commitment' }).click();
  await page.getByLabel('Name').fill('Internet bill');
  await page.getByLabel('Planning amount').fill('999');
  await page.getByLabel('Next expected date').fill('2026-04-05');
  await page.getByRole('button', { name: 'Add commitment' }).click();
  await expect(commitments).toContainText('Internet bill');

  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-04-05T09:00:00Z' } })).ok()).toBeTruthy();
  const refreshedStories = page.waitForResponse(response => response.url().includes('/api/web/expenses/monthly?month=2026-04') && response.status() === 200);
  await page.reload();
  await refreshedStories;
  await page.locator('.story-carousel-card').first().click();
  const commitmentsResponse = page.waitForResponse(response => response.url().endsWith('/api/web/recurring-commitments') && response.status() === 200);
  await page.getByTestId('view-included-commitments').click();
  const refreshedCommitments = await (await commitmentsResponse).json();
  expect(refreshedCommitments.items.find(item => item.label === 'Internet bill').currentOccurrence.status).toBe('DUE');
  const dueEvidence = page.getByTestId(/included-recurring-commitment-/).locator('..');
  await expect(dueEvidence).toHaveClass(/due-commitment/);
  await page.getByTestId(/included-recurring-commitment-/).click();
  const dueCommitment = commitments.locator('.loan-row', { hasText: 'Internet bill' });
  await expect(dueCommitment).toBeFocused();
  await expect(dueCommitment).toHaveClass(/due-recurring/);

  const doneResponse = page.waitForResponse(response => response.url().includes('/recurring-commitments/') && response.url().includes('/occurrences/complete') && response.request().method() === 'POST');
  await dueCommitment.getByRole('button', { name: 'Mark completed' }).click();
  await page.getByRole('dialog').getByLabel('Completed on').fill('2026-04-05');
  await page.getByRole('dialog').getByRole('button', { name: 'Save completion' }).click();
  expect((await doneResponse).status()).toBe(200);
  await expect(dueCommitment).toContainText('Completed');
  await dueCommitment.getByRole('button', { name: 'View details' }).click();
  const commitmentHistory = page.locator('.fund-detail').filter({ hasText: 'Payment history' });
  await expect(commitmentHistory).toHaveClass(/fund-detail/);
  await expect(commitmentHistory.locator('.investment-history')).toBeVisible();
  await expect(commitmentHistory).toContainText('April 2026 payment');
  await expect(commitmentHistory).toContainText('Done on');
  await commitmentHistory.getByRole('button', { name: '×' }).click();
  await page.locator('.money-modal > .close').click();
  await page.locator('.story-carousel-card').first().click();
  await page.getByTestId('view-included-commitments').click();
  await expect(page.getByTestId(/included-recurring-commitment-/).locator('..')).not.toHaveClass(/due-commitment/);
});

test.skip('legacy monthly due-day schedule is superseded by next expected date', async ({ page, request }) => {
  const fixture = await request.post('http://localhost:8080/test/e2e/session');
  expect(fixture.ok()).toBeTruthy();
  const { sessionToken } = await fixture.json();
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);
  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-04-15T09:00:00Z' } })).ok()).toBeTruthy();
  await page.goto('/dashboard?month=2026-04');
  await page.getByRole('button', { name: /Your money/ }).click();
  const commitments = page.getByTestId('commitments-section');

  for (const [label, dueDay] of [['First-day bill', '1'], ['Tenth-day bill', '10'], ['Twenty-fifth bill', '25']]) {
    await commitments.getByRole('button', { name: '＋ Add a commitment' }).click();
    await page.getByLabel('Name').fill(label);
    await page.getByLabel('Planning amount').fill('100');
    await page.getByLabel('Next expected date').fill(`2026-04-${dueDay.padStart(2,'0')}`);
    await page.getByRole('button', { name: 'Add commitment' }).click();
  }
  await expect(commitments).toContainText('Twenty-fifth bill');

  async function openCurrentEvidence() {
    const storiesResponse = page.waitForResponse(response => response.url().includes('/api/web/expenses/monthly?month=2026-04') && response.status() === 200);
    await page.reload();
    await storiesResponse;
    await page.locator('.story-carousel-card').first().click();
    await page.getByTestId('view-included-commitments').click();
  }

  await page.locator('.money-modal > .close').click();
  await openCurrentEvidence();
  await expect(page.locator('.evidence-row.due-commitment')).toHaveCount(0);

  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-04-25T09:00:00Z' } })).ok()).toBeTruthy();
  await openCurrentEvidence();
  const aprilDue = page.locator('.evidence-row.due-commitment');
  await expect(aprilDue).toHaveCount(1);
  await expect(aprilDue).toContainText('Twenty-fifth bill');
  await aprilDue.getByRole('button', { name: 'Review' }).click();
  await page.getByRole('button', { name: 'Mark completed' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Save completion' }).click();
  await page.locator('.money-modal > .close').click();

  // April has 30 days. At the month end, the two past-at-creation commitments remain non-due.
  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-04-30T09:00:00Z' } })).ok()).toBeTruthy();
  await openCurrentEvidence();
  await expect(page.locator('.evidence-row.due-commitment')).toHaveCount(0);

  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-05-01T09:00:00Z' } })).ok()).toBeTruthy();
  await openCurrentEvidence();
  const mayDue = page.locator('.evidence-row.due-commitment');
  await expect(mayDue).toHaveCount(1);
  await expect(mayDue).toContainText('First-day bill');
  await mayDue.getByRole('button', { name: 'Review' }).click();
  await page.getByRole('button', { name: 'Mark completed' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Save completion' }).click();
  await expect(commitments.locator('.loan-row', { hasText: 'First-day bill' })).toContainText('done');
});
