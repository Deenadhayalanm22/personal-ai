import {test,expect} from '@playwright/test';

async function setup(page, withBills=false) {
  const commands=[];
  let salary=null;
  let accounts=[{id:31,name:'HDFC credit card',type:'CREDIT_CARD',cardId:4,issuerName:'HDFC',statementDay:1,dueDay:21,linkedSpending:480},{id:32,name:'Salary account',type:'UNCONFIGURED',linkedSpending:100}];
  if(withBills)accounts.push({id:33,name:'ICICI credit account',type:'CREDIT_CARD',cardId:5,issuerName:'ICICI',statementDay:1,dueDay:21});
  await page.clock.install({time:new Date('2026-10-02T06:00:00Z')});
  await page.route('**/api/web/**',async route=>{
    const req=route.request(),path=new URL(req.url()).pathname;
    let json={items:[],actions:[],loans:[],mutualFunds:[],stocks:[],bills:[],cards:[]};
    if(path.endsWith('/demo-profile'))json={demoMode:false,canUseDemoMode:false};
    else if(path.endsWith('/calendar'))json={month:'2026-10',timezone:'Asia/Kolkata',currency:'INR',totalSpend:580,days:[]};
    else if(path.endsWith('/monthly-commitment'))json={commitment:null};
    else if(path.endsWith('/income-outlook'))json={salary};
    else if(path.endsWith('/income-outlook/salary')&&req.method()==='PUT'){const body=req.postDataJSON();commands.push({path,body});salary=body.salaryVisibility==='SKIPPED'?null:{maskedValue:'**'};json=salary||{};}
    else if(path.endsWith('/expenses/options'))json={categories:[{name:'Food',subcategories:['Groceries']}],merchants:[],accounts:accounts.map(a=>({id:a.id,name:a.name}))};
    else if(path.endsWith('/credit-card-bills')&&withBills)json={bills:[4,5].map(cardId=>({cardId,cardName:cardId===4?'Regalia card':'ICICI Visa',month:'2026-10',periodStart:'2026-09-01',statementEnd:'2026-09-30',dueDate:'2026-10-21',monthlyPurchaseAmount:480,projectedAmount:cardId===4?5000:2000,paidAmount:1000,remaining:cardId===4?4000:1000,statementClosed:true,payments:[{id:cardId,paidAt:'2026-10-01',amount:1000}]}))};
    else if(path.endsWith('/accounts')&&req.method()==='GET')json={currency:'INR',accounts};
    else if(path.endsWith('/accounts')&&req.method()==='POST'){
      const body=req.postDataJSON();commands.push({path,body});
      const id=accounts.length+40;
      const account={id,name:body.name,type:body.type,linkedSpending:0,issuerName:body.issuerName,statementDay:body.statementDay,dueDay:body.dueDay,startMonth:body.startMonth,cardId:body.type==='CREDIT_CARD'?id:null};
      accounts=[...accounts,account];json=account;
    }else if(path.endsWith('/accounts/32')&&req.method()==='PATCH'){
      const body=req.postDataJSON();commands.push({path,body});json={...accounts.find(a=>a.id===32),...body};accounts=accounts.map(a=>a.id===32?json:a);
    }
    await route.fulfill({json});
  });
  await page.route('**/health',r=>r.fulfill({json:{status:'UP'}}));
  await page.goto('/dashboard?month=2026-10');
  await page.getByRole('button',{name:/Your money.*Explore/}).click();
  await expect(page.getByRole('region',{name:'Accounts',exact:true})).toContainText('HDFC credit card');
  return commands;
}

for(const width of [1280,375]) {
  test(`unified accounts keep details optional and support both types at ${width}px`,async({page})=>{
    await page.setViewportSize({width,height:900});const commands=await setup(page);
    const section=page.getByRole('region',{name:'Accounts',exact:true});
    await expect(section).toContainText('Bill generates');await expect(section).toContainText('Day 1');await expect(section).toContainText('Day 21');
    await expect(section.getByText('Monthly income',{exact:true})).toBeVisible();
    const headingFont=await section.getByRole('heading',{name:'Accounts',exact:true}).evaluate(el=>getComputedStyle(el).fontFamily);
    const salaryFont=await section.getByText('Monthly income',{exact:true}).evaluate(el=>getComputedStyle(el).fontFamily);
    expect(headingFont.split(',')[0].trim()).toBe(salaryFont.split(',')[0].trim());
    const bodyFont=await page.locator('body').evaluate(el=>getComputedStyle(el).fontFamily);
    expect(await section.locator('.account-type').first().evaluate(el=>getComputedStyle(el).fontFamily)).toBe(bodyFont);
    await section.screenshot({path:`/tmp/accounts-facts-initial-${width}.png`});
    await section.getByRole('button',{name:'Set up monthly income'}).click();
    await expect(page.getByRole('heading',{name:'What’s your approximate total monthly income?'})).toBeVisible();
    await page.locator('.income-flow .close').click();
    await section.getByRole('button',{name:'Add account'}).click();
    const form=page.getByRole('dialog',{name:'Add account'});
    expect(await form.getByLabel('Account type').evaluate(el=>getComputedStyle(el).fontFamily)).toBe(bodyFont);
    await expect(form.getByLabel('Opening balance (optional)')).toHaveCount(0);
    await form.getByLabel('Account name',{exact:true}).fill('ICICI bank');
    await form.getByRole('button',{name:'Save account'}).click();
    await expect(form).toHaveCount(0);
    const bank=section.locator('article').filter({has:page.getByRole('heading',{name:'ICICI bank',exact:true})});
    await expect(bank).toContainText('Bank / debit');
    expect(commands[0].body).toMatchObject({type:'BANK'});
    expect(commands[0].body).not.toHaveProperty('openingAmount');
    await expect(section.getByText(/Tracked balance|Balance not provided|Money received|Expenses recorded/)).toHaveCount(0);
    await expect(section.getByRole('button',{name:'Record money received'})).toHaveCount(0);
    await section.getByRole('button',{name:'Add account'}).click();
    await form.getByLabel('Account name',{exact:true}).fill('ICICI Visa');
    await form.getByLabel('Account type').selectOption('CREDIT_CARD');
    await expect(form.getByLabel('Opening balance (optional)')).toHaveCount(0);
    await form.getByLabel('Bank / issuer').fill('ICICI');
    await form.getByLabel('Start tracking bills from (optional)').fill('2026-11');
    await form.getByLabel('Bill generation day').fill('1');await form.getByLabel('Payment due day').fill('21');
    await form.getByRole('button',{name:'Save account'}).click();
    await expect(section.getByRole('heading',{name:'ICICI Visa',exact:true})).toBeVisible();
    await expect(section).toContainText('Bills included from 2026-11');
    await section.getByRole('button',{name:'Configure Salary account'}).click();
    const configure=page.getByRole('dialog',{name:'Configure account'});
    await configure.getByRole('button',{name:'Save account'}).click();
    await expect(section.locator('article').filter({has:page.getByRole('heading',{name:'Salary account',exact:true})})).toContainText('Bank / debit');
    expect(commands.at(-1).path).toBe('/api/web/accounts/32');
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await page.screenshot({path:`/tmp/accounts-${width}.png`,fullPage:true});
  });
}

