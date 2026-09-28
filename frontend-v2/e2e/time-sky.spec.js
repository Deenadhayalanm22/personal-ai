import { test, expect } from '@playwright/test';
test.use({ timezoneId: 'Asia/Kolkata' });
test('one small sky follows device time independently of expanded dates',async({page})=>{
 await page.clock.install({time:new Date('2026-09-28T04:59:00+05:30')});await page.goto('/');const sky=page.locator('.time-sky');
 await expect(sky).toHaveCount(1);await expect(sky).toHaveAttribute('data-phase','night');await page.clock.fastForward(60000);await expect(sky).toHaveAttribute('data-phase','dawn');
 for(const [hour,phase] of [[8,'day'],[17,'evening'],[20,'night']]){await page.clock.setSystemTime(new Date(`2026-09-28T${String(hour).padStart(2,'0')}:00:00+05:30`));await page.evaluate(()=>document.dispatchEvent(new Event('visibilitychange')));await expect(sky).toHaveAttribute('data-phase',phase);}
 await page.getByLabel('Jump to date').fill('2026-09-15');await expect(sky).toHaveAttribute('data-phase','night');await expect(sky).toHaveAccessibleName('Moon and stars · current device time');
 await page.getByRole('button',{name:'Money',exact:true}).click();await page.clock.setSystemTime(new Date('2026-09-28T12:00:00+05:30'));await page.getByRole('button',{name:'Journey',exact:true}).click();await expect(sky).toHaveAttribute('data-phase','day');
 await page.emulateMedia({reducedMotion:'reduce'});expect(await sky.locator('.sun').evaluate(el=>getComputedStyle(el).transitionDuration)).toBe('0s');
});
