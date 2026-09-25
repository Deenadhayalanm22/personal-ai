import { expect, test } from '@playwright/test';
test.use({ actionTimeout: 5000 });

const api = 'http://localhost:8080';
async function clock(request, date) {
  expect((await request.post(`${api}/test/e2e/clock`, { data: { instant: `${date}T09:00:00Z` } })).ok()).toBeTruthy();
}
async function month(page, request, date) {
  await clock(request, date);
  await page.goto(`/dashboard?month=${date.slice(0, 7)}`);
  await page.getByRole('button', { name: /Your money/ }).click();
}

test('yearly car insurance savings survive a skipped November and fund part of the payment', async ({ page, request }) => {
  test.setTimeout(180_000);
  const session = await request.post(`${api}/test/e2e/session`);
  expect(session.ok()).toBeTruthy();
  const { sessionToken } = await session.json();
  await page.context().addCookies([{ name: 'WEB_SESSION', value: sessionToken, domain: 'localhost', path: '/', httpOnly: true }]);
  await month(page, request, '2026-09-01');

  const section = page.getByTestId('commitments-section');
  await section.getByRole('button', { name: '＋ Add a commitment' }).click();
  await page.getByLabel('Name').fill('Car insurance');
  await page.getByLabel('Planning amount').fill('20000');
  await page.getByLabel('Frequency').selectOption('YEAR');
  await page.getByLabel('Next expected date').fill('2027-09-01');
  await page.getByRole('button', { name: 'Add commitment' }).click();
  const card = section.locator('[data-testid^="commitment-"]', { hasText: 'Car insurance' });
  await card.getByRole('button', { name: 'View details' }).click();
  const detail = page.getByRole('dialog').filter({ hasText: 'COMMITMENT DETAILS' });
  const savings = detail.getByTestId('commitment-savings');
  await expect(savings).toBeVisible();
  expect(await savings.evaluate(element => element.compareDocumentPosition(element.previousElementSibling) & Node.DOCUMENT_POSITION_PRECEDING)).toBeTruthy();
  await savings.getByRole('button', { name: 'Start saving for this payment' }).click();
  const setup = page.getByRole('dialog', { name: 'Savings plan' });
  await expect(setup.getByRole('row', { name: /Saving months/ })).toContainText('12');
  await expect(setup.getByLabel('Monthly amount to set aside')).toHaveValue('1666.67');
  await expect(setup.getByRole('row', { name: /Last month/ })).toContainText('₹1,666.63');
  const creation = page.waitForResponse(response => response.url().endsWith('/savings') && response.request().method() === 'POST');
  await setup.getByRole('button', { name: 'Start saving plan' }).click();
  const created = await creation;
  expect(created.status(), await created.text()).toBe(201);
  await expect(setup).toBeHidden();
  await expect(savings.getByRole('row', { name: /Set aside/ }).first()).toContainText('₹0.00');
  await expect(savings.locator('.savings-segment')).toHaveCount(12);
  await expect(detail.getByLabel('Money actually set aside')).toHaveValue('1666.67');
  await detail.getByLabel('Money actually set aside').fill('1666.67');
  await detail.getByRole('button', { name: 'Pay savings' }).click();
  await expect(savings.getByRole('row', { name: /Set aside/ }).first()).toContainText('₹1,666.67');
  await expect(savings.locator('.savings-segment').first()).toHaveClass(/saved/);

  await month(page, request, '2026-10-01');
  await page.locator('.money-modal > .close').click();
  await page.locator('.story-carousel-card').first().click();
  await page.getByTestId('view-included-commitments').click();
  const savingsReview = page.getByTestId(/included-recurring-commitment-/);
  await expect(savingsReview).toContainText('Review');
  await expect(savingsReview.locator('..')).toHaveClass(/due-recurring-commitment/);
  await savingsReview.click();
  await expect(card).toHaveClass(/due-recurring/);
  await card.getByRole('button', { name: 'View details' }).click();
  await expect(page.getByRole('dialog').getByLabel('Money actually set aside')).toHaveValue('1666.67');
  await page.getByRole('dialog').getByLabel('Money actually set aside').fill('1666.67');
  await page.getByRole('dialog').getByRole('button', { name: 'Pay savings' }).click();
  await expect(savings.getByRole('row', { name: /Set aside/ }).first()).toContainText('₹3,333.34');

  await month(page, request, '2026-11-01');
  await card.getByRole('button', { name: 'View details' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Skip savings' }).click();
  await expect(savings.getByRole('row', { name: /Expected gap/ })).toContainText('₹1,666.67');
  await expect(savings.getByRole('img', { name: 'November 2026: skipped' })).toHaveClass(/skipped/);
  const plansAfterSkip = await (await request.get(`${api}/api/web/recurring-commitments/savings`, { headers: { Cookie: `WEB_SESSION=${sessionToken}` } })).json();
  const plan = plansAfterSkip.find(item => item.targetAmount === 20000);
  expect(plan.monthlyAmount).toBe(1666.67);
  expect(plan.saved).toBe(3333.34);
  expect(plan.history.find(item => item.month === '2026-11').status).toBe('SKIPPED');
  const novemberStory = await request.get(`${api}/api/web/expenses/monthly?month=2026-11`, { headers: { Cookie: `WEB_SESSION=${sessionToken}` } });
  expect(novemberStory.ok()).toBeTruthy();
  const novemberCommitment = (await novemberStory.json()).stories.find(item => item.storyType === 'MONTHLY_COMMITMENT');
  expect(novemberCommitment.evidence.byCard.commitment.transactions).toEqual([]);
  expect(novemberCommitment.evidence.byCard.commitment.totalAmount.value).toBe(0);
  expect(novemberCommitment.evidence.byCard['next-commitment'].transactions).toEqual(expect.arrayContaining([
    expect.objectContaining({ transactionId: `commitment_savings:${plan.commitmentId}` })
  ]));

  for (let year = 2026, number = 12; year < 2027 || number <= 8; number++) {
    if (number === 13) { year = 2027; number = 1; }
    const scheduled = `${year}-${String(number).padStart(2, '0')}`;
    await clock(request, `${scheduled}-01`);
    const response = await request.post(`${api}/api/web/recurring-commitments/${plan.commitmentId}/savings/record`, {
      headers: { Cookie: `WEB_SESSION=${sessionToken}` }, data: { month: scheduled, amount: scheduled === '2027-08' ? 1666.63 : 1666.67 }
    });
    expect(response.ok(), `${scheduled}: ${await response.text()}`).toBeTruthy();
  }

  await month(page, request, '2027-09-01');
  await page.locator('.money-modal > .close').click();
  await page.locator('.story-carousel-card').first().click();
  await page.getByTestId('view-included-commitments').click();
  const billReview = page.getByTestId(/included-recurring-commitment-/);
  await expect(billReview.locator('..')).toContainText('₹20,000');
  await expect(billReview.locator('..')).toContainText('₹18,333.33 recorded as set aside');
  await billReview.click();
  await card.getByRole('button', { name: 'View details' }).click();
  await expect(savings.getByRole('row', { name: /Set aside/ }).first()).toContainText('₹18,333.33');
  await page.getByRole('dialog').getByRole('button', { name: 'Paid' }).click();
  await page.getByLabel('Completed on').fill('2027-09-01');
  await page.getByLabel('Next expected date').fill('2028-09-01');
  await expect(page.getByLabel('Use from recorded savings')).toHaveValue('18333.33');
  await page.getByRole('button', { name: 'Save completion' }).click();
  await card.getByRole('button', { name: 'View details' }).click();
  await expect(detail).toContainText('₹18,333.33 from savings');
  await expect(detail).toContainText('₹1,666.67 from other money');
  await expect(detail.getByRole('button', { name: 'Save for the next payment' })).toBeVisible();
});
