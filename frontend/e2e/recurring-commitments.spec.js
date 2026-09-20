import { expect, test } from '@playwright/test';

test.describe.configure({ mode: 'serial' });

test('real API: edit and delete a monthly commitment from Your money', async ({ page, request }) => {
  const fixture = await request.post('http://localhost:8080/test/e2e/session');
  expect(fixture.ok()).toBeTruthy();
  const { sessionToken } = await fixture.json();
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);

  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-04-15T09:00:00Z' } })).ok()).toBeTruthy();
  await page.goto('/dashboard?month=2026-04');
  await page.getByRole('button', { name: /Your money/ }).click();
  const commitments = page.getByTestId('monthly-commitments-section');

  await commitments.getByRole('button', { name: '＋ Add a monthly commitment' }).click();
  await page.getByLabel('Name').fill('Home rent');
  await page.getByLabel('Monthly planning amount').fill('15000');
  await page.getByLabel('Expected day Optional').fill('5');
  await page.getByRole('button', { name: 'Add commitment' }).click();
  await expect(commitments).toContainText('Home rent');
  await expect(commitments).toContainText('₹15,000/mo');

  await commitments.getByRole('button', { name: 'Edit Home rent' }).click();
  await expect(page.getByRole('heading', { name: 'Edit monthly commitment' })).toBeVisible();
  await expect(page.getByLabel('Name')).toHaveValue('Home rent');
  await page.getByLabel('Monthly planning amount').fill('16000');
  await page.getByLabel('Expected day Optional').fill('7');
  const updateResponse = page.waitForResponse(response => response.url().includes('/api/web/recurring-commitments/') && response.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'Save changes' }).click();
  expect((await updateResponse).status()).toBe(200);
  await expect(commitments).toContainText('₹16,000/mo');
  await expect(commitments).toContainText('due around the 7');

  const deleteResponse = page.waitForResponse(response => response.url().includes('/api/web/recurring-commitments/') && response.request().method() === 'DELETE');
  await commitments.getByRole('button', { name: 'Delete Home rent' }).click();
  expect((await deleteResponse).status()).toBe(204);
  await expect(commitments).not.toContainText('Home rent');
  await expect(commitments).toContainText('No recurring commitments yet.');

  await page.reload();
  await page.getByRole('button', { name: /Your money/ }).click();
  await expect(page.getByTestId('monthly-commitments-section')).toContainText('No recurring commitments yet.');
});

test('real API: a due commitment is red in the story and can be marked done', async ({ page, request }) => {
  const fixture = await request.post('http://localhost:8080/test/e2e/session');
  expect(fixture.ok()).toBeTruthy();
  const { sessionToken } = await fixture.json();
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);

  expect((await request.post('http://localhost:8080/test/e2e/clock', { data: { instant: '2026-04-04T09:00:00Z' } })).ok()).toBeTruthy();
  await page.goto('/dashboard?month=2026-04');
  await page.getByRole('button', { name: /Your money/ }).click();
  const commitments = page.getByTestId('monthly-commitments-section');
  await commitments.getByRole('button', { name: '＋ Add a monthly commitment' }).click();
  await page.getByLabel('Name').fill('Internet bill');
  await page.getByLabel('Monthly planning amount').fill('999');
  await page.getByLabel('Expected day Optional').fill('5');
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

  const doneResponse = page.waitForResponse(response => response.url().includes('/recurring-commitments/') && response.url().includes('/done') && response.request().method() === 'POST');
  await dueCommitment.getByRole('button', { name: 'Mark commitment done' }).click();
  expect((await doneResponse).status()).toBe(200);
  await expect(dueCommitment).toContainText('done');
  await page.locator('.money-modal > .close').click();
  await page.locator('.story-carousel-card').first().click();
  await page.getByTestId('view-included-commitments').click();
  await expect(page.getByTestId(/included-recurring-commitment-/).locator('..')).toHaveClass(/confirmed-commitment/);
});
