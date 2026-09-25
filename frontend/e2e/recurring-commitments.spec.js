import { expect, test } from '@playwright/test';
test.use({ actionTimeout: 5000 });

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

test('weekly family support contributes every scheduled week and each due payment can be recorded', async ({ page, request }) => {
  test.setTimeout(120_000);
  const commitments = await openAuthenticatedMoney(page, request, '2026-09-01T09:00:00Z');
  await commitments.getByRole('button', { name: '＋ Add a commitment' }).click();
  await page.getByLabel('Name').fill('Wife family support');
  await page.getByLabel('Planning amount').fill('2000');
  await page.getByLabel('Frequency').selectOption('WEEK');
  await page.getByLabel('Next expected date').fill('2026-09-01');
  await page.getByRole('button', { name: 'Add commitment' }).click();
  const card = commitments.locator('[data-testid^="commitment-"]', { hasText: 'Wife family support' });
  await expect(card).toContainText('Due now');
  const sessionToken = (await page.context().cookies()).find(cookie => cookie.name === 'WEB_SESSION').value;
  const monthly = async month => (await request.get(`http://localhost:8080/api/web/expenses/monthly?month=${month}`, { headers: { Cookie: `WEB_SESSION=${sessionToken}` } })).json();
  const september = await monthly('2026-09');
  const story = september.stories.find(item => item.storyType === 'MONTHLY_COMMITMENT');
  const rows = story.evidence.byCard.commitment.transactions.filter(item => item.transactionId.startsWith('recurring_commitment:'));
  expect(story.evidence.byCard.commitment.totalAmount.value).toBe(10000);
  expect(rows.map(item => item.dateLabel)).toEqual(['1 Sept', '8 Sept', '15 Sept', '22 Sept', '29 Sept']);
  expect(rows.map(item => item.amount.value)).toEqual([2000, 2000, 2000, 2000, 2000]);
  await card.getByRole('button', { name: 'View details' }).click();
  const detail = page.getByRole('dialog').filter({ hasText: 'COMMITMENT DETAILS' });
  await expect(detail.getByTestId('cadence-progress').locator('.cadence-segment')).toHaveCount(5);
  const first = detail.getByTestId('dated-occurrence-2026-09-01');
  await expect(first).toHaveClass(/due-sip/);
  await first.getByRole('button', { name: 'Paid' }).click();
  await page.getByLabel('Completed on').fill('2026-09-01');
  await expect(page.getByLabel('Next expected date')).toHaveValue('2026-09-08');
  await page.getByRole('button', { name: 'Save completion' }).click();
  const afterPayment = await monthly('2026-09');
  expect(afterPayment.stories.find(item => item.storyType === 'MONTHLY_COMMITMENT').evidence.byCard.commitment.transactions
    .filter(item => item.transactionId.startsWith('recurring_commitment:'))).toHaveLength(5);
  await card.getByRole('button', { name: 'View details' }).click();
  await detail.getByRole('button', { name: /Sep 1 paid/ }).click();
  await expect(detail.getByTestId('dated-occurrence-2026-09-01')).toContainText('Paid');
  await detail.locator('.close').click();
  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-09-08T09:00:00Z' } })).ok()).toBeTruthy();
  await page.reload();
  await page.getByRole('button', { name: /Your money/ }).click();
  await page.locator('.money-modal > .close').click();
  await page.locator('.story-carousel-card').first().click();
  await page.getByTestId('view-included-commitments').click();
  const dueStoryRow = page.locator('.cadence-evidence-row', { hasText: 'Wife family support' });
  await expect(dueStoryRow).toHaveClass(/due-recurring-commitment/);
  await expect(page.locator('.cadence-evidence-row')).toHaveCount(1);
  await dueStoryRow.getByRole('button', { name: /Sep 8 due/ }).click();
  await expect(detail.getByTestId('dated-occurrence-2026-09-08')).toHaveClass(/due-sip/);
  await detail.getByTestId('dated-occurrence-2026-09-08').getByRole('button', { name: 'Skip' }).click();
  await detail.getByRole('button', { name: /Sep 8 skipped/ }).click();
  await expect(detail.getByTestId('dated-occurrence-2026-09-08')).toContainText('Skipped');
  const afterSkip = await monthly('2026-09');
  const remaining = afterSkip.stories.find(item => item.storyType === 'MONTHLY_COMMITMENT').evidence.byCard.commitment.transactions;
  expect(remaining.filter(item => item.transactionId.startsWith('recurring_commitment:'))).toHaveLength(4);
  expect(afterSkip.stories.find(item => item.storyType === 'MONTHLY_COMMITMENT').evidence.byCard.commitment.totalAmount.value).toBe(8000);
  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-10-01T09:00:00Z' } })).ok()).toBeTruthy();
  const october = await monthly('2026-10');
  const nextRows = october.stories.find(item => item.storyType === 'MONTHLY_COMMITMENT').evidence.byCard.commitment.transactions;
  expect(october.stories.find(item => item.storyType === 'MONTHLY_COMMITMENT').evidence.byCard.commitment.totalAmount.value).toBe(8000);
  expect(nextRows.filter(item => item.transactionId.startsWith('recurring_commitment:')).map(item => item.dateLabel)).toEqual(['6 Oct', '13 Oct', '20 Oct', '27 Oct']);
});