for (const mode of ['RANGE','EXACT','SKIPPED']) {
  test(`visible income context preserves ${mode.toLowerCase()} input`, async ({page})=>{
    const commands=await setup(page);
    const section=page.getByRole('region',{name:'Accounts',exact:true});
    await expect(section.getByRole('button',{name:'Set up monthly income'})).toBeVisible();
    await section.getByRole('button',{name:'Set up monthly income'}).click();
    const flow=page.locator('.income-flow');
    if(mode==='EXACT'){
      await flow.getByRole('button',{name:'I’m comfortable sharing the exact amount'}).click();
      await flow.getByLabel('Total monthly income').fill('85000');
    } else if(mode==='RANGE') await flow.getByRole('button',{name:'₹50k–₹1L',exact:true}).click();
    await flow.getByRole('button',{name:mode==='SKIPPED'?'Skip for now':'Save monthly income',exact:true}).click();
    await expect(flow).toHaveCount(0);
    expect(commands[0].body.salaryVisibility).toBe(mode);
    expect(commands[0].body.salaryFrequency).toBe('MONTHLY');
    await expect(page.getByLabel('How often does it arrive?')).toHaveCount(0);
    if(mode==='EXACT')expect(commands[0].body.exactMonthlySalary).toBe(85000);
    if(mode==='RANGE')expect(commands[0].body.salaryRange).toBe('FROM_50000_TO_100000');
    if(mode==='SKIPPED')await expect(section.getByRole('button',{name:'Set up monthly income'})).toBeVisible();
    else {await expect(section).toContainText('Income saved');await expect(section.getByRole('button',{name:'Update monthly income'})).toBeVisible();await expect(section).not.toContainText('85000');}
    expect(commands).toHaveLength(1);
    expect(commands[0].path).toBe('/api/web/income-outlook/salary');
  });
}


for(const width of [1280,375]) {
  test(`credit-card bills stay inside their matching account at ${width}px`,async({page})=>{
    await page.setViewportSize({width,height:900});
    await setup(page,true);
    const section=page.getByRole('region',{name:'Accounts',exact:true});
    const hdfc=section.locator('.account-card').filter({has:page.getByRole('heading',{name:'HDFC credit card',exact:true})});
    const icici=section.locator('.account-card').filter({has:page.getByRole('heading',{name:'ICICI credit account',exact:true})});
    await expect(hdfc).toContainText('Regalia card');
    await expect(hdfc).toContainText('₹4,000.00 remaining');
    await expect(hdfc.getByRole('button',{name:'Configure HDFC credit card'})).toBeVisible();
    await expect(hdfc.getByRole('button',{name:'Record bill payment'})).toBeEnabled();
    await expect(hdfc).not.toContainText('ICICI Visa');
    await expect(icici).toContainText('ICICI Visa');
    await expect(icici).toContainText('₹1,000.00 remaining');
    await expect(icici).not.toContainText('Regalia card');
    await expect(section.locator('.card-bill-panel')).toHaveCount(2);
    await expect(section.locator('.account-card .card-bill-panel')).toHaveCount(2);
    await hdfc.getByText('Payment history',{exact:true}).click();
    await expect(hdfc).toContainText('₹1,000.00 paid');
    await hdfc.getByRole('button',{name:'Record bill payment'}).click();
    await expect(page.getByRole('dialog',{name:'Record bill payment'})).toContainText('Regalia card');
    await page.getByRole('dialog',{name:'Record bill payment'}).getByRole('button',{name:'Cancel',exact:true}).click();
    await expect(section.locator('.account-card').filter({has:page.getByRole('heading',{name:'Salary account',exact:true})}).getByRole('region',{name:'Credit-card bills',exact:true})).toHaveCount(0);
    expect(await section.evaluate(el=>el.scrollWidth<=el.clientWidth)).toBe(true);
    await section.screenshot({path:`/tmp/merged-card-accounts-${width}.png`});
  });
}
