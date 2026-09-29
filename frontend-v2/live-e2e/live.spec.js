import { test, expect } from '@playwright/test';

test('v2 sign-in reuses the existing portal form and login endpoint',async ({page})=>{
  const submitted=[];
  await page.route('**/api/web/auth/login-link',async route=>{
    submitted.push(route.request().postDataJSON());
    await route.fulfill({status:200,contentType:'application/json',body:'{"message":"If this number is registered, we sent a login link."}'});
  });
  await page.goto('/auth.html');
  await expect(page.getByRole('heading',{name:'Sign in to your portal'})).toBeVisible();
  await page.getByLabel('Phone number').fill('12345');
  await page.getByRole('button',{name:'Send link on WhatsApp'}).click();
  await expect(page.getByRole('alert')).toContainText('valid 10-digit Indian mobile number');
  expect(submitted).toHaveLength(0);
  await page.getByLabel('Phone number').fill('9876543210');
  await page.getByRole('button',{name:'Send link on WhatsApp'}).click();
  await expect(page.getByRole('heading',{name:'Check your WhatsApp'})).toBeVisible();
  expect(submitted).toEqual([{phoneNumber:'+919876543210'}]);
});

async function mockOwnedApi(page) {
  const calls=[];
  await page.route('**/api/web/**',async route=>{
    const url=new URL(route.request().url());const path=url.pathname;calls.push({path,method:route.request().method(),body:route.request().postDataJSON?.()});
    let body={};
    if(path.endsWith('/auth/demo-profile')) body={demoMode:false,canUseDemoMode:false};
    else if(path.endsWith('/expenses/calendar')) body={month:url.searchParams.get('month'),timezone:'Asia/Kolkata',currency:'INR',days:url.searchParams.get('month')==='2026-09'?[{date:'2026-09-19',transactionCount:1,totalSpend:250,intensity:1},{date:'2026-09-20',transactionCount:0,totalSpend:0,intensity:0}]:[]};
    else if(path.endsWith('/expenses/monthly')) body={month:url.searchParams.get('month'),timezone:'Asia/Kolkata',currency:'INR',stories:[{storyId:'live-anchor',storyType:'MONTHLY_COMMITMENT',cardFace:{heading:'Monthly commitments',displayValue:'₹8,000'},period:{startDate:'2026-09-01',endDate:'2026-09-30'}},...(url.searchParams.get('month')==='2026-09'?[{storyId:'story-1',logicalStoryId:'logical-1',revision:1,storyType:'DAILY_OBSERVATION',generatedAt:'2026-09-21T10:00:00Z',period:{startDate:'2026-09-19',endDate:'2026-09-20',displayLabel:'19–20 September'},cardFace:{heading:'A small spending day'},cards:[{sequence:1,title:'Recorded spending',body:'One expense was recorded.'}],evidence:{transactions:[{transactionId:'77',dateLabel:'19 Sep',merchantLabel:'Shop',amount:{displayValue:'₹250'}}]}}]:[])]};
    else if(path.endsWith('/loans')) body={loans:[{id:4,loanName:'Home loan',loanType:'HOME',lenderName:'Bank',monthlyEmiAmount:8000,originalPrincipal:100000,totalTenureMonths:12,firstEmiDueDate:'2026-09-19',status:'ACTIVE',remainingEmiCount:2,emiOccurrences:[{month:'2026-09',dueDate:'2026-09-19',status:'DUE',plannedAmount:8000}]}]};
    else if(path.endsWith('/loans/4/history')) body={id:4,loanName:'Home loan',history:[{month:'2026-09',dueDate:'2026-09-19',status:'DUE',plannedAmount:8000}]};
    else if(path.endsWith('/recurring-commitments')) body={items:[]};
    else if(path.endsWith('/mutual-funds')) body={mutualFunds:[]};
    else if(path.endsWith('/stocks')) body={stocks:[]};
    else if(path.endsWith('/credit-cards')) body={cards:[]};
    else if(path.endsWith('/recurring-commitments/savings')) body=[];
    else if(path.endsWith('/expenses')) body={items:[{id:77,merchant:'Shop',category:'Shopping',originalMessage:'Paid 250',amount:250,transactionTime:'2026-09-19T00:00:00Z'}],nextBeforeId:null};
    else if(path.endsWith('/expenses/options')) body={categories:[],merchants:[],accounts:[]};
    else if(route.request().method()==='PATCH') body={id:77};
    else if(route.request().method()==='POST' && path.includes('/emi-occurrences/')) body={id:4};
    else if(path.endsWith('/auth/logout')) {await route.fulfill({status:204,body:''});return;}
    else {await route.fulfill({status:404,body:'{}',contentType:'application/json'});return;}
    await route.fulfill({status:200,body:JSON.stringify(body),contentType:'application/json'});
  });
  return calls;
}

