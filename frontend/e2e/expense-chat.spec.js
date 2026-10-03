import { expect, test } from '@playwright/test';

const answer = {
  answer: 'Your recorded food expenses were ₹750 across 2 records.',
  evidence: [{ query: { startDate: '2026-09-01', endDate: '2026-10-01', groupBy: ['category'], filters: [] }, currency: 'INR', matchingCount: 2, matchingTotal: 750, rows: [{ category: 'Food', total: 750, count: 2 }], truncated: false }]
};
async function dashboard(page) {
  const savedConversations = new Map();
  let activeProfile = 'real';
  await page.route('**/api/web/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('demo-profile') && route.request().method() === 'PUT') activeProfile = route.request().postDataJSON().enabled ? 'demo' : 'real';
    const json = path.endsWith('/ai-credits/permissions') ? { admin: true } : path.includes('/ai-credits/') ? [] : path.endsWith('/ai-credits') ? { available: 100, balance: 100, reserved: 0, paused: false, enabled: true, configured: true } : path.endsWith('demo-profile') ? { demoMode: false, canUseDemoMode: true }
      : path.endsWith('/calendar') ? { currency: 'INR', totalSpend: 750, transactionCount: 2, days: [] }
      : path.endsWith('/monthly-commitment') ? { commitment: null } : { items: [], actions: [], commitments: [], loans: [], funds: [], stocks: [] };
    return route.fulfill({ json });
  });
  await page.route('**/api/web/expense-chat/conversations**', route => {
    const request = route.request();
    const profileChats = savedConversations.get(activeProfile) || new Map();
    if (request.method() === 'GET') return route.fulfill({ json: [...profileChats.values()].reverse() });
    const conversation = request.postDataJSON();
    profileChats.set(conversation.id, conversation);
    savedConversations.set(activeProfile, profileChats);
    return route.fulfill({ json: conversation });
  });
  await page.route('**/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.goto('/dashboard?month=2026-09');
  const assistantTab = page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: 'Ask about your money', exact: true });
  await expect(assistantTab).toBeVisible();
  await expect(page.locator('.chat-launcher')).toHaveCount(0);
  await assistantTab.click();
  await expect(assistantTab).toHaveClass(/active/);
  await expect(page.locator('.app-shell').getByRole('region', { name: 'Money assistant' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Where did my money go?' })).toBeEnabled();
  return { setProfile: profile => { activeProfile = profile; } };
}

test('restores profile chat, evidence and draft after refresh without sending them to the model', async ({ page }) => {
  await dashboard(page);
  const modelRequests = [];
  await page.route('**/api/web/expense-chat', route => {
    modelRequests.push(route.request().postDataJSON());
    return route.fulfill({ json: answer });
  });
  await page.getByRole('button', { name: 'Where did my money go?' }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
  await page.getByLabel('Your money question').fill('And next month?');
  await page.waitForTimeout(650);
  await page.reload();
  await page.getByRole('button', { name: 'Ask about your money', exact: true }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
  await expect(page.getByLabel('Your money question')).toHaveValue('And next month?');
  expect(modelRequests).toHaveLength(1);
  await page.getByText('Based on 1 data query').click();
  await expect(page.getByText('2 matching records', { exact: false })).toBeVisible();
});

test('starter question, evidence, follow-up history and new chat', async ({ page }) => {
  await dashboard(page);
  const requests = [];
  await page.route('**/api/web/expense-chat', route => {
    requests.push(route.request().postDataJSON());
    return route.fulfill({ json: answer });
  });
  await page.getByRole('button', { name: 'Where did my money go?' }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
  expect(requests[0]).toEqual({ message: 'Where did my money go?', month: '2026-09', history: [], requestId: expect.any(String) });
  await page.getByText('Based on 1 data query').click();
  await expect(page.getByText('2 matching records', { exact: false })).toBeVisible();
  await page.getByLabel('Your money question').fill('And excluding rent?');
  await page.getByRole('button', { name: 'Send question' }).click();
  await expect.poll(() => requests.length).toBe(2);
  expect(requests[1].history).toEqual([{ role: 'user', content: requests[0].message }, { role: 'assistant', content: answer.answer }]);
  await expect(page.getByRole('button', { name: 'New chat' })).toBeEnabled();
  await page.getByRole('button', { name: 'New chat' }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toHaveCount(0);
  const history = page.getByRole('navigation', { name: 'Recent money chats' });
  await expect(history.getByRole('button', { name: /Where did my money go/ })).toBeVisible();
  await history.getByRole('button', { name: /Where did my money go/ }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toHaveCount(2);
  await page.screenshot({ path: 'test-results/expense-chat-desktop.png' });
});

test('failed question remains editable and retry does not duplicate history', async ({ page }) => {
  await dashboard(page);
  await page.route('**/api/web/expense-chat', route => route.fulfill({ status: 503, json: { message: 'Expense chat is not configured yet.' } }));
  await page.getByRole('button', { name: 'Which were my largest expenses?' }).click();
  await expect(page.getByRole('alert')).toContainText('not configured');
  await expect(page.getByLabel('Your money question')).toHaveValue('Which were my largest expenses?');
  await page.route('**/api/web/expense-chat', route => {
    expect(route.request().postDataJSON().history).toEqual([]);
    return route.fulfill({ json: answer });
  });
  await page.getByRole('button', { name: 'Send question' }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
});

test('fits mobile, disables offline questions and clears on profile switch', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const fixture = await dashboard(page);
  await page.route('**/api/web/expense-chat', route => route.fulfill({ json: answer }));
  await page.getByRole('button', { name: 'Where did my money go?' }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
  const box = await page.getByRole('region', { name: 'Money assistant' }).boundingBox();
  expect(box.x).toBeGreaterThanOrEqual(0);
  expect(box.x + box.width).toBeLessThanOrEqual(390);
  await page.screenshot({ path: 'test-results/expense-chat-mobile.png' });
  await page.evaluate(() => window.dispatchEvent(new Event('offline')));
  await expect(page.getByLabel('Your money question')).toBeDisabled();
  await page.getByRole('button', { name: 'Close money chat' }).click();
  // Switching the active profile unmounts the chat component and drops its history.
  await page.route('**/api/web/auth/demo-profile', route => route.fulfill({ json: { demoMode: true, canUseDemoMode: true } }));
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: 'You', exact: true }).click();
  // The V1 header exposes the profile toggle as a checkbox.
  const toggle = page.getByLabel(/demo mode/i);
  fixture.setProfile('demo');
  await toggle.check();
  await page.getByRole('button', { name: 'Ask about your money', exact: true }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toHaveCount(0);
  await expect(page.getByRole('navigation', { name: 'Recent money chats' })).toHaveCount(0);
});


test('keeps conversations, drafts and month context separate when reopening old chats', async ({ page }) => {
  await dashboard(page);
  const requests = [];
  await page.route('**/api/web/expense-chat', route => {
    const request = route.request().postDataJSON();
    requests.push(request);
    return route.fulfill({ json: { answer: `Reply to ${request.message}`, evidence: [] } });
  });
  await page.getByRole('button', { name: 'Where did my money go?', exact: true }).click();
  await expect(page.getByText('Reply to Where did my money go?', { exact: true })).toBeVisible();
  await page.getByLabel('Your money question').fill('Continue the first chat');
  await page.getByRole('button', { name: 'New chat', exact: true }).click();
  await page.evaluate(() => {
    history.pushState({}, '', '/dashboard?month=2026-08');
    dispatchEvent(new PopStateEvent('popstate'));
  });
  await expect(page.getByText('Exploring 2026-08', { exact: true })).toBeVisible();
  await page.getByLabel('Your money question').fill('Show my August spending');
  await page.getByRole('button', { name: 'Send question' }).click();
  await expect(page.getByText('Reply to Show my August spending', { exact: true })).toBeVisible();
  expect(requests[1].history).toEqual([]);
  expect(requests[1].month).toBe('2026-08');
  await page.getByLabel('Your money question').fill('Draft for August');
  const chats = page.getByRole('navigation', { name: 'Recent money chats' });
  await chats.getByRole('button', { name: /Where did my money go/ }).click();
  await expect(page.getByText('Exploring 2026-09', { exact: true })).toBeVisible();
  await expect(page.getByLabel('Your money question')).toHaveValue('Continue the first chat');
  await expect(page.getByText('Reply to Show my August spending', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Send question' }).click();
  await expect(page.getByText('Reply to Continue the first chat', { exact: true })).toBeVisible();
  expect(requests[2].month).toBe('2026-09');
  expect(requests[2].history).toEqual([
    { role: 'user', content: 'Where did my money go?' },
    { role: 'assistant', content: 'Reply to Where did my money go?' }
  ]);
  await page.getByRole('button', { name: 'Close money chat' }).click();
  await page.getByRole('button', { name: 'Ask about your money', exact: true }).click();
  await chats.getByRole('button', { name: /Show my August spending/ }).click();
  await expect(page.getByLabel('Your money question')).toHaveValue('Draft for August');
  await expect(page.getByText('Reply to Show my August spending', { exact: true })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(chats.getByRole('button', { name: /Show my August spending/ })).toBeVisible();
  await page.getByRole('button', { name: 'New chat', exact: true }).click();
  await expect(chats.getByText('New conversation')).toBeVisible();
  const dimensions = await chats.evaluate(el => ({ width: el.clientWidth, content: el.scrollWidth, right: el.getBoundingClientRect().right }));
  expect(dimensions.content).toBeGreaterThan(dimensions.width);
  expect(dimensions.right).toBeLessThanOrEqual(390);
  await page.screenshot({ path: 'test-results/expense-chat-history-mobile.png' });
});

test('prevents switching conversations while a reply is pending', async ({ page }) => {
  await dashboard(page);
  let finish;
  const responseReady = new Promise(resolve => { finish = resolve; });
  await page.route('**/api/web/expense-chat', async route => {
    await responseReady;
    await route.fulfill({ json: answer });
  });
  await page.getByRole('button', { name: 'Where did my money go?', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: /Checking your financial records|Reading your expenses/ })).toBeVisible();
  await expect(page.getByRole('button', { name: 'New chat', exact: true })).toBeDisabled();
  await expect(page.getByRole('navigation', { name: 'Recent money chats' }).getByRole('button')).toBeDisabled();
  finish();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'New chat', exact: true })).toBeEnabled();
});


test('shows monthly plan, conditional scenario and cross-module evidence', async ({ page }) => {
  await dashboard(page);
  const nextMonth = { kind: 'plan', month: '2026-10', currency: 'INR', baselineTotal: 75000, proposedTotal: 75000,
    incomeStatus: 'EXACT_MONTHLY_ESTIMATE', baselineAfterIncome: -15000, proposedAfterIncome: -15000,
    stillToPay: 45000, pendingInvesting: 20000, pendingSavings: 2000,
    items: [{ label: 'Home EMI', dueDate: '2026-10-05', baseline: 30000, proposed: 30000, reduction: 0,
      condition: 'Obligation: preserve payment.', remainingPayments: 4, endsInMonth: '2027-01', freesFromMonth: '2027-02', detail: 'Recorded schedule' }, { label: 'Index SIP', dueDate: '2026-10-12', baseline: 20000, proposed: 20000,
      reduction: 0, condition: 'Review provider terms.' }], limitations: ['Additional living costs are not included.'] };
  const scenario = { ...nextMonth, kind: 'scenario', proposedTotal: 60000, proposedAfterIncome: 0,
    items: [nextMonth.items[0], { ...nextMonth.items[1], proposed: 5000, reduction: 15000 }] };
  const portfolio = { kind: 'records', module: 'mutual_funds', view: 'records', currency: 'INR', matchingCount: 1,
    rows: [{ name: 'Index fund', invested_amount: 1000, units: 10 }], truncated: false,
    note: 'Stored holdings only. No live market value.' };
  await page.route('**/api/web/expense-chat', route => route.fulfill({ json: {
    answer: 'The recorded plan is ₹75,000 against your monthly income estimate, a ₹15,000 shortfall. Reducing the SIP is only a hypothetical option.',
    evidence: [nextMonth, scenario, portfolio, {kind: 'records', module: 'credit_card_bills', view: 'records',
      currency: 'INR', matchingCount: 1, rows: [{card_id: 7, name: 'Travel card', captured_bill_amount: 4000,
      paid_amount: 4500, remaining_amount: 0, monthly_purchase_amount: 900, unmatched_payment_amount: 500}],
      truncated: false, note: 'Captured bills and recorded settlements only.'}]
  } }));
  await page.getByRole('button', { name: 'Can I cover next month’s commitments with my monthly income?' }).click();
  await expect(page.getByText(/₹15,000 shortfall/)).toBeVisible();
  await page.getByText('Based on 4 data queries').click();
  await expect(page.getByRole('table', { name: 'Scenario comparison' })).toBeVisible();
  await expect(page.getByText('Hypothetical only · No records changed')).toBeVisible();
  await expect(page.getByText('Obligation: preserve payment.', { exact: true })).toHaveCount(2);
  await expect(page.getByText('Stored holdings only. No live market value.')).toBeVisible();
  await expect(page.getByText('Travel card', { exact: true })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Unmatched payment amount' })).toBeVisible();
  await expect(page.getByRole('row', { name: /Still to pay/ })).toHaveCount(1);
  await expect(page.getByRole('row', { name: /Pending investing/ })).toHaveCount(1);
  await expect(page.getByRole('row', { name: /Pending earmarked savings/ })).toHaveCount(1);
  await expect(page.getByText(/4 scheduled payments remaining/)).toHaveCount(2);
  await expect(page.getByRole('row', { name: /Against monthly income estimate/ })).toHaveCount(2);
  await page.screenshot({ path: 'test-results/money-chat-scenario.png' });
});


test('exhausted credits block new questions but keep saved chats readable', async ({ page }) => {
  await dashboard(page);
  await page.route('**/api/web/expense-chat', route => route.fulfill({ json: answer }));
  await page.getByRole('button', { name: 'Where did my money go?' }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
  await page.route('**/api/web/ai-credits', route => route.fulfill({ json: { balance: 0, available: 0, reserved: 0, paused: false, enabled: true, configured: true } }));
  await page.getByRole('button', { name: 'Refresh credits', exact: true }).click();
  await expect(page.getByText('You’ve used your AI credits.', { exact: false })).toBeVisible();
  await page.getByLabel('Your money question').fill('Another question');
  await expect(page.getByRole('button', { name: 'Send question' })).toBeDisabled();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
  await page.route('**/api/web/ai-credits', route => route.fulfill({ json: { balance: 20, available: 20, reserved: 0, paused: false, enabled: true, configured: true } }));
  await page.getByRole('button', { name: 'Refresh credits', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Send question' })).toBeEnabled();
});

test('network retry reuses request ID and shows held credits', async ({ page }) => {
  await dashboard(page);
  const requests = [];
  await page.route('**/api/web/expense-chat', route => {
    requests.push(route.request().postDataJSON());
    return requests.length === 1 ? route.abort('failed') : route.fulfill({ json: answer });
  });
  await page.getByRole('button', { name: 'Where did my money go?' }).click();
  await expect(page.getByLabel('Your money question')).toHaveValue('Where did my money go?');
  await page.route('**/api/web/ai-credits', route => route.fulfill({ json: { balance: 0, available: 0, reserved: 0, paused: false, enabled: true, configured: true } }));
  await page.getByRole('button', { name: 'Refresh credits', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Send question' })).toBeDisabled();
  await page.getByRole('button', { name: 'Check previous answer' }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
  expect(requests[1].requestId).toBe(requests[0].requestId);
  await page.route('**/api/web/ai-credits', route => route.fulfill({ json: { balance: 20, available: 18, reserved: 2, paused: false, enabled: true, configured: true } }));
  await page.getByRole('button', { name: 'Refresh credits', exact: true }).click();
  await expect(page.getByText('18 credits available · 2 on hold')).toBeVisible();
});

test('admin can grant credits and pause a friend from profile settings', async ({ page }) => {
  await dashboard(page);
  await page.getByRole('button', { name: 'Close money chat' }).click();
  const friend = { id: 2, externalUserId: '919876543210', channel: 'WHATSAPP', balance: 0, reserved: 0, available: 0, paused: false };
  let grant;
  await page.route('**/api/web/ai-credits/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/permissions')) return route.fulfill({ json: { admin: true } });
    if (path.endsWith('/grants')) { grant = route.request().postDataJSON(); friend.available += grant.amount; friend.balance += grant.amount; return route.fulfill({ json: friend }); }
    if (path.endsWith('/access')) { friend.paused = route.request().postDataJSON().paused; return route.fulfill({ json: friend }); }
    if (path.endsWith('/users')) return route.fulfill({ json: [friend] });
    return route.fulfill({ json: [] });
  });
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: 'You', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Manage friends’ credits' })).toBeVisible();
  await page.getByLabel('Grant to').selectOption('2');
  await page.getByLabel('Credits to add').fill('25');
  await page.getByLabel('Reason', { exact: true }).fill('Welcome allowance');
  await page.getByRole('button', { name: 'Add credits', exact: true }).click();
  await expect(page.getByText('Credits added.', { exact: true })).toBeVisible();
  expect(grant).toMatchObject({ amount: 25, note: 'Welcome allowance', requestId: expect.any(String) });
  await expect(page.getByText('WHATSAPP · 25 available · 0 held')).toBeVisible();
  await page.getByRole('button', { name: 'Pause', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Resume', exact: true })).toBeVisible();
});

test('credit loading failure fails closed and regular users see no grant controls', async ({ page }) => {
  await dashboard(page);
  await page.route('**/api/web/ai-credits', route => route.fulfill({ status: 503, json: { message: 'Credits temporarily unavailable' } }));
  await page.getByRole('button', { name: 'Refresh credits', exact: true }).click();
  await expect(page.getByText('Credits temporarily unavailable')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Where did my money go?' })).toBeDisabled();
  await page.route('**/api/web/auth/demo-profile', route => route.fulfill({ json: { demoMode: false, canUseDemoMode: false } }));
  await page.route('**/api/web/ai-credits/permissions', route => route.fulfill({ json: { admin: false } }));
  await page.route('**/api/web/ai-credits/ledger', route => route.fulfill({ json: [] }));
  await page.reload();
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: 'You', exact: true }).click();
  await expect(page.getByLabel(/demo mode/i)).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'AI credits', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Manage friends’ credits' })).toHaveCount(0);
});


test('mobile question focus keeps readable text and fits narrow screens', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 720 });
  await dashboard(page);
  const question = page.getByLabel('Your money question');
  await question.focus();
  await expect(question).toBeFocused();
  expect(await question.evaluate(input => parseFloat(getComputedStyle(input).fontSize))).toBeGreaterThanOrEqual(16);
  await question.fill('What did I spend this month?');
  await page.setViewportSize({ width: 320, height: 420 });
  await question.scrollIntoViewIfNeeded();
  await expect(question).toBeInViewport();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect(await page.locator('meta[name="viewport"]').getAttribute('content')).not.toMatch(/user-scalable=no|maximum-scale=1/);
});

async function fakeMicrophone(page, denied = false) {
  await page.addInitScript(({ denied }) => {
    window.voiceTracksStopped = 0;
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: {
      getUserMedia: async () => {
        if (denied) throw new DOMException('Denied', 'NotAllowedError');
        return { getTracks: () => [{ stop: () => { window.voiceTracksStopped++; } }] };
      }
    } });
    window.MediaRecorder = class {
      static isTypeSupported(type) { return type.startsWith('audio/webm'); }
      constructor(stream, options) { this.mimeType = options.mimeType; this.state = 'inactive'; }
      start() { this.state = 'recording'; }
      stop() {
        this.state = 'inactive';
        queueMicrotask(() => {
          this.ondataavailable?.({ data: new Blob(['voice-example'], { type: this.mimeType }) });
          this.onstop?.();
        });
      }
    };
  }, { denied });
}

test('voice uploads multipart audio and reviews editable text before sending', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 720 });
  await fakeMicrophone(page);
  await dashboard(page);
  let uploads = 0, questions = 0;
  await page.route('**/api/web/expense-chat/transcribe', route => {
    uploads++;
    expect(route.request().headers()['content-type']).toContain('multipart/form-data; boundary=');
    expect(route.request().postDataBuffer().toString()).toContain('question.webm');
    return route.fulfill({ json: { text: 'Where did my money go?' } });
  });
  await page.route('**/api/web/expense-chat', route => {
    questions++;
    expect(route.request().postDataJSON().message).toBe('Which were my largest expenses?');
    return route.fulfill({ json: answer });
  });
  await page.getByRole('button', { name: 'Record voice question' }).click();
  await expect(page.getByText(/Recording ·/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Send question' })).toBeDisabled();
  await page.getByRole('button', { name: 'Stop and transcribe' }).click();
  await expect(page.getByLabel('Your money question')).toHaveValue('Where did my money go?');
  await expect(page.getByText('Review your voice question below. Edit it, then tap Send.')).toBeVisible();
  expect(uploads).toBe(1); expect(questions).toBe(0);
  expect(await page.evaluate(() => window.voiceTracksStopped)).toBe(1);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByLabel('Your money question').fill('Which were my largest expenses?');
  await page.getByRole('button', { name: 'Send question' }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
  expect(questions).toBe(1);
});

test('voice cancellation and closing release microphone without uploading', async ({ page }) => {
  await fakeMicrophone(page); await dashboard(page);
  let uploads = 0;
  await page.route('**/api/web/expense-chat/transcribe', route => { uploads++; return route.fulfill({ json: { text: 'Unexpected' } }); });
  await page.getByRole('button', { name: 'Record voice question' }).click();
  await expect(page.getByText(/Recording ·/)).toBeVisible();
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Record voice question' })).toBeVisible();
  expect(await page.evaluate(() => window.voiceTracksStopped)).toBe(1);
  await page.getByRole('button', { name: 'Record voice question' }).click();
  await expect(page.getByText(/Recording ·/)).toBeVisible();
  await page.getByRole('button', { name: 'Close money chat' }).click();
  await expect(page.getByRole('region', { name: 'Money assistant' })).toHaveCount(0);
  expect(await page.evaluate(() => window.voiceTracksStopped)).toBe(2); expect(uploads).toBe(0);
});

test('voice permission denial keeps typed questions usable', async ({ page }) => {
  await fakeMicrophone(page, true); await dashboard(page);
  await page.getByRole('button', { name: 'Record voice question' }).click();
  await expect(page.getByRole('alert')).toContainText('Microphone access was denied');
  await page.getByLabel('Your money question').fill('My typed question');
  await expect(page.getByRole('button', { name: 'Send question' })).toBeEnabled();
});

test('voice retries failed transcription and discard restores the typed draft', async ({ page }) => {
  await fakeMicrophone(page); await dashboard(page);
  await page.getByLabel('Your money question').fill('Existing draft');
  let uploads = 0;
  await page.route('**/api/web/expense-chat/transcribe', route => {
    uploads++;
    return uploads === 1 ? route.fulfill({ status: 503, json: { code: 'VOICE_UNAVAILABLE', message: 'Could not transcribe this recording.' } })
      : route.fulfill({ json: { text: 'Voice words' } });
  });
  await page.getByRole('button', { name: 'Record voice question' }).click();
  await page.getByRole('button', { name: 'Stop and transcribe' }).click();
  await expect(page.getByRole('alert')).toContainText('Could not transcribe');
  await page.getByRole('button', { name: 'Retry transcription' }).click();
  await expect(page.getByLabel('Your money question')).toHaveValue('Existing draft\nVoice words');
  await page.getByRole('button', { name: 'Discard voice text' }).click();
  await expect(page.getByLabel('Your money question')).toHaveValue('Existing draft');
  expect(uploads).toBe(2);
});

test('cancelled transcription ignores late words and preserves the draft', async ({ page }) => {
  await fakeMicrophone(page); await dashboard(page);
  await page.getByLabel('Your money question').fill('Keep this draft');
  let upload;
  await page.route('**/api/web/expense-chat/transcribe', route => { upload = route; });
  await page.getByRole('button', { name: 'Record voice question' }).click();
  await page.getByRole('button', { name: 'Stop and transcribe' }).click();
  await expect(page.getByText('Transcribing your voice…')).toBeVisible();
  await expect.poll(() => !!upload).toBe(true);
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  await upload.fulfill({ json: { text: 'Late transcript' } }).catch(() => {});
  await expect(page.getByLabel('Your money question')).toHaveValue('Keep this draft');
  await expect(page.getByRole('button', { name: 'Discard voice text' })).toHaveCount(0);
  expect(await page.evaluate(() => window.voiceTracksStopped)).toBe(1);
});

test('30-second recording cutoff discards audio and asks for a fresh recording', async ({ page }) => {
  await page.clock.install();
  await fakeMicrophone(page); await dashboard(page);
  const question = page.getByLabel('Your money question');
  await question.fill('Keep my typed draft');
  let uploads = 0;
  await page.route('**/api/web/expense-chat/transcribe', route => {
    uploads++; return route.fulfill({ json: { text: 'Short question' } });
  });
  await page.getByRole('button', { name: 'Record voice question' }).click();
  await expect(page.getByText(/Recording · 0s \/ 30s/)).toBeVisible();
  await page.clock.runFor(29_000);
  await expect(page.getByText(/Recording · 29s \/ 30s/)).toBeVisible();
  expect(await page.evaluate(() => window.voiceTracksStopped)).toBe(0);
  await page.clock.runFor(1_000);
  await expect(page.getByRole('alert')).toContainText('30-second limit. Please re-record a shorter question.');
  await expect(page.getByRole('button', { name: 'Stop and transcribe' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Retry transcription' })).toHaveCount(0);
  await expect(question).toHaveValue('Keep my typed draft');
  expect(await page.evaluate(() => window.voiceTracksStopped)).toBe(1);
  expect(uploads).toBe(0);
  await page.getByRole('button', { name: 'Record voice question' }).click();
  await expect(page.getByText(/Recording · 0s \/ 30s/)).toBeVisible();
  await page.clock.runFor(1_000);
  await page.getByRole('button', { name: 'Stop and transcribe' }).click();
  await expect(question).toHaveValue('Keep my typed draft\nShort question');
  expect(uploads).toBe(1);
  expect(await page.evaluate(() => window.voiceTracksStopped)).toBe(2);
});

test('mic follows Send inside composer and recording shows animated feedback', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await fakeMicrophone(page); await dashboard(page);
  const composer = page.locator('.composer');
  const send = composer.getByRole('button', { name: 'Send question', exact: true });
  const mic = composer.getByRole('button', { name: 'Record voice question', exact: true });
  await expect(mic).toBeVisible();
  const sendBox = await send.boundingBox(), micBox = await mic.boundingBox();
  expect(micBox.x).toBeGreaterThanOrEqual(sendBox.x + sendBox.width);
  expect(micBox.y).toBe(sendBox.y);
  await mic.click();
  await expect(composer.getByRole('button', { name: 'Stop and transcribe', exact: true })).toBeVisible();
  const bars = composer.locator('.recording-wave i');
  await expect(bars).toHaveCount(9);
  expect(await bars.first().evaluate(bar => getComputedStyle(bar).animationName)).not.toBe('none');
  await expect(composer.getByText(/Recording ·/)).toBeVisible();
  await page.screenshot({ path: 'test-results/voice-recording-mobile.png' });
  await page.setViewportSize({ width: 320, height: 720 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  expect(await bars.first().evaluate(bar => getComputedStyle(bar).animationName)).toBe('none');
  await composer.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(composer.locator('.recording-wave')).toHaveCount(0);
  await expect(composer.getByRole('button', { name: 'Record voice question', exact: true })).toBeVisible();
});
