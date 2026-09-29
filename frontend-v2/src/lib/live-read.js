// FIN-EPIC-007: read-only v2 integration boundary. Server facts remain authoritative.
const monthPattern = /^\d{4}-(0[1-9]|1[0-2])$/;
const datePattern = /^\d{4}-(0[1-9]|1[0-2])-\d{2}$/;
const sources = new Set(['EXPENSE', 'COMMITMENT', 'SAVINGS', 'LOAN_EMI', 'MUTUAL_FUND_SIP', 'STOCK_PLAN']);
const states = new Set(['UPCOMING', 'DUE', 'OVERDUE', 'RECORDED', 'SKIPPED']);

export function isDate(value) {
  if (!datePattern.test(value || '')) return false;
  const date = new Date(`${value}T12:00:00Z`);
  return !Number.isNaN(date.valueOf()) && date.toISOString().slice(0, 10) === value;
}

export function occurrenceKey(item) {
  if (!sources.has(item?.source) || !item.sourceId || !isDate(item?.dueDate) || !monthPattern.test(item?.month || '')) {
    throw new TypeError('Invalid owned occurrence identity');
  }
  return `${item.source}:${item.sourceId}:${item.month}:${item.dueDate}`;
}

export function normalizeOccurrence(item) {
  const key = occurrenceKey(item);
  if (!states.has(item.status) || !Array.isArray(item.allowedActions) ||
      item.allowedActions.some(action => !['RECORD', 'SKIP', 'OPEN_MONEY'].includes(action))) {
    throw new TypeError('Invalid occurrence state or eligibility');
  }
  if (['UPCOMING', 'RECORDED', 'SKIPPED'].includes(item.status) &&
      item.allowedActions.some(action => action === 'RECORD' || action === 'SKIP')) {
    throw new TypeError('Non-actionable occurrence has a decision action');
  }
  if (item.status === 'OVERDUE' && !item.allowedActions.includes('OPEN_MONEY') &&
      !item.allowedActions.includes('RECORD') && !item.allowedActions.includes('SKIP')) {
    throw new TypeError('Overdue occurrence has no review path');
  }
  return { ...item, key };
}

export function normalizeStory(story) {
  if (!story?.storyId || !story.logicalStoryId || !Number.isInteger(story.revision) ||
      !isDate(story.period?.startDate) || !isDate(story.period?.endDate) ||
      story.period.startDate > story.period.endDate || !story.generatedAt ||
      Number.isNaN(Date.parse(story.generatedAt))) {
    throw new TypeError('Invalid published story reference');
  }
  const evidence = story.evidence;
  if (evidence && (!Array.isArray(evidence.transactions) ||
      evidence.transactions.some(row => !row.transactionId))) {
    throw new TypeError('Invalid story evidence');
  }
  return {
    ...story,
    reference: {
      storyId: story.storyId,
      logicalStoryId: story.logicalStoryId,
      revision: story.revision,
      publishedAt: story.generatedAt,
      period: story.period,
      evidenceIds: evidence?.transactions.map(row => row.transactionId) || []
    }
  };
}

export function composeDay(day, occurrences = [], stories = []) {
  if (!isDate(day?.date)) throw new TypeError('Invalid daily summary date');
  const owned = occurrences.map(normalizeOccurrence);
  if (new Set(owned.map(item => item.key)).size !== owned.length) throw new TypeError('Duplicate occurrence');
  return {
    ...day,
    // Missing recorded measures stay null. A missing composition is never rendered as zero.
    recordedExpenses: day.recordedExpenses ?? null,
    occurrences: owned.filter(item => item.dueDate === day.date),
    stories: stories.map(normalizeStory).filter(story =>
      story.period.startDate <= day.date && story.period.endDate >= day.date)
  };
}

function localMonth(timezone, date = new Date()) {
  const parts = new Intl.DateTimeFormat('en-CA', {timeZone:timezone,year:'numeric',month:'2-digit'}).formatToParts(date);
  return `${parts.find(part=>part.type==='year').value}-${parts.find(part=>part.type==='month').value}`;
}

