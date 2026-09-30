import { test, expect } from '@playwright/test';
const day = (page, n) => page.getByRole('region', { name: `${n} September`, exact: true });
async function jump(page, n) { await page.getByLabel('Jump to date').fill(`2026-09-${n}`); }

test('monthly context and expandable history stay independent without backend calls', async ({ page }) => {
  const requests = [], errors = [];
  page.on('request', r => { if (new URL(r.url()).pathname.startsWith('/api/')) requests.push(r.url()); });
  page.on('pageerror', e => errors.push(e.message));
  await page.goto('/');
  await expect(page.getByTestId('monthly-spend')).toHaveText('₹1,380');
  await expect(page.getByTestId('monthly-plan')).toHaveText('₹32,949');
  await expect(day(page,21).getByRole('button', {name:/^Today/})).toHaveAttribute('aria-expanded','true');
  await jump(page,16);
  await expect(page.getByTestId('monthly-plan')).toHaveText('₹32,949');
  await page.getByRole('button',{name:'View month',exact:false}).click();
  await expect(page.getByRole('dialog')).toContainText('not represent an account balance');
  await page.getByRole('button',{name:'Close monthly overview'}).click();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
  expect(requests).toEqual([]);expect(errors).toEqual([]);
});

test('future and overdue items stay distinct with one actionable occurrence',async({page})=>{
  await page.goto('/');await jump(page,22);
  const future=page.getByTestId('task-electricity');
  await expect(future).toContainText('Upcoming');
  await expect(future.getByRole('button',{name:'Record payment',exact:true})).toHaveCount(0);
  await expect(future.getByRole('button',{name:'Skip',exact:true})).toHaveCount(0);
  await page.getByRole('button',{name:/1 overdue item still needs attention/}).click();
  const loan=page.getByTestId('task-bike-emi');await expect(loan).toBeVisible();
  await expect(loan).toContainText('Overdue');await expect(page.locator('[data-testid="task-bike-emi"]')).toHaveCount(1);
  await loan.getByRole('button',{name:'Skip',exact:true}).click();
  await loan.getByRole('button',{name:'Cancel',exact:true}).click();await expect(loan).toContainText('Overdue');
  await loan.getByRole('button',{name:'Skip',exact:true}).click();await loan.getByLabel('Bank penalty, if any (₹)').fill('100');
  await loan.getByRole('button',{name:'Confirm sample skip'}).click();
  await expect(loan).toContainText('schedule extends by one month');
  await expect(loan).toContainText('Penalty recorded: ₹100');
  await expect(page.getByRole('button',{name:/overdue item still needs attention/})).toHaveCount(0);
  await expect(page.getByTestId('monthly-plan')).toHaveText('₹29,449');
  await expect(loan.getByRole('button',{name:'Record payment',exact:true})).toHaveCount(0);
  await expect(day(page,19).locator('.day-toggle')).toContainText('1 item reviewed');
  await expect(day(page,19).locator('.day-toggle')).not.toContainText('overdue');
});

test('recording a commitment confirms inline and cannot record it twice',async({page})=>{
  await page.goto('/');const item=page.getByTestId('task-internet');
  await item.getByRole('button',{name:'Record payment',exact:true}).click();
  await item.getByLabel('Actual amount (₹)').fill('750');
  await item.getByRole('button',{name:'Cancel',exact:true}).click();await expect(item).toContainText('Due today');
  await item.getByRole('button',{name:'Record payment',exact:true}).click();
  await item.getByLabel('Actual amount (₹)').fill('750');await item.getByRole('button',{name:'Confirm sample record'}).click();
  await expect(item).toContainText('₹750 recorded');
  await expect(item.getByRole('button',{name:'Record payment',exact:true})).toHaveCount(0);
  await expect(page.getByTestId('monthly-plan')).toHaveText('₹32,949');
  await expect(page.getByTestId('monthly-spend')).toHaveText('₹1,380');
  await expect(day(page,21).getByRole('region',{name:'Activity for 21 September'})).toContainText('Internet recharge');
  await page.reload();await expect(page.getByTestId('task-internet')).toContainText('Due today');
});

test('investment and savings collect different facts and update separate totals',async({page})=>{
  await page.goto('/');const sip=page.getByTestId('task-index-sip');
  await sip.getByRole('button',{name:'Record investment'}).click();await sip.getByLabel('Actual amount (₹)').fill('2200');
  await sip.getByRole('button',{name:'Confirm sample record'}).click();await expect(sip.getByRole('form')).toBeVisible();
  await sip.getByLabel('Units received').fill('20');await sip.getByRole('button',{name:'Confirm sample record'}).click();
  await expect(sip).toContainText('20 units');
  await expect(day(page,21)).toContainText('New sample activity is shown below.');
  const saving=page.getByTestId('task-insurance-saving');await saving.getByRole('button',{name:'Record savings'}).click();
  await expect(saving).toContainText('does not pay the insurance bill');await saving.getByLabel('Amount set aside (₹)').fill('1000');await saving.getByRole('button',{name:'Confirm sample record'}).click();
  await expect(page.getByTestId('monthly-plan')).toHaveText('₹31,949');
  await page.getByRole('button',{name:'View month',exact:false}).click();
  const modal=page.getByRole('dialog');await expect(modal.locator('.monthly-details')).toContainText('₹6,200');await expect(modal.locator('.monthly-details')).toContainText('₹6,000');
  await expect(page.getByTestId('monthly-spend')).toHaveText('₹1,380');
});

