import test from 'node:test';
import assert from 'node:assert/strict';
import { liveUrl, readLiveLocation } from './live-navigation.js';

test('live URLs preserve month, date and view without accepting invalid dates',()=>{
  const url=liveUrl({month:'2026-09',day:'2026-09-19'});
  assert.deepEqual(readLiveLocation(new URL(url,'http://example.test').search,'2026-09'),
    {month:'2026-09',day:'2026-09-19',view:'journey'});
  assert.deepEqual(readLiveLocation('?month=2026-02&day=2026-02-30&view=unknown','2026-09'),
    {month:'2026-02',day:null,view:'journey'});
  assert.equal(readLiveLocation('?month=garbage&view=money','2026-09').month,'2026-09');
});
