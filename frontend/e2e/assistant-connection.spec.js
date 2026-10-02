import { test, expect } from '@playwright/test';

test('Assistant waits for service and dashboard readiness and preserves drafts offline', async ({ page }) => {
  let releaseHealth, releaseCalendar;
  const healthGate = new Promise(resolve => releaseHealth=resolve);
  const calendarGate = new Promise(resolve => releaseCalendar=resolve);
  let modelCalls=0;
  await page.route('**/health', async route => { await healthGate; await route.fulfill({ json:{status:'UP'} }); });
  await page.route('**/api/web/**', async route => {
    const path=new URL(route.request().url()).pathname;
    if(path.endsWith('/expense-chat') || path.endsWith('/expenses/prepare')) modelCalls++;
    if(path.endsWith('/calendar')) { await calendarGate; return route.fulfill({json:{currency:'INR',timezone:'Asia/Kolkata',days:[]}}); }
    const json=path.endsWith('/demo-profile')?{demoMode:false,canUseDemoMode:false}
      :path.endsWith('/ai-credits')?{available:100,balance:100,reserved:0,paused:false,enabled:true,configured:true}
      :path.endsWith('/expenses/options')?{categories:[],merchants:[],accounts:[]}
      :path.endsWith('/expense-chat/conversations')?[]
      :path.endsWith('/monthly-commitment')?{commitment:null}
      :{items:[],actions:[],loans:[],mutualFunds:[],stocks:[]};
    await route.fulfill({json});
  });
  await page.goto('/dashboard?month=2026-10');
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Ask about your money',exact:true}).click();
  const chat=page.getByRole('region',{name:'Money assistant'});
  await expect(chat).toContainText('App is not online yet');
  await expect(chat.getByRole('button',{name:'Where did my money go?'})).toBeDisabled();
  releaseHealth();
  await expect(page.getByRole('button',{name:/Connection status: Checking/})).toBeVisible();
  await expect(chat.getByRole('button',{name:'Where did my money go?'})).toBeDisabled();
  releaseCalendar();
  await expect(chat.getByRole('button',{name:'Where did my money go?'})).toBeEnabled();
  await page.getByLabel('Your money question').fill('Keep my draft');
  await page.evaluate(()=>window.dispatchEvent(new Event('offline')));
  await expect(chat).toContainText('App is not online.');
  await expect(chat.getByRole('button',{name:'Where did my money go?'})).toBeDisabled();
  await expect(page.getByLabel('Your money question')).toHaveValue('Keep my draft');
  expect(modelCalls).toBe(0);
  await page.evaluate(()=>window.dispatchEvent(new Event('online')));
  await expect(chat.getByRole('button',{name:'Where did my money go?'})).toBeEnabled();
  await chat.getByRole('button',{name:'Add expense',exact:true}).click();
  await page.getByRole('button',{name:'Describe with AI',exact:true}).click();
  await page.getByLabel('Expense description').fill('Dinner 450');
  await page.evaluate(()=>window.dispatchEvent(new Event('offline')));
  await expect(page.getByRole('button',{name:'Prepare expense',exact:true})).toBeDisabled();
  await expect(page.getByLabel('Expense description')).toHaveValue('Dinner 450');
  await expect(page.getByText('App is not online. Reconnect to record an expense or use AI.')).toBeVisible();
  expect(modelCalls).toBe(0);
});
