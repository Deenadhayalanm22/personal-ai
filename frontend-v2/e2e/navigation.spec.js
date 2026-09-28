import { test, expect } from '@playwright/test';

test('day and story links survive reload, close, and browser back', async ({ page }) => {
  await page.goto('/?day=2026-09-17');
  const day = page.getByRole('region',{name:'17 September',exact:true});
  await expect(day.locator('.day-toggle')).toHaveAttribute('aria-expanded','true');
  await day.getByRole('button',{name:'View supporting details'}).click();
  await expect(page).toHaveURL(/detail=story%3A2026-09-17/);
  await expect(page.getByRole('dialog')).toContainText('Groceries accounted for ₹1,200');
  await page.reload();
  await expect(page.getByRole('dialog')).toContainText('Groceries accounted for ₹1,200');
  await page.getByRole('button',{name:'Close details'}).click();
  await expect(page).not.toHaveURL(/detail=/);
  await page.getByRole('button',{name:'Money',exact:true}).click();
  await expect(page).toHaveURL(/view=money/);
  await page.goBack();
  await expect(day.locator('.day-toggle')).toHaveAttribute('aria-expanded','true');
  await expect(day).toBeInViewport();
});

test('month boundaries and unavailable dates are honest and navigable', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Choose month').fill('2026-08');
  await expect(page.getByRole('region',{name:'No sample data for this month'})).toContainText('No sample records for this month');
  await expect(page.getByTestId('monthly-spend')).toHaveCount(0);
  await expect(page).toHaveURL(/month=2026-08/);
  await page.getByLabel('Jump to date').fill('2026-08-31');
  await expect(page.getByRole('region',{name:'31 August',exact:true})).toContainText('No sample records are available for this date');
  await expect(page).toHaveURL(/day=2026-08-31/);
  await page.getByRole('button',{name:'Today',exact:true}).click();
  await expect(page.getByTestId('monthly-spend')).toHaveText('₹1,380');
  await page.goBack();
  await expect(page.getByRole('region',{name:'31 August',exact:true})).toBeVisible();
});

test('a quiet stretch stays compact until opened or reached by date link', async ({ page }) => {
  await page.goto('/');
  const gap=page.getByRole('region',{name:'Quiet stretch 13 to 14 September'});
  await expect(gap).toContainText('No sample activity recorded');
  await expect(page.getByRole('region',{name:'13 September',exact:true})).toHaveCount(0);
  await gap.getByRole('button').click();
  await expect(page.getByRole('region',{name:'13 September',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Collapse quiet stretch'}).click();
  await page.goto('/?day=2026-09-13');
  await expect(page.getByRole('region',{name:'13 September',exact:true}).locator('.day-toggle')).toHaveAttribute('aria-expanded','true');
});