test('bounded Journey reads show owner facts, story coverage and honest history',async ({page})=>{
  const calls=await mockOwnedApi(page);
  await page.goto('/?month=2026-09');
  await expect(page.getByRole('heading',{name:'Your journey'})).toBeVisible();
  await expect(page.getByText('₹250',{exact:false}).first()).toBeVisible();
  await page.getByRole('button',{name:/19 September/}).click();
  await expect(page.getByText('Home loan')).toBeVisible();
  await page.getByRole('button',{name:/A small spending day/}).click();
  await expect(page.getByRole('dialog',{name:'Published story'})).toContainText('Coverage: 19–20 September');
  await expect(page.getByRole('dialog',{name:'Published story'})).toContainText('Shop');
  await page.getByRole('button',{name:'Close'}).click();
  await expect(page.getByText('Earlier publications are unavailable through the current API.')).toBeVisible();
  await page.getByRole('button',{name:'Review in Money'}).click();
  await expect(page.getByRole('heading',{name:'Home loan'})).toBeVisible();
  expect(calls.filter(call=>call.path.endsWith('/expenses/calendar')).length).toBeLessThanOrEqual(2);
});

test('expense correction uses the owned edit endpoint and refreshes the affected month',async ({page})=>{
  const calls=await mockOwnedApi(page);
  await page.goto('/?month=2026-09');
  await page.getByRole('button',{name:/19 September/}).click();
  await page.getByRole('button',{name:'Correct'}).click();
  await page.getByRole('dialog',{name:'Correct expense'}).getByLabel('Amount').fill('300');
  await page.getByRole('button',{name:'Save correction'}).click();
  await expect(page.getByRole('status')).toContainText('Expense corrected');
  const patch=calls.find(call=>call.method==='PATCH');
  expect(patch.path).toBe('/api/web/expenses/77');
  expect(patch.body.amount).toBe(300);
});

test('Money source command and read-only exploration use existing APIs',async ({page})=>{
  const calls=await mockOwnedApi(page);
  await page.goto('/?month=2026-09');
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Money'}).click();
  await expect(page.getByText('Home loan')).toBeVisible();
  await page.locator('[data-testid="loans-section"]').getByRole('button',{name:'View details'}).click();
  await expect(page.getByRole('heading',{name:'Home loan'})).toBeVisible();
  await page.getByRole('button',{name:'Pay September EMI'}).click();
  expect(calls.some(call=>call.method==='POST' && call.path==='/api/web/loans/4/emi-occurrences/2026-09/paid')).toBe(true);
  await page.locator('.fund-detail .close').click();
  await page.locator('.money-modal .close').click();
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Explore'}).click();
  await expect(page.getByText('Story ID: story-1')).toBeVisible();
  await page.getByRole('button',{name:'What supports it?'}).click();
  await expect(page.getByText(/Shop · ₹250/)).toBeVisible();
});

test('expired session prevents live financial reads',async ({page})=>{
  const paths=[];
  await page.route('**/api/web/**',async route=>{paths.push(new URL(route.request().url()).pathname);await route.fulfill({status:401,body:'{}',contentType:'application/json'});});
  await page.goto('/?month=2026-09');
  await expect(page.getByRole('heading',{name:'Your session ended'})).toBeVisible();
  expect(paths).toEqual(['/api/web/auth/demo-profile']);
});

