import { expect, test } from '@playwright/test';

test('India sign-in validates the local mobile number and submits +91', async ({ page }) => {
  const requests = [];
  await page.route('**/api/web/auth/login-link', async (route) => {
    requests.push(route.request().postDataJSON());
    await route.fulfill({ status: 202, contentType: 'application/json', body: JSON.stringify({ message: 'Link requested' }) });
  });
  await page.goto('/portal');
  const phone = page.getByLabel('Phone number');
  await expect(phone).toHaveValue('');
  await expect(page.getByRole('button', { name: 'Country code: India +91' })).toBeVisible();
  await page.getByRole('button', { name: 'Country code: India +91' }).click();
  await expect(page.getByRole('alert')).toContainText('More countries coming soon');

  for (const value of ['987654321', '98765432101', '5876543210', '+919876543210']) {
    await phone.fill(value);
    await page.getByRole('button', { name: 'Send link on WhatsApp' }).click();
    await expect(page.getByRole('alert')).toContainText('valid 10-digit Indian mobile number');
  }
  expect(requests).toHaveLength(0);

  await phone.fill('9876543210');
  await page.getByRole('button', { name: 'Send link on WhatsApp' }).click();
  await expect(page.getByRole('heading', { name: 'Check your WhatsApp' })).toBeVisible();
  expect(requests).toEqual([{ phoneNumber: '+919876543210' }]);
});