test('capture defaults to today, historical capture is explicit and confirm-only',async({page})=>{
  const calls=[];page.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))calls.push(r.url());});
  await page.goto('/');await jump(page,17);
  await expect(page.getByRole('button',{name:/Anything to add today/})).toBeVisible();
  await day(page,17).getByRole('button',{name:'Add something for 17 September',exact:false}).click();
  await page.getByLabel('Tell us about your spending').fill('Lunch ₹180 and Auto ₹90');await page.getByRole('button',{name:'Tell us',exact:true}).click();
  await expect(page.getByRole('dialog')).toContainText('17 September 2026');
  await page.getByLabel('Expense 2 amount').fill('95.50');await page.getByRole('button',{name:'Add 2 sample expenses',exact:true}).click();
  await day(page,17).getByRole('button',{name:'Show all 4 items'}).click();
  await expect(day(page,17).getByRole('region',{name:'Activity for 17 September'}).locator('.activity-row')).toHaveCount(4);
  await expect(page.getByTestId('monthly-spend')).toHaveText('₹1,655.5');
  await expect(day(page,17)).toContainText('New sample activity is shown below.');
  await page.reload();await expect(page.getByTestId('monthly-spend')).toHaveText('₹1,380');expect(calls).toEqual([]);
});

test('invalid capture and cancelled confirmation make no changes',async({page})=>{
  await page.goto('/');await page.getByRole('button',{name:/Anything to add today/}).click();
  const input=page.getByLabel('Tell us about your spending'),send=page.getByRole('button',{name:'Tell us',exact:true});
  await input.fill('Lunch ₹180');await send.click();await page.getByRole('button',{name:'Cancel expense preview'}).click();await expect(input).toHaveValue('Lunch ₹180');
  for(const text of ['Why did I spend more?','Investment ₹2000','Lunch ₹180 and Auto','Lunch -20','Lunch 0']){await input.fill(text);await send.click();await expect(page.getByRole('alert')).toBeVisible();await expect(page.getByRole('dialog')).not.toBeVisible();}
  await expect(page.getByTestId('monthly-spend')).toHaveText('₹1,380');
});

test('keyboard expansion and Money preserve the journey state',async({page})=>{
  await page.goto('/');const toggle=day(page,18).getByRole('button',{name:/^18 September/});
  await toggle.focus();await page.keyboard.press('Enter');await expect(toggle).toHaveAttribute('aria-expanded','true');
  await page.getByRole('button',{name:'Money',exact:true}).click();await page.getByRole('button',{name:/PREVIEW.*Commitments/}).click();await expect(page.getByRole('dialog')).toContainText('No real records');await page.keyboard.press('Escape');await page.getByRole('button',{name:'Journey',exact:true}).click();
  await expect(day(page,18).getByRole('button',{name:/^18 September/})).toHaveAttribute('aria-expanded','true');
  await expect(page.getByRole('navigation',{name:'Main navigation'}).getByRole('button')).toHaveCount(2);
});

test('busy, quiet and overdue dates communicate their facts before expansion',async({page})=>{
  await page.goto('/');
  await expect(day(page,17).getByRole('button',{name:/17 September/})).toContainText('₹1,380 spent · 2 recorded expenses');
  await expect(day(page,19).getByRole('button',{name:/19 September/})).toContainText('₹3,500 overdue · No activity recorded');
  await expect(day(page,21).getByRole('button',{name:/Today/})).toContainText('₹2,000 invested · 3 due to review');
  await jump(page,19);
  await expect(day(page,19).getByRole('region',{name:'Activity for 19 September'})).toContainText('No activity recorded. This does not mean nothing was spent.');
  await expect(page.getByTestId('task-bike-emi')).toBeVisible();
  await day(page,19).getByRole('button',{name:'Add something for 19 September'}).click();
  await page.getByLabel('Tell us about your spending').fill('Lunch ₹80');
  await page.getByRole('button',{name:'Tell us',exact:true}).click();
  await page.getByRole('button',{name:'Add 1 sample expense'}).click();
  await expect(day(page,19).locator('.day-toggle')).toContainText('₹80 in 1 new expense');
});

test('first-use preview starts without fabricated totals and accepts a confirmed first entry',async({page})=>{
  const requests=[];page.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))requests.push(r.url());});
  await page.goto('/');
  await page.getByRole('button',{name:'Preview first-use state'}).click();
  const first=page.getByRole('region',{name:'First-use preview'});
  await expect(first).toContainText('No money activity has been recorded yet');
  await expect(first).not.toContainText('₹0');
  await expect(page.getByRole('region',{name:'17 September',exact:true})).toHaveCount(0);
  await page.getByLabel('Tell us about your spending').fill('Lunch ₹180');
  await page.getByRole('button',{name:'Tell us',exact:true}).click();
  await page.getByRole('button',{name:'Add 1 sample expense'}).click();
  await expect(first).toContainText('₹180');
  await expect(page.getByRole('region',{name:'First recorded day'})).toContainText('Lunch');
  await expect(page.getByRole('region',{name:'First recorded day'})).toContainText('This sample entry appears in your activity');
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
  await page.getByRole('button',{name:'Return to populated sample journey'}).click();
  await expect(page.getByTestId('monthly-spend')).toHaveText('₹1,380');
  expect(requests).toEqual([]);
});