test('offline refresh labels stale facts and historic projection stays unavailable',async ({page})=>{
  await mockOwnedApi(page);
  await page.goto('/?month=2026-09');
  await expect(page.getByRole('heading',{name:'Your journey'})).toBeVisible();
  await expect(page.getByText(/Recorded expenses in 2026-09/)).toBeVisible();
  await page.route('**/api/web/expenses/calendar?**',route=>route.abort());
  await page.route('**/api/web/expenses/monthly?**',route=>route.abort());
  await page.getByRole('button',{name:'Refresh month'}).click();
  await expect(page.getByText(/Showing an older saved read/)).toBeVisible();
  await page.unroute('**/api/web/expenses/calendar?**');
  await page.unroute('**/api/web/expenses/monthly?**');
  await page.getByLabel('Month').fill('2026-08');
  await expect(page.getByRole('heading',{name:'Projection unavailable'})).toBeVisible();
  await expect(page.getByText('Historical commitments are not reconstructed from today’s plans.')).toBeVisible();
});

test('live date and story links survive reload and browser Back without adding history',async ({page})=>{
  await mockOwnedApi(page);
  await page.goto('/?month=2026-09&day=2026-09-19&story=story-1');
  await expect(page.getByRole('dialog',{name:'Published story'})).toBeVisible();
  await page.reload();
  await expect(page.getByRole('dialog',{name:'Published story'})).toBeVisible();
  await page.getByRole('button',{name:'Close'}).click();
  await expect(page).not.toHaveURL(/story=/);
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Money'}).click();
  await expect(page).toHaveURL(/view=money/);
  await page.goBack();
  await expect(page.getByRole('heading',{name:'Your journey'})).toBeVisible();
  await expect(page).toHaveURL(/day=2026-09-19/);
  await page.getByLabel('Month').fill('2026-08');
  await expect(page).toHaveURL(/month=2026-08/);
  await page.goBack();
  await expect(page.getByRole('heading',{name:'Your journey'})).toBeVisible();
  await expect(page).toHaveURL(/month=2026-09/);
});

test('a dated calendar item opens the existing loan payment flow',async ({page})=>{
  await page.clock.setFixedTime(new Date('2026-09-19T06:00:00Z'));
  const calls=await mockOwnedApi(page);
  await page.goto('/?month=2026-09&day=2026-09-19');
  const day=page.locator('#day-2026-09-19');
  await expect(day.getByRole('button',{name:'Record',exact:true})).toHaveCount(0);
  await day.getByRole('button',{name:'Review in Money'}).click();
  await expect(page.getByRole('heading',{name:'Home loan'})).toBeVisible();
  await page.getByRole('button',{name:'Pay September EMI'}).click();
  expect(calls.some(call=>call.method==='POST'&&call.path==='/api/web/loans/4/emi-occurrences/2026-09/paid')).toBe(true);
});

test('a dated commitment opens its existing details and Paid action',async ({page})=>{
  await page.clock.setFixedTime(new Date('2026-09-19T06:00:00Z'));
  const calls=await mockOwnedApi(page);
  await page.route('**/api/web/recurring-commitments**',async route=>{
    const path=new URL(route.request().url()).pathname;
    if(path.endsWith('/recurring-commitments')){
      const due={month:'2026-09',dueDate:'2026-09-19',status:'DUE'};
      await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({items:[{id:8,label:'Rent',planningAmount:5000,recurrenceUnit:'MONTH',recurrenceInterval:1,status:'ACTIVE',currentOccurrence:due,currentOccurrences:[due]}]})});return;
    }
    if(path.endsWith('/recurring-commitments/8/history')){
      await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({id:8,label:'Rent',planningAmount:5000,history:[]})});return;
    }
    await route.fallback();
  });
  await page.goto('/?month=2026-09&day=2026-09-19');
  await page.locator('#day-2026-09-19').locator('.live-occurrence').filter({hasText:'Rent'}).getByRole('button',{name:'Review in Money'}).click();
  await expect(page.getByRole('heading',{name:'Rent',exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Paid'})).toBeVisible();
  expect(calls.some(call=>call.method==='POST'&&call.path.includes('/recurring-commitments/8/occurrences'))).toBe(false);
});
