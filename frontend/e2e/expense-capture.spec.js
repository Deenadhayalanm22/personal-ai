import { test, expect } from '@playwright/test';

async function dashboard(page) {
  const requests=[];let recorded=false;
  await page.route('**/api/web/**',async route=>{
    const url=new URL(route.request().url()),path=url.pathname,body=route.request().postDataJSON();requests.push({path,body,method:route.request().method()});
    let json={items:[],actions:[],loans:[],mutualFunds:[],stocks:[]};
    if(path.endsWith('/auth/demo-profile'))json={demoMode:false,canUseDemoMode:false};
    else if(path.endsWith('/expenses/calendar'))json={month:'2026-09',currency:'INR',timezone:'Asia/Kolkata',totalSpend:recorded?450:0,transactionCount:recorded?1:0,days:[{date:'2026-09-28',transactionCount:recorded?1:0,totalSpend:recorded?450:0,intensity:recorded?1:0}]};
    else if(path.endsWith('/monthly-commitment'))json={commitment:null};
    else if(path.endsWith('/ai-credits'))json={available:100,balance:100,reserved:0,enabled:true,configured:true,paused:false};
    else if(path.endsWith('/ai-credits/permissions'))json={admin:false};
    else if(path.endsWith('/ai-credits/ledger') || path.endsWith('/expense-chat/conversations'))json=[];
    else if(path.endsWith('/expenses/options'))json={categories:[{name:'Food',subcategories:['Dining']}],merchants:[{id:1,name:'Saravana Bhavan'}],accounts:[{id:2,name:'HDFC bank account'}]};
    else if(path.endsWith('/expenses/manual'))json={status:'READY',answer:'Review this expense.',extractionId:123,preview:{...body,currency:'INR'}};
    else if(path.endsWith('/expense-chat/capture'))json={status:'READY',answer:'Review this expense.',extractionId:123,preview:{amount:450,date:body.date,category:'Food',subcategory:'Dining',merchant:'Saravana Bhavan',account:'HDFC bank account',currency:'INR'}};
    else if(path.endsWith('/capture/123/confirm')){recorded=true;json={status:'RECORDED',date:'2026-09-28'};}
    else if(path.endsWith('/expenses'))json={items:recorded?[{id:77,merchant:'Saravana Bhavan',category:'Food',amount:450,transactionTime:'2026-09-28T00:00:00Z'}]:[],nextBeforeId:null};
    else if(path.endsWith('/reference-entity-types'))json={entityTypes:['MERCHANT','ACCOUNT','BENEFICIARY']};
    else if(path.endsWith('/reference-preferences'))json={references:[{referenceId:1,entityType:'MERCHANT',primaryReference:'Saravana Bhavan',aliases:[],transactionCount:1}]};
    await route.fulfill({json});
  });
  await page.route('**/health',route=>route.fulfill({json:{status:'UP'}}));
  await page.goto('/dashboard?month=2026-09');
  await expect(page.getByRole('button',{name:'Add expense',exact:true})).toBeVisible();
  return requests;
}

test('selected calendar date opens Ask AI and records only after review',async({page},testInfo)=>{
  const requests=await dashboard(page);
  await page.locator('.calendar').getByRole('button').filter({hasText:/^28$/}).click();
  await page.getByRole('button',{name:'Add missing expense',exact:true}).click();
  await expect(page.getByRole('region',{name:'Add expense conversation'})).toContainText('September 28, 2026');
  await page.getByRole('button',{name:'Describe with AI'}).click();
  await page.getByLabel('Expense description').fill('Paid 450 for dinner at Saravana Bhavan');
  await page.getByRole('button',{name:'Prepare expense'}).click();
  await expect(page.getByRole('region',{name:'Expense preview'})).toContainText('HDFC bank account');
  expect(requests.some(r=>r.path.endsWith('/confirm'))).toBe(false);
  expect(requests.find(r=>r.path.endsWith('/capture')).body.date).toBe('2026-09-28');
  await page.screenshot({path:`/private/tmp/personal-ai-expense-preview-${testInfo.project.name}.png`,fullPage:false});
  await page.getByRole('button',{name:'Record expense',exact:true}).click();
  await expect(page.getByRole('status').filter({hasText:'has been recorded'})).toBeVisible();
  await page.getByRole('button',{name:'View expense'}).click();
  await expect(page.getByRole('heading',{name:/28 September/})).toBeVisible();
  expect(requests.filter(r=>r.path.endsWith('/confirm'))).toHaveLength(1);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

test('You exposes existing merchant and account name cleanup',async({page})=>{
  await dashboard(page);
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'You',exact:true}).click();
  await expect(page.getByRole('button',{name:/Expense capture/})).toHaveCount(0);
  await expect(page.getByRole('button',{name:/Optional money modules/})).toBeVisible();
  await page.getByRole('button',{name:/Manage names/}).click();
  await expect(page.getByRole('dialog')).toContainText('Manage names');
  await expect(page.getByRole('button',{name:/M Saravana Bhavan Merchant/})).toBeVisible();
});

