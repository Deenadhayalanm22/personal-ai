import { test, expect } from '@playwright/test';

for (const width of [1280, 375]) {
  test(`card settlement preserves spending and supports partial/full payments at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.clock.install({ time: new Date('2026-11-05T06:00:00Z') });
    const payments = [], commands = [];
    let failNext = true;
    const bill = () => ({ cardId: 4, cardName: 'Millennia', month: '2026-11', periodStart: '2026-09-29', statementEnd: '2026-10-28', dueDate: '2026-11-05', monthlyPurchaseAmount:700,projectedAmount: 5000, paidAmount: payments.reduce((sum,p)=>sum+p.amount,0), remaining: 5000-payments.reduce((sum,p)=>sum+p.amount,0), statementClosed: true, payments });
    await page.route('**/api/web/**', async route => {
      const req=route.request(), url=new URL(req.url()), path=url.pathname;
      let json={ items:[], actions:[], loans:[], mutualFunds:[], stocks:[], cards:[], bills:[] };
      if(path.endsWith('/demo-profile')) json={demoMode:false,canUseDemoMode:false};
      else if(path.endsWith('/calendar')) json={month:'2026-11',currency:'INR',timezone:'Asia/Kolkata',totalSpend:700,creditCardSpend:700,transactionCount:1,days:[]};
      else if(path.endsWith('/monthly-commitment')) json={commitment:null,overview:{stillToPay:bill().remaining,plannedInvesting:0,plannedSavings:0}};
      else if(path.endsWith('/accounts')) json={accounts:[{id:7,name:'Salary bank',type:'BANK'}]};
      else if(path.endsWith('/credit-card-bills')) json={month:'2026-11',bills:[bill()]};
      else if(path.endsWith('/activity')) json={items:payments.map(p=>({type:'CARD_PAYMENT',id:p.id,date:p.paidAt,amount:p.amount,label:'Millennia',description:'Card bill payment · purchases already counted in spending'}))};
      else if(path.endsWith('/payments')) {
        const body=req.postDataJSON();commands.push({path,body});
        if(failNext){failNext=false;return route.fulfill({status:503,json:{message:'Please retry this payment'}});}
        payments.push({id:payments.length+1,paidAt:body.paidAt,amount:body.amount});json=bill();
      }
      await route.fulfill({json});
    });
    await page.route('**/health',route=>route.fulfill({json:{status:'UP'}}));
    await page.goto('/dashboard?month=2026-11');
    await expect(page.getByRole('region',{name:'Credit-card bills',exact:true})).toHaveCount(0);
    await expect(page.getByRole('progressbar')).toHaveCount(0);
    await expect(page.getByRole('button',{name:'Record bill payment'})).toHaveCount(0);
    await page.getByRole('button',{name:'View credit-card bills →'}).click();
    const bills=page.getByRole('region',{name:'Credit-card bills',exact:true});
    await expect(page.getByRole('region',{name:'Accounts',exact:true})).toContainText('Credit-card bills');
    const overview=page.getByRole('region',{name:'Month at a glance'});
    await expect(overview).toContainText('Spent this month');
    await expect(overview).toContainText('Includes ₹700 on credit cards');
    await expect(bills).toContainText('₹5,000.00 remaining');
    const comparison=bills.getByRole('group',{name:'Millennia monthly spending and bill comparison'});
    await expect(comparison).toContainText('Purchases in November 2026');
    await expect(comparison).toContainText('₹700.00');
    await expect(comparison).toContainText('₹5,000.00');
    await expect(bills.getByRole('list',{name:'Millennia billing cycle'})).toContainText('Bill generates');
    await expect(bills.getByRole('progressbar')).toHaveAttribute('aria-valuenow','0');
    await bills.getByRole('button',{name:'Record bill payment'}).click();
    const dialog=page.getByRole('dialog',{name:'Record bill payment'});
    await expect(dialog).toContainText('purchases were already counted as spending');
    await dialog.getByLabel('Amount paid').fill('2000');
    await dialog.getByRole('button',{name:'Confirm bill payment'}).click();
    await expect(dialog.getByRole('alert')).toContainText('Please retry');
    await dialog.getByRole('button',{name:'Confirm bill payment'}).click();
    await expect(dialog).toHaveCount(0);
    expect(commands[0].body.requestId).toBe(commands[1].body.requestId);
    await expect(bills).toContainText('₹3,000.00 remaining');
    await expect(bills.getByRole('progressbar')).toHaveAttribute('aria-valuenow','40');
    await page.setViewportSize({width,height:1400});
    await bills.scrollIntoViewIfNeeded();
    await bills.screenshot({path:`/tmp/card-bill-visual-${width}.png`});
    await page.setViewportSize({width,height:900});
    await expect(overview).toContainText('₹700');
    await expect(overview).toContainText('₹3,000');
    const activity=page.getByRole('region',{name:'Activity',exact:true});
    await expect(activity).toContainText('Card bill payment');
    await expect(activity.getByRole('button',{name:/Edit Millennia|Delete Millennia/})).toHaveCount(0);
    await bills.getByRole('button',{name:'Record bill payment'}).click();
    await dialog.getByRole('button',{name:'Confirm bill payment'}).click();
    await expect(bills).toContainText('Captured purchases covered');
    await expect(bills.getByRole('progressbar')).toHaveAttribute('aria-valuenow','100');
    await expect(bills.getByRole('button',{name:'Record bill payment'})).toBeEnabled();
    await bills.getByText('Payment history',{exact:true}).click();
    await expect(bills).toContainText('₹2,000.00 paid');
    await expect(bills).toContainText('₹3,000.00 paid');
    await expect(overview).toContainText('₹700');
    await page.locator('.money-modal > .close').click();
    await expect(page.getByRole('region',{name:'Credit-card summary'})).toHaveCount(0);
    await page.reload();
    await expect(page.getByRole('region',{name:'Credit-card summary'})).toHaveCount(0);
    await page.getByRole('button',{name:/Your money.*Explore/}).click();
    await expect(page.getByRole('region',{name:'Credit-card bills',exact:true})).toContainText('Captured purchases covered');
    await page.locator('.money-modal > .close').click();
    await expect(page.getByRole('region',{name:'Credit-card bills',exact:true})).toHaveCount(0);
    await expect(page.getByRole('button',{name:'Record bill payment'})).toHaveCount(0);
    await expect(page.getByRole('progressbar')).toHaveCount(0);
    expect(commands).toHaveLength(3);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  });
}

test('open statement remains a projection with settlement disabled',async({page})=>{
  await page.route('**/api/web/**',async route=>{
    const path=new URL(route.request().url()).pathname;
    let json={items:[],actions:[],loans:[],mutualFunds:[],stocks:[],cards:[]};
    if(path.endsWith('/demo-profile'))json={demoMode:false,canUseDemoMode:false};
    if(path.endsWith('/calendar'))json={month:'2026-11',currency:'INR',timezone:'Asia/Kolkata',days:[]};
    if(path.endsWith('/monthly-commitment'))json={commitment:null};
    if(path.endsWith('/credit-card-bills'))json={bills:[{cardId:4,cardName:'Millennia',month:'2026-11',periodStart:'2026-10-06',statementEnd:'2026-11-05',dueDate:'2026-11-20',projectedAmount:5000,paidAmount:0,remaining:5000,statementClosed:false,payments:[]}]};
    await route.fulfill({json});
  });
  await page.route('**/health',route=>route.fulfill({json:{status:'UP'}}));
  await page.goto('/dashboard?month=2026-11');
  await page.getByRole('button',{name:'View credit-card bills →'}).click();
  const bills=page.getByRole('region',{name:'Credit-card bills',exact:true});
  await expect(bills).toContainText('Projected bill');
  await expect(bills.getByRole('progressbar')).toHaveCount(0);
  await expect(bills).toContainText('Amount can change until this date');
  await expect(bills.getByRole('button',{name:'Record bill payment'})).toBeDisabled();
});

for (const width of [1280,375]) {
  test(`actual payment above captured purchases retains difference at ${width}px`,async({page})=>{
    await page.setViewportSize({width,height:900});
    await page.clock.install({time:new Date('2026-11-05T06:00:00Z')});
    const payments=[];
    const bill=()=>{const paid=payments.reduce((sum,p)=>sum+p.amount,0);return {cardId:4,cardName:'Millennia',month:'2026-11',periodStart:'2026-10-01',statementEnd:'2026-10-31',statementGeneratedAt:'2026-11-01',dueDate:'2026-11-21',projectedAmount:75000,monthlyPurchaseAmount:0,paidAmount:paid,remaining:Math.max(75000-paid,0),unmatchedPaymentAmount:Math.max(paid-75000,0),statementClosed:true,payments};};
    await page.route('**/api/web/**',async route=>{
      const path=new URL(route.request().url()).pathname;
      let json={items:[],actions:[],loans:[],mutualFunds:[],stocks:[],cards:[],accounts:[]};
      if(path.endsWith('/demo-profile'))json={demoMode:false,canUseDemoMode:false};
      if(path.endsWith('/calendar'))json={month:'2026-11',currency:'INR',timezone:'Asia/Kolkata',totalSpend:0,days:[]};
      if(path.endsWith('/monthly-commitment'))json={commitment:null};
      if(path.endsWith('/credit-card-bills'))json={bills:[bill()]};
      if(path.endsWith('/payments')){const body=route.request().postDataJSON();payments.push({id:payments.length+1,amount:body.amount,paidAt:body.paidAt});json=bill();}
      await route.fulfill({json});
    });
    await page.route('**/health',route=>route.fulfill({json:{status:'UP'}}));
    await page.goto('/dashboard?month=2026-11');
    await page.getByRole('button',{name:'View credit-card bills →'}).click();
    const bills=page.getByRole('region',{name:'Credit-card bills',exact:true});
    await bills.getByRole('button',{name:'Record bill payment'}).click();
    const dialog=page.getByRole('dialog',{name:'Record bill payment'});
    await dialog.getByLabel('Amount paid').fill('88000');
    await dialog.getByRole('button',{name:'Confirm bill payment'}).click();
    await expect(dialog).toHaveCount(0);
    await expect(bills.getByRole('note')).toContainText('Unmatched payment · ₹13,000.00');
    await expect(bills.getByRole('progressbar')).toHaveAttribute('aria-valuenow','100');
    await expect(bills).toContainText('₹0.00 remaining');
    await bills.getByText('Payment history',{exact:true}).click();
    await expect(bills).toContainText('₹88,000.00 paid');
    expect(payments[0].amount).toBe(88000);
    await bills.getByRole('button',{name:'Record bill payment'}).click();
    await expect(dialog.getByLabel('Amount paid')).toHaveValue('');
    await dialog.getByLabel('Amount paid').fill('500');
    await dialog.getByRole('button',{name:'Confirm bill payment'}).click();
    await expect(bills.getByRole('note')).toContainText('₹13,500.00');
    expect(payments).toHaveLength(2);
  });
}
