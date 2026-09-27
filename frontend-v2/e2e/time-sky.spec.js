import { test, expect } from '@playwright/test';

test.use({ timezoneId: 'Asia/Kolkata' });

test('ambient sky follows local time bands and not the story date', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-09-28T04:59:00+05:30') });
  await page.goto('/');
  const sky = page.locator('.time-sky');
  await expect(sky).toHaveAttribute('data-phase', 'night');
  await page.clock.fastForward(60000);
  await expect(sky).toHaveAttribute('data-phase', 'dawn');
  for (const [hour, phase] of [[8, 'day'], [17, 'evening'], [20, 'night'], [0, 'night']]) {
    await page.clock.setSystemTime(new Date(`2026-09-28T${String(hour).padStart(2, '0')}:00:00+05:30`));
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
    await expect(sky).toHaveAttribute('data-phase', phase);
  }
  await page.getByRole('button', { name: '15 September', exact: true }).click();
  await expect(sky).toHaveAttribute('data-phase', 'night');
  await expect(sky).toHaveAccessibleName('Moon and stars · current device time');
  await page.getByRole('button', { name: 'Money', exact: true }).click();
  await page.clock.setSystemTime(new Date('2026-09-28T12:00:00+05:30'));
  await page.getByRole('button', { name: 'Journey', exact: true }).click();
  await expect(sky).toHaveAttribute('data-phase', 'day');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  expect(await sky.locator('.sun').evaluate(el => getComputedStyle(el).transitionDuration)).toBe('0s');
});

test('small world stays around traveller and inside the road at either end', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  await expect(page.locator('.journey-heading .time-sky')).toHaveCount(0);
  await expect(page.locator('.traveller-world .time-sky')).toHaveCount(1);
  for (const date of ['15 September', '21 September']) {
    await page.getByRole('button', { name: date, exact: true }).click();
    const world = await page.locator('.traveller-world').boundingBox();
    const panel = await page.locator('.journey-panel').boundingBox();
    const person = await page.locator('.traveller').boundingBox();
    expect(world.x).toBeGreaterThanOrEqual(panel.x);
    expect(world.x + world.width).toBeLessThanOrEqual(panel.x + panel.width);
    expect(world.y).toBeGreaterThanOrEqual(panel.y - 4);
    expect(Math.abs(person.x + person.width / 2 - world.x - world.width / 2)).toBeLessThan(1);
    expect(world.height).toBeLessThanOrEqual(42);
  }
});
