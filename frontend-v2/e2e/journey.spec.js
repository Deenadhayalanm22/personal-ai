import { test, expect } from '@playwright/test';
test('sample journey travels through facts without backend requests',async({page})=>{
 const apiRequests=[]; const errors=[];page.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))apiRequests.push(r.url());});page.on('pageerror',e=>errors.push(e.message));
 await page.goto('/');await expect(page.getByText('Stage 01 · Sample data only',{exact:false})).toBeVisible();
 await expect(page.getByRole('heading',{name:'Your contributions reached ₹4,000'})).toBeVisible();
 await page.getByRole('button',{name:'16 September',exact:true}).click();await expect(page.getByRole('heading',{name:'Your first investment contribution'})).toBeVisible();
 await page.getByRole('button',{name:'View supporting details'}).click();await expect(page.getByRole('dialog')).toContainText('does not represent investment returns');await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).not.toBeVisible();
 await page.getByLabel('Jump to date',{exact:true}).fill('2026-09-19');await expect(page.getByRole('heading',{name:'No activity recorded'})).toBeVisible();
 await page.getByRole('button',{name:'15 September',exact:true}).click();await expect(page.getByRole('button',{name:'Previous day',exact:true})).toBeDisabled();
 await page.getByRole('button',{name:'15 September',exact:true}).focus();await page.keyboard.press('ArrowRight');await expect(page.getByRole('heading',{name:'Your first investment contribution'})).toBeVisible();
 await page.getByRole('button',{name:'Latest demo day'}).click();await expect(page.getByRole('button',{name:'Next day',exact:true})).toBeDisabled();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();expect(apiRequests).toEqual([]);expect(errors).toEqual([]);
});
test('secondary tools are explicit previews and return to selected day',async({page})=>{
 await page.goto('/');await page.getByRole('button',{name:'18 September',exact:true}).click();await page.getByRole('button',{name:'Money',exact:true}).click();await page.getByRole('button',{name:/PREVIEW.*Commitments/}).click();await expect(page.getByRole('dialog')).toContainText('no real records');await page.getByRole('button',{name:'Close details'}).click();await page.getByRole('button',{name:'Journey',exact:true}).click();await expect(page.getByRole('heading',{name:'Halfway to your insurance savings target'})).toBeVisible();await expect(page.getByRole('navigation',{name:'Main navigation'}).getByRole('button')).toHaveCount(2);
});
test('reduced motion and swipe navigation',async({page})=>{
 await page.emulateMedia({reducedMotion:'reduce'});await page.goto('/');expect(await page.locator('.traveller').evaluate(el=>getComputedStyle(el).transitionDuration)).toBe('0s');
 await page.locator('.scene').evaluate(el=>{const touch=(x,y)=>new Touch({identifier:1,target:el,clientX:x,clientY:y});el.dispatchEvent(new TouchEvent('touchstart',{bubbles:true,touches:[touch(100,100)]}));el.dispatchEvent(new TouchEvent('touchend',{bubbles:true,changedTouches:[touch(220,110)]}));});await expect(page.getByRole('heading',{name:'Another loan payment recorded'})).toBeVisible();
});

test('simple summary leads and quiet days do not invent a story',async({page},testInfo)=>{
 await page.goto('/');
 await expect(page.locator('.story-illustration')).toHaveCount(1);
 await expect(page.getByRole('region',{name:'Explore your landmarks'})).toHaveCount(0);
 if(testInfo.project.name==='mobile') {
   const value=await page.locator('.story-value').boundingBox();
   const nav=await page.getByRole('navigation',{name:'Main navigation'}).boundingBox();
   expect(value.y+value.height).toBeLessThan(nav.y);
 }
 await page.getByRole('button',{name:'17 September',exact:true}).click();
 await expect(page.locator('.story-illustration')).toHaveCount(0);
 await expect(page.getByRole('heading',{name:'₹1,380 spent on everyday needs'})).toBeVisible();
 await page.getByRole('button',{name:'19 September',exact:true}).click();
 await expect(page.getByRole('button',{name:'View supporting details'})).toHaveCount(0);
 await expect(page.locator('.story-illustration')).toHaveCount(0);
 await expect(page.locator('.story-value')).toHaveCount(0);
});

test('Tell us previews, edits and confirms only local dated sample activity',async({page})=>{
 const requests=[];page.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))requests.push(r.url());});
 await page.goto('/');await page.getByRole('button',{name:'19 September',exact:true}).click();
 await expect(page.getByRole('heading',{name:'Add something for 19 September'})).toBeVisible();
 await page.getByLabel('Tell us about your spending').fill('Lunch ₹180 and Auto ₹90');await page.getByRole('button',{name:'Tell us',exact:false}).click();
 await expect(page.getByRole('dialog')).toContainText('19 September 2026');
 await expect(page.locator('.activity-row')).toHaveCount(0);
 await page.getByLabel('Expense 2 amount').fill('95.50');await page.getByLabel('Expense 2 description').fill('Auto ride');
 await page.getByRole('button',{name:'Add 2 sample expenses',exact:true}).click();
 await expect(page.getByRole('dialog')).not.toBeVisible();await expect(page.locator('.activity-row')).toHaveCount(2);
 await expect(page.locator('.story-value')).toContainText('₹275.5');
 await page.getByRole('button',{name:'18 September',exact:true}).click();await expect(page.locator('.activity-row')).toHaveCount(1);
 await page.getByRole('button',{name:'19 September',exact:true}).click();await expect(page.locator('.activity-row')).toHaveCount(2);
 await page.reload();await page.getByRole('button',{name:'19 September',exact:true}).click();await expect(page.locator('.activity-row')).toHaveCount(0);expect(requests).toEqual([]);
});
test('Tell us cancellation, invalid input and questions make no changes',async({page})=>{
 await page.goto('/');const input=page.getByLabel('Tell us about your spending');const send=page.getByRole('button',{name:'Tell us',exact:false});
 await input.fill('Lunch ₹180');await send.click();await page.getByRole('button',{name:'Cancel expense preview'}).click();await expect(page.locator('.activity-row')).toHaveCount(1);await expect(input).toHaveValue('Lunch ₹180');
 for(const message of ['Why did I spend more?', 'Investment ₹2000', 'Lunch ₹180 and Auto', 'Lunch -20', 'Lunch 0', 'Yesterday lunch 180']) {
   await input.fill(message);await send.click();await expect(page.getByRole('alert')).toBeVisible();await expect(page.getByRole('dialog')).not.toBeVisible();await expect(page.locator('.activity-row')).toHaveCount(1);
 }
 await page.getByRole('button',{name:'20 September',exact:true}).click();await expect(page.getByLabel('Tell us about your spending')).toHaveValue('');
});
