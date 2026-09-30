import test from 'node:test';
import assert from 'node:assert/strict';
import { composeDay, createReadClient, mapOwnedOccurrences, normalizeOccurrence, profileDate } from './live-read.js';

const occurrence = { source:'LOAN_EMI', sourceId:'17', month:'2026-09', dueDate:'2026-09-19', status:'OVERDUE', allowedActions:['OPEN_MONEY'] };


test('occurrence identity and server eligibility remain intact across views', () => {
  assert.equal(normalizeOccurrence(occurrence).key, 'LOAN_EMI:17:2026-09:2026-09-19');
  assert.throws(() => normalizeOccurrence({...occurrence,status:'RECORDED',allowedActions:['RECORD']}));
  assert.throws(() => composeDay({date:'2026-09-19'}, [occurrence, occurrence]));
});

test('daily composition keeps missing spending distinct from zero', () => {
  const result = composeDay({date:'2026-09-19'}, [occurrence]);
  assert.equal(result.recordedExpenses, null);
  assert.equal(result.occurrences.length, 1);
});

test('month reads are cached per owner, stale on transient failure, and clear on owner change', async () => {
  let calls = 0, clock = 100;
  const client = createReadClient({now:()=>clock,ttlMs:10,fetcher:async () => {
    calls++;
    if (calls === 2) throw new Error('offline');
    return {ok:true,json:async()=>({month:'2026-09',days:[]})};
  }});
  client.setOwner('profile-a');
  assert.equal((await client.days('2026-09')).freshness,'fresh');
  assert.equal((await client.days('2026-09')).freshness,'cached');
  assert.equal(calls,1);
  clock = 111;
  assert.equal((await client.days('2026-09')).freshness,'stale');
  client.setOwner('profile-b');
  assert.equal((await client.days('2026-09')).freshness,'fresh');
  assert.equal(calls,3);
});

test('activity pagination is bounded and never fetches a scroll tick', async () => {
  const urls=[];
  const client=createReadClient({fetcher:async url=>{urls.push(url);return {ok:true,json:async()=>({items:[],nextCursor:null})};}});
  client.setOwner('a');
  await client.activity('2026-09-19',{limit:20,cursor:'next'});
  assert.match(urls[0],/limit=20/);
  assert.match(urls[0],/beforeId=next/);
  await assert.rejects(client.activity('2026-09-19',{limit:51}));
});

test('existing source lists expose only owned occurrence dates, without fabricating schedules', () => {
  const items=mapOwnedOccurrences('2026-09',{loans:{loans:[{id:4,loanName:'Loan',emiOccurrences:[{month:'2026-09',dueDate:'2026-09-19',status:'DUE',plannedAmount:8000}]}]},
    commitments:{items:[]},funds:{mutualFunds:[{id:5,schemeName:'Fund',currentSip:{scheduledMonth:'2026-09',status:'DUE',amount:2000},activeSip:{nextDueDate:'2026-10-01'}}]}},'2026-09-21');
  assert.equal(items.length,1);
  assert.equal(items[0].status,'OVERDUE');
  assert.equal(items[0].key,'LOAN_EMI:4:2026-09:2026-09-19');
  assert.deepEqual(items[0].allowedActions,['OPEN_MONEY']);
  const due=mapOwnedOccurrences('2026-09',{loans:{loans:[{id:4,loanName:'Loan',emiOccurrences:[{month:'2026-09',dueDate:'2026-09-19',status:'DUE',plannedAmount:8000}]}]}},'2026-09-19');
  assert.deepEqual(due[0].allowedActions,['OPEN_MONEY','RECORD','SKIP']);
});

test('expense effective date is read in the profile timezone', () => {
  assert.equal(profileDate('2026-09-18T18:30:00Z','Asia/Kolkata'),'2026-09-19');
  assert.equal(profileDate('2026-09-18T18:30:00Z',null),null);
});