test('manual entry is default and records without AI credits',async({page},testInfo)=>{
  const requests=await dashboard(page);
  await page.route('**/api/web/ai-credits',route=>route.fulfill({status:503,json:{message:'AI unavailable'}}));
  await page.locator('.calendar').getByRole('button').filter({hasText:/^28$/}).click();
  await page.getByRole('button',{name:'Add missing expense',exact:true}).click();
  await expect(page.getByRole('button',{name:'Enter manually'})).toHaveAttribute('aria-pressed','true');
  await page.getByLabel('Amount',{exact:true}).fill('450');
  await page.getByLabel('Category',{exact:true}).selectOption('Food');
  await page.getByLabel('Subcategory',{exact:true}).selectOption('Dining');
  await page.getByLabel('Merchant (optional)').fill('Saravana Bhavan');
  await page.screenshot({path:`/private/tmp/personal-ai-manual-${testInfo.project.name}.png`,fullPage:false});
  await page.getByRole('button',{name:'Review expense',exact:true}).click();
  await expect(page.getByRole('region',{name:'Expense preview'})).toContainText('Saravana Bhavan');
  expect(requests.some(r=>r.path.endsWith('/confirm') || r.path.endsWith('/expense-chat/capture'))).toBe(false);
  await page.getByRole('button',{name:'Record expense',exact:true}).click();
  await expect(page.getByRole('status').filter({hasText:'has been recorded'})).toBeVisible();
  expect(requests.filter(r=>r.path.endsWith('/expenses/manual'))).toHaveLength(1);
});

test('Money opens and closes within the main dashboard',async({page})=>{
  await dashboard(page);
  await page.getByRole('button',{name:/Your money.*Explore/}).click();
  const dialog=page.getByRole('dialog');
  await expect(dialog.getByRole('heading',{name:'Build your complete money picture'})).toBeVisible();
  await expect(dialog.getByTestId('commitments-section')).toBeVisible();
  await dialog.locator('button.close').click();
  await expect(dialog).toHaveCount(0);
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Home',exact:true}).click();
  await expect(page.getByRole('button',{name:'Add expense',exact:true})).toBeVisible();
});

for(const width of [1280,375]){
  test(`expense capture matches portal typography at ${width}px`,async({page})=>{
    await page.setViewportSize({width,height:900});
    await dashboard(page);
    await page.getByRole('button',{name:'Add expense',exact:true}).click();
    const capture=page.getByRole('region',{name:'Add expense conversation'});
    await expect(capture.getByRole('heading',{name:'Add an expense'})).toHaveCSS('font-family','Manrope, sans-serif');
    const bodyFont=await page.locator('body').evaluate(el=>getComputedStyle(el).fontFamily);
    for(const label of ['Amount','Date','Category','Subcategory'])expect(await capture.getByLabel(label,{exact:true}).evaluate(el=>getComputedStyle(el).fontFamily)).toBe(bodyFont);
    await capture.getByLabel('Amount',{exact:true}).fill('450');
    await capture.getByLabel('Category',{exact:true}).selectOption('Food');
    await capture.getByLabel('Subcategory',{exact:true}).selectOption('Dining');
    await capture.screenshot({path:`/tmp/expense-style-${width}.png`});
    await capture.getByRole('button',{name:'Describe with AI'}).click();
    expect(await capture.getByLabel('Expense description').evaluate(el=>getComputedStyle(el).fontFamily)).toBe(bodyFont);
    await capture.getByLabel('Expense description').fill('Paid 450 for dinner');
    await capture.getByRole('button',{name:'Prepare expense'}).click();
    await expect(capture.getByRole('heading',{name:'Review expense'})).toHaveCSS('font-family','Manrope, sans-serif');
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  });
}