test('weekly commitment does not invent weeks before its first expected payment', async ({ page, request }) => {
  const commitments = await openAuthenticatedMoney(page, request, '2026-09-01T09:00:00Z');
  await commitments.getByRole('button', { name: '＋ Add a commitment' }).click();
  await page.getByLabel('Name').fill('Future family support');
  await page.getByLabel('Planning amount').fill('2000');
  await page.getByLabel('Frequency').selectOption('WEEK');
  await page.getByLabel('Next expected date').fill('2026-10-06');
  await page.getByRole('button', { name: 'Add commitment' }).click();
  const token = (await page.context().cookies()).find(cookie => cookie.name === 'WEB_SESSION').value;
  const response = await request.get('http://localhost:8080/api/web/expenses/monthly?month=2026-09', { headers: { Cookie: `WEB_SESSION=${token}` } });
  expect(response.ok()).toBeTruthy();
  const story = (await response.json()).stories.find(item => item.storyType === 'MONTHLY_COMMITMENT');
  expect(story.evidence.byCard.commitment.transactions.filter(item => item.transactionId.startsWith('recurring_commitment:'))).toHaveLength(0);
});

test('daily commitment uses one compact story row and a dated progress bar', async ({ page, request }) => {
  const commitments = await openAuthenticatedMoney(page, request, '2026-09-01T09:00:00Z');
  await commitments.getByRole('button', { name: '＋ Add a commitment' }).click();
  await page.getByLabel('Name').fill('Daily support');
  await page.getByLabel('Planning amount').fill('100');
  await page.getByLabel('Frequency').selectOption('DAY');
  await page.getByLabel('Next expected date').fill('2026-09-01');
  await page.getByRole('button', { name: 'Add commitment' }).click();
  const card = commitments.locator('[data-testid^="commitment-"]', { hasText: 'Daily support' });
  await card.getByRole('button', { name: 'View details' }).click();
  const detail = page.getByRole('dialog').filter({ hasText: 'COMMITMENT DETAILS' });
  await expect(detail.getByTestId('cadence-progress').locator('.cadence-segment')).toHaveCount(30);
  await detail.getByTestId('dated-occurrence-2026-09-01').getByRole('button', { name: 'Paid' }).click();
  await page.getByLabel('Completed on').fill('2026-09-01');
  await expect(page.getByLabel('Next expected date')).toHaveValue('2026-09-02');
  await page.getByRole('button', { name: 'Save completion' }).click();
  await page.locator('.money-modal > .close').click();
  await page.locator('.story-carousel-card').first().click();
  await page.getByTestId('view-included-commitments').click();
  const grouped = page.locator('.cadence-evidence-row', { hasText: 'Daily support' });
  await expect(grouped).toHaveCount(1);
  await expect(grouped.getByTestId('cadence-progress').locator('.cadence-segment')).toHaveCount(30);
  await grouped.getByRole('button', { name: /Sep 1 paid/ }).click();
  await expect(detail.getByTestId('dated-occurrence-2026-09-01')).toContainText('Paid');
});

