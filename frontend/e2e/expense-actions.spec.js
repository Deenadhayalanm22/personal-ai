import { test, expect } from '@playwright/test';

async function dashboard(page, { paginated = false, noReferences = false } = {}) {
  const requests = [];
  let deleted = false, failDelete = false;
  let expense = { id: 77, merchant: 'bookmyshow', amount: 480, transactionTime: '2026-09-28T00:00:00', category: 'Entertainment', subcategory: 'Movies', merchantId: noReferences ? null : 1, accountId: noReferences ? null : 2 };
  await page.route('**/api/web/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname;
    requests.push({ path, method: request.method(), body: request.postDataJSON(), date: url.searchParams.get('date'), cursor: url.searchParams.get('beforeId') });
    let json = { items: [], actions: [], loans: [], mutualFunds: [], stocks: [] };
    if (path.endsWith('/demo-profile')) json = { demoMode: false, canUseDemoMode: false };
    else if (path.endsWith('/calendar')) json = { month: '2026-09', currency: 'INR', timezone: 'Asia/Kolkata', totalSpend: 5000 + (deleted ? 0 : expense.amount), transactionCount: deleted ? 1 : 2, days: [] };
    else if (path.endsWith('/monthly-commitment')) json = { commitment: null };
    else if (path.endsWith('/activity')) json = { items: [{ type: 'COMMITMENT', id: 78, label: 'Chit fund', description: 'Commitment paid', date: '2026-09-28', amount: 5000 }, ...(deleted ? [] : [{ type: 'EXPENSE', id: 77, label: 'bookmyshow', description: 'Recorded expense', date: expense.transactionTime.slice(0, 10), amount: expense.amount }])] };
    else if (path.endsWith('/expenses/options')) json = { categories: [{ name: 'Entertainment', subcategories: ['Movies'] }], merchants: noReferences ? [] : [{ id: 1, name: 'bookmyshow' }], accounts: noReferences ? [] : [{ id: 2, name: 'HDFC' }] };
    else if (path.endsWith('/expenses')) json = { items: deleted ? [] : paginated && url.searchParams.has('date') && !url.searchParams.has('beforeId') ? [{ ...expense, id: 99 }] : [expense], nextBeforeId: paginated && url.searchParams.has('date') && !url.searchParams.has('beforeId') ? 99 : null };
    else if (path.endsWith('/expenses/77') && request.method() === 'PATCH') {
      const body = request.postDataJSON(); expense = { ...expense, ...body, transactionTime: body.transactionDate }; json = expense;
    } else if (path.endsWith('/expenses/77') && request.method() === 'DELETE') {
      if (failDelete) { failDelete = false; return route.fulfill({ status: 500, json: { message: 'Please retry deleting' } }); }
      deleted = true; return route.fulfill({ status: 204 });
    }
    await route.fulfill({ json });
  });
  await page.route('**/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.goto('/dashboard?month=2026-09');
  await expect(page.getByRole('button', { name: 'Edit bookmyshow', exact: true })).toBeVisible();
  return { requests, failNextDelete: () => { failDelete = true; } };
}

for (const width of [1280, 375]) {
  test(`Activity expense edit/delete and commitment exclusion at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 850 });
    const { requests } = await dashboard(page, { paginated: true });
    const activity = page.getByRole('region', { name: 'Activity', exact: true });
    await expect(activity.getByRole('button', { name: /(?:Edit|Delete) Chit fund/ })).toHaveCount(0);
    await page.getByRole('button', { name: 'Edit bookmyshow', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByLabel('Amount', { exact: true })).toHaveValue('480');
    await expect(dialog.getByRole('combobox', { name: 'Category', exact: true })).toHaveValue('Entertainment');
    await expect(dialog.getByRole('combobox', { name: 'Merchant', exact: true })).toHaveValue('1');
    await expect(dialog.getByRole('combobox', { name: 'Source account', exact: true })).toHaveValue('2');
    await dialog.getByLabel('Amount', { exact: true }).fill('600');
    await dialog.getByRole('button', { name: 'Save changes' }).click();
    await expect(dialog).toHaveCount(0);
    await expect(activity).toContainText('₹600');
    expect(requests.find(r => r.method === 'PATCH').body).toEqual({ amount: 600, transactionDate: '2026-09-28', category: 'Entertainment', subcategory: 'Movies', merchantId: 1, accountId: 2 });
    expect(requests.some(r => r.date === '2026-09-28' && r.cursor === '99')).toBe(true);
    await page.getByRole('button', { name: 'Delete bookmyshow', exact: true }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Cancel' }).click();
    expect(requests.filter(r => r.method === 'DELETE')).toHaveLength(0);
    await page.getByRole('button', { name: 'Delete bookmyshow', exact: true }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Delete', exact: true }).click();
    await expect(activity).not.toContainText('bookmyshow');
    await expect(activity).toContainText('Chit fund');
    for (const suffix of ['/activity', '/calendar', '/monthly-commitment', '/expenses']) expect(requests.filter(r => r.path.endsWith(suffix) && r.method === 'GET').length).toBeGreaterThanOrEqual(3);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });
}

test('unset expense references stay unset and failed delete can be retried', async ({ page }) => {
  const { requests, failNextDelete } = await dashboard(page, { noReferences: true });
  await page.getByRole('button', { name: 'Edit bookmyshow', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Amount', { exact: true }).fill('500');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(requests.find(r => r.method === 'PATCH').body).not.toHaveProperty('merchantId');
  expect(requests.find(r => r.method === 'PATCH').body).not.toHaveProperty('accountId');
  failNextDelete();
  await page.getByRole('button', { name: 'Delete bookmyshow', exact: true }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Delete', exact: true }).click();
  await expect(page.getByRole('alertdialog').getByRole('alert')).toBeVisible();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Delete', exact: true }).click();
  await expect(page.getByRole('alertdialog')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Delete bookmyshow', exact: true })).toHaveCount(0);
});