export function profileDate(instant, timezone) {
  if (!instant || !timezone || Number.isNaN(Date.parse(instant))) return null;
  try {
    const parts = new Intl.DateTimeFormat('en-CA',{timeZone:timezone,year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date(instant));
    return `${parts.find(part=>part.type==='year').value}-${parts.find(part=>part.type==='month').value}-${parts.find(part=>part.type==='day').value}`;
  } catch { return null; }
}

function occurrence(source, sourceId, label, month, dueDate, status, plannedAmount, actualAmount, recordedDate, moneyUrl, today, details={}) {
  return normalizeOccurrence({source,sourceId:String(sourceId),label,month,dueDate,status,plannedAmount,actualAmount,
    recordedDate,allowedActions:status==='DUE' && month===today.slice(0,7)
      ? ['OPEN_MONEY','RECORD','SKIP'] : ['OPEN_MONEY'],moneyUrl,...details});
}

export function mapOwnedOccurrences(month, {loans, commitments, funds} = {}, today) {
  const items=[];
  for(const loan of loans?.loans || []) for(const emi of loan.emiOccurrences || []) {
    if(emi.month!==month || !isDate(emi.dueDate)) continue;
    const status=emi.status==='PAID'?'RECORDED':emi.status==='SKIPPED'?'SKIPPED':emi.status==='DUE' && emi.dueDate<today?'OVERDUE':emi.status==='DUE'?'DUE':'UPCOMING';
    items.push(occurrence('LOAN_EMI',loan.id,loan.loanName,month,emi.dueDate,status,emi.plannedAmount,emi.paidAmount,emi.paidAt,`/money/loans/${loan.id}`,today));
  }
  for(const commitment of commitments?.items || []) for(const row of commitment.currentOccurrences || []) {
    if(row.month!==month || !isDate(row.dueDate)) continue;
    const status=row.status==='COMPLETED'?'RECORDED':row.status==='SKIPPED'?'SKIPPED':row.status==='DUE' && row.dueDate<today?'OVERDUE':row.status==='DUE'?'DUE':'UPCOMING';
    items.push(occurrence('COMMITMENT',commitment.id,commitment.label,month,row.dueDate,status,commitment.planningAmount,row.actualAmount,row.completedAt,`/money/commitments/${commitment.id}`,today,
      {dated:['DAY','WEEK'].includes(commitment.recurrenceUnit)}));
  }
  // The fund list exposes a current occurrence and an explicit next due date. We never
  // infer a historical SIP day or synthesize occurrences from an active plan.
  for(const fund of funds?.mutualFunds || []) {
    const sip=fund.currentSip, due=fund.activeSip?.nextDueDate;
    if(sip?.scheduledMonth!==month || !isDate(due) || due.slice(0,7)!==month) continue;
    const status=sip.status==='CONFIRMED'?'RECORDED':sip.status==='SKIPPED'?'SKIPPED':sip.status==='DUE' && due<today?'OVERDUE':sip.status==='DUE'?'DUE':'UPCOMING';
    items.push(occurrence('MUTUAL_FUND_SIP',fund.id,fund.schemeName,month,due,status,sip.amount,
      sip.status==='CONFIRMED'?sip.amount:null,sip.transactionDate,`/money/funds/${fund.id}`,today));
  }
  return items.sort((a,b)=>a.dueDate.localeCompare(b.dueDate)||a.key.localeCompare(b.key));
}

export function createReadClient({ fetcher = fetch, base = '', ttlMs = 60_000, now = () => Date.now() } = {}) {
  const cache = new Map();
  let owner = null;
  async function request(path, signal) {
    const response = await fetcher(`${base}${path}`, { credentials: 'include', signal });
    if (!response.ok) throw Object.assign(new Error(response.status === 401 ? 'Session expired' : 'Read unavailable'), { status: response.status });
    return response.json();
  }
  function setOwner(ownerKey) {
    if (!ownerKey) throw new TypeError('Owner cache key required');
    if (owner !== ownerKey) { cache.clear(); owner = ownerKey; }
  }
  function clear() { cache.clear(); owner = null; }
  async function cached(scope, month, signal, force = false) {
    if (!owner || !monthPattern.test(month)) throw new TypeError('Owner and valid month required');
    const key = `${owner}:${scope}:${month}`;
    const hit = cache.get(key);
    if (hit && !force && now() - hit.at < ttlMs) return { data: hit.data, freshness: 'cached', loadedAt: hit.at };
    try {
      const data = await load(scope, month, signal);
      if (data.month !== month) throw new TypeError('Wrong response month');
      cache.set(key, { data, at: now() });
      return { data, freshness: 'fresh', loadedAt: now() };
    } catch (error) {
      if (hit && error.status !== 401) return { data: hit.data, freshness: 'stale', loadedAt: hit.at, error };
      throw error;
    }
  }
  async function load(scope, month, signal) {
    const query=`month=${encodeURIComponent(month)}`;
    if(scope==='days') {
      const calendar=await request(`/api/web/expenses/calendar?${query}`,signal);
      return {...calendar,asOf:new Date(now()).toISOString(),days:calendar.days.map(day=>({...day,
        recordedExpenses:day.totalSpend,recordedExpenseCount:day.transactionCount,occurrenceKeys:[]}))};
    }
    if(scope==='projection' || scope==='stories') {
      const result=await request(`/api/web/expenses/monthly?${query}`,signal);
      if(scope==='stories') return {...result,state:'AVAILABLE',stories:result.stories.filter(story=>story.storyType!=='MONTHLY_COMMITMENT')};
      const current=localMonth(result.timezone,new Date(now()));
      return {month,state:month===current?'AVAILABLE':'UNAVAILABLE',asOf:new Date(now()).toISOString(),
        story:month===current?result.stories.find(story=>story.storyType==='MONTHLY_COMMITMENT') || null:null};
    }
    if(scope==='occurrences') {
      const [calendar,loans,commitments,funds]=await Promise.all([
        request(`/api/web/expenses/calendar?${query}`,signal),request('/api/web/loans',signal),
        request('/api/web/recurring-commitments',signal),request('/api/web/mutual-funds',signal)]);
      const today=profileDate(new Date(now()).toISOString(),calendar.timezone);
      return {month,asOf:new Date(now()).toISOString(),coverage:'SOURCE_LIST_OCCURRENCES',
        unavailableSources:['SAVINGS','STOCK_PLAN'],items:mapOwnedOccurrences(month,{loans,commitments,funds},today)};
    }
    if(scope==='history') return {month,available:false,publications:[]};
    throw new TypeError('Unknown read scope');
  }
  return {
    setOwner, clear,
    invalidate(month) { for (const key of cache.keys()) if (!month || key.endsWith(`:${month}`)) cache.delete(key); },
    days: (month, signal, force) => cached('days', month, signal, force),
    projection: (month, signal, force) => cached('projection', month, signal, force),
    occurrences: (month, signal, force) => cached('occurrences', month, signal, force),
    stories: (month, signal, force) => cached('stories', month, signal, force),
    history: (month, signal, force) => cached('history', month, signal, force),
    async activity(date, { cursor = null, limit = 20, signal } = {}) {
      if (!owner || !isDate(date) || !Number.isInteger(limit) || limit < 1 || limit > 50) throw new TypeError('Invalid activity request');
      const params = new URLSearchParams({ date, limit: String(limit) });
      if (cursor) params.set('cursor', cursor);
      if(cursor) {params.delete('cursor');params.set('beforeId',cursor);}
      const page = await request(`/api/web/expenses?month=${encodeURIComponent(date.slice(0,7))}&${params}`, signal);
      if (!Array.isArray(page.items) || page.items.length > limit) throw new TypeError('Invalid activity page');
      return {items:page.items,nextCursor:page.nextBeforeId == null ? null : String(page.nextBeforeId)};
    }
  };
}