test('every two weeks creates three September contributions in one progress bar', async ({ page, request }) => {
  const commitments = await openAuthenticatedMoney(page, request, '2026-09-01T09:00:00Z');
  await commitments.getByRole('button', { name: '＋ Add a commitment' }).click();
  await page.getByLabel('Name').fill('Fortnightly support');
  await page.getByLabel('Planning amount').fill('2000');
  await page.getByLabel('Frequency').selectOption('WEEK');
  await page.getByLabel('Every').fill('2');
  await page.getByLabel('Next expected date').fill('2026-09-01');
  await page.getByRole('button', { name: 'Add commitment' }).click();
  const card = commitments.locator('[data-testid^="commitment-"]', { hasText: 'Fortnightly support' });
  await card.getByRole('button', { name: 'View details' }).click();
  const detail = page.getByRole('dialog').filter({ hasText: 'COMMITMENT DETAILS' });
  await expect(detail.getByTestId('cadence-progress').locator('.cadence-segment')).toHaveCount(3);
  await expect(detail.getByRole('button', { name: /Sep 15 upcoming/ })).toBeVisible();
  await expect(detail.getByRole('button', { name: /Sep 29 upcoming/ })).toBeVisible();
  const token = (await page.context().cookies()).find(cookie => cookie.name === 'WEB_SESSION').value;
  const response = await request.get('http://localhost:8080/api/web/expenses/monthly?month=2026-09', { headers: { Cookie: `WEB_SESSION=${token}` } });
  expect(response.ok()).toBeTruthy();
  const rows = (await response.json()).stories.find(item => item.storyType === 'MONTHLY_COMMITMENT').evidence.byCard.commitment.transactions;
  expect(rows.filter(item => item.transactionId.startsWith('recurring_commitment:')).map(item => item.dateLabel)).toEqual(['1 Sept', '15 Sept', '29 Sept']);
});


test('recurring commitment details and payment lifecycle', async ({ page, request }) => {
  test.setTimeout(120_000);
  await test.step('records an early bike service while retaining its plan and chosen next date', async () => {
      const commitments = await openAuthenticatedMoney(page, request);
      await addFlexibleCommitment(page, commitments, { label: 'Bike service', amount: '2000', intervalMonths: 4, nextExpectedDate: '2027-01-15' });
      const bikeService = commitments.locator('[data-testid^="commitment-"]', { hasText: 'Bike service' });
      await expect(bikeService).toContainText('₹2,000');
      await expect(bikeService).toContainText('Every 4 months');
      await expect(bikeService).toContainText(/Next expected:.*2027/);

      await bikeService.getByRole('button', { name: 'View details' }).click();
      await expect(page.getByRole('dialog').getByRole('button', { name: 'Paid' })).toBeDisabled();
      await expect(page.getByRole('dialog').getByRole('button', { name: 'Skip' })).toBeDisabled();
      await page.getByRole('dialog').getByRole('button', { name: 'Record an early payment' }).click();
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
      await expect(history).toContainText(/Paid on.*21/);
  });

  await test.step('allows a two-month internet recharge without overwriting its usual three-month cadence', async () => {
      const commitments = await openAuthenticatedMoney(page, request);
      await addFlexibleCommitment(page, commitments, { label: 'Internet recharge', amount: '799', intervalMonths: 3, nextExpectedDate: '2026-12-15', flexible: true });
      const recharge = commitments.locator('[data-testid^="commitment-"]', { hasText: 'Internet recharge' });
      await expect(recharge).toContainText('Usually every 3 months');

      await recharge.getByRole('button', { name: 'View details' }).click();
      await expect(page.getByRole('dialog').getByRole('button', { name: 'Paid' })).toBeDisabled();
      await expect(page.getByRole('dialog').getByRole('button', { name: 'Skip' })).toBeDisabled();
      await page.getByRole('dialog').getByRole('button', { name: 'Record an early payment' }).click();
      await page.getByLabel('Actual amount').fill('699');
      await page.getByLabel('Completed on').fill('2026-09-21');
      await page.getByLabel('Next expected date').fill('2026-11-21');
      await page.getByRole('button', { name: 'Save completion' }).click();

      await expect(recharge).toContainText('Usually every 3 months');
      await expect(recharge).toContainText(/Next expected:.*2026/);
      await recharge.getByRole('button', { name: 'View details' }).click();
      const history = page.locator('.fund-detail');
      await expect(history).toContainText('₹699');
      await expect(history).toContainText(/Paid on.*21/);
  });

  await test.step('real API: edit and delete a commitment from Your money', async () => {
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

    await commitments.locator('[data-testid^="commitment-"]', { hasText: 'Home rent' }).getByRole('button', { name: 'View details' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Edit commitment' }).click();
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
    await commitments.locator('[data-testid^="commitment-"]', { hasText: 'Home rent' }).getByRole('button', { name: 'View details' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Delete commitment' }).click();
    expect((await deleteResponse).status()).toBe(204);
    await expect(commitments).not.toContainText('Home rent');
    await expect(commitments).toContainText('No commitments yet.');

    await page.reload();
    await page.getByRole('button', { name: /Your money/ }).click();
    await expect(page.getByTestId('commitments-section')).toContainText('No commitments yet.');
  });

  await test.step('real API: a due commitment can record its actual completion', async () => {
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
    const upcomingBill = commitments.locator('[data-testid^="commitment-"]', { hasText: 'Internet bill' });
    await upcomingBill.getByRole('button', { name: 'View details' }).click();
    const upcomingDetails = page.getByRole('dialog').filter({ hasText: 'COMMITMENT DETAILS' });
    await expect(upcomingDetails.getByRole('button', { name: 'Paid' })).toBeDisabled();
    await expect(upcomingDetails.getByRole('button', { name: 'Skip' })).toBeDisabled();
    const upcomingId = (await (await request.get('http://localhost:8080/api/web/recurring-commitments', {
      headers: { Cookie: `WEB_SESSION=${sessionToken}` }
    })).json()).items.find(item => item.label === 'Internet bill').id;
    const prematureSkip = await request.post(`http://localhost:8080/api/web/recurring-commitments/${upcomingId}/occurrences/2026-04/skip`, {
      headers: { Cookie: `WEB_SESSION=${sessionToken}` }
    });
    expect(prematureSkip.status()).toBe(400);
    await upcomingDetails.getByRole('button', { name: '×' }).click();

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
    await expect(dueEvidence).toHaveCSS('border-top-color', 'rgb(228, 164, 156)');
    await page.getByTestId(/included-recurring-commitment-/).click();
    const dueCommitment = commitments.locator('.loan-row', { hasText: 'Internet bill' });
    await expect(dueCommitment).toBeFocused();
    await expect(dueCommitment).toHaveClass(/due-recurring/);
    await expect(dueCommitment).not.toHaveCSS('border-top-color', 'rgb(228, 164, 156)');
    const dueActions = dueCommitment.locator('.loan-details-area');
    await expect(dueActions).toContainText('Due now');
    await expect(dueActions).toHaveCSS('border-top-color', 'rgb(228, 164, 156)');
    const cardBounds = await dueCommitment.boundingBox();
    const actionBounds = await dueActions.boundingBox();
    const detailsBounds = await dueCommitment.getByRole('button', { name: 'View details' }).boundingBox();
    expect(actionBounds.width).toBeLessThan(cardBounds.width / 2);
    expect(actionBounds.x).toBeGreaterThan(cardBounds.x + cardBounds.width / 2);
    expect(detailsBounds.x).toBeGreaterThan(cardBounds.x + cardBounds.width / 2);
    expect(detailsBounds.x + detailsBounds.width).toBeLessThanOrEqual(cardBounds.x + cardBounds.width);

    const doneResponse = page.waitForResponse(response => response.url().includes('/recurring-commitments/') && response.url().includes('/occurrences/complete') && response.request().method() === 'POST');
    await dueCommitment.getByRole('button', { name: 'View details' }).click();
    await expect(page.getByRole('dialog').getByRole('button', { name: 'Paid' })).toBeEnabled();
    await expect(page.getByRole('dialog').getByRole('button', { name: 'Skip' })).toBeEnabled();
    await page.getByRole('dialog').getByRole('button', { name: 'Paid' }).click();
    await page.getByRole('dialog').getByLabel('Completed on').fill('2026-04-05');
    await page.getByRole('dialog').getByRole('button', { name: 'Save completion' }).click();
    expect((await doneResponse).status()).toBe(200);
    await expect(dueCommitment).toContainText('Completed');
    await expect(dueCommitment).not.toHaveClass(/due-recurring/);
    await expect(dueActions).not.toHaveClass(/due/);
    await dueCommitment.getByRole('button', { name: 'View details' }).click();
    const commitmentHistory = page.locator('.fund-detail').filter({ hasText: 'Payment history' });
    await expect(commitmentHistory).toHaveClass(/fund-detail/);
    await expect(commitmentHistory.locator('.investment-history').filter({ hasText: 'Payment history' })).toBeVisible();
    await expect(commitmentHistory).toContainText('April 2026 payment');
    await expect(commitmentHistory).toContainText('Paid on');
    await commitmentHistory.getByRole('button', { name: '×' }).click();
    await page.locator('.money-modal > .close').click();
    await page.locator('.story-carousel-card').first().click();
    await page.getByTestId('view-included-commitments').click();
    await expect(page.getByTestId(/included-recurring-commitment-/).locator('..')).not.toHaveClass(/due-commitment/);
  });

  await test.step('real API: extra support is recorded separately from the usual amount', async () => {
    const fixture = await request.post('http://localhost:8080/test/e2e/session');
    expect(fixture.ok()).toBeTruthy();
    const { sessionToken } = await fixture.json();
    await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);
    expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-09-21T09:00:00Z' } })).ok()).toBeTruthy();
    await page.goto('/dashboard?month=2026-09');
    await page.getByRole('button', { name: /Your money/ }).click();
    const section = page.getByTestId('commitments-section');
    await section.getByRole('button', { name: '＋ Add a commitment' }).click();
    await page.getByLabel('Name').fill('Family support');
    await page.getByLabel('Planning amount').fill('10000');
    await page.getByLabel('Next expected date').fill('2026-09-21');
    await page.getByRole('button', { name: 'Add commitment' }).click();
    const card = section.locator('[data-testid^="commitment-"]', { hasText: 'Family support' });
    await expect(card.getByRole('button')).toHaveCount(1);
    const cardBounds = await card.boundingBox();
    const linkBounds = await card.getByRole('button', { name: 'View details' }).boundingBox();
    expect(linkBounds.x).toBeGreaterThan(cardBounds.x + cardBounds.width / 2);
    expect(linkBounds.x + linkBounds.width).toBeLessThanOrEqual(cardBounds.x + cardBounds.width);
    await card.getByRole('button', { name: 'View details' }).click();
    const detail = page.getByRole('dialog').filter({ hasText: 'COMMITMENT DETAILS' });
    await detail.getByRole('button', { name: 'Paid' }).click();
    await page.getByLabel('Completed on').fill('2026-09-21');
    await page.getByRole('button', { name: 'Save completion' }).click();
    await card.getByRole('button', { name: 'View details' }).click();
    await detail.getByRole('button', { name: '＋ Add extra' }).click();
    const extraPopup = page.getByRole('dialog').filter({ hasText: 'EXTRA PAYMENT' });
    await extraPopup.getByLabel('Extra amount').fill('2000');
    await extraPopup.getByLabel('Reason').fill('Birthday support');
    await extraPopup.getByRole('button', { name: 'Save extra' }).click();
    await expect(detail).toContainText('Extra ₹2,000');
    await expect(detail).toContainText('Birthday support');
    await expect(detail).toContainText('₹10,000');
    await detail.getByRole('button', { name: '×' }).click();
    await card.getByRole('button', { name: 'View details' }).click();
    await expect(detail).toContainText('Birthday support');
  });

  await test.step('real API: a skipped month appears in commitment history', async () => {
    const fixture = await request.post('http://localhost:8080/test/e2e/session');
    expect(fixture.ok()).toBeTruthy();
    const { sessionToken } = await fixture.json();
    await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);
    expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-09-21T09:00:00Z' } })).ok()).toBeTruthy();
    await page.goto('/dashboard?month=2026-09');
    await page.getByRole('button', { name: /Your money/ }).click();
    const section = page.getByTestId('commitments-section');
    await section.getByRole('button', { name: '＋ Add a commitment' }).click();
    await page.getByLabel('Name').fill('Family support');
    await page.getByLabel('Planning amount').fill('10000');
    await page.getByLabel('Next expected date').fill('2026-09-21');
    await page.getByRole('button', { name: 'Add commitment' }).click();
    const card = section.locator('[data-testid^="commitment-"]', { hasText: 'Family support' });
    await expect(card).toHaveClass(/due-recurring/);
    await card.getByRole('button', { name: 'View details' }).click();
    const detail = page.getByRole('dialog').filter({ hasText: 'COMMITMENT DETAILS' });
    await detail.getByRole('button', { name: 'Skip' }).click();
    await expect(detail).toContainText('Skipped');
    await expect(detail).toContainText('₹10,000');
    await expect(card).not.toHaveClass(/due-recurring/);
    await detail.getByRole('button', { name: '×' }).click();
    await page.locator('.money-modal > .close').click();
    await page.locator('.story-carousel-card').first().click();
    await page.getByRole('button', { name: 'Next →' }).click();
    if (await page.getByTestId('view-included-commitments').count()) await page.getByTestId('view-included-commitments').click();
    await expect(page.locator('.evidence-row.due-recurring-commitment')).toHaveCount(0);
  });

});
