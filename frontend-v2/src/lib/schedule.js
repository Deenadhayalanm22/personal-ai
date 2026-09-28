// FIN-EPIC-007: fixed demo clock and in-memory occurrences; not production financial logic.
export const demoToday = '2026-09-21';
export const scheduled = [
  { id: 'bike-emi', date: '2026-09-19', type: 'loan', name: 'Bike loan EMI', amount: 3500, note: 'Overdue since 19 September', status: 'due' },
  { id: 'internet', date: demoToday, type: 'commitment', name: 'Internet recharge', amount: 799, note: 'Monthly commitment', status: 'due' },
  { id: 'index-sip', date: demoToday, type: 'investment', name: 'Index fund SIP', amount: 2000, note: 'Record the amount and units received', status: 'due' },
  { id: 'insurance-saving', date: demoToday, type: 'savings', name: 'Car insurance savings', amount: 1000, note: 'Money set aside, not an insurance payment', status: 'due' },
  { id: 'electricity', date: '2026-09-22', type: 'commitment', name: 'Electricity bill', amount: 650, note: 'Upcoming · no payment recorded', status: 'upcoming' }
];
export const basePlan = 25000; // Fixture: recorded loan 16,000 + investing 4,000 + September saving 5,000.
export const sumMoney = values => values.reduce((sum, amount) => sum + Math.round(amount * 100), 0) / 100;
export function decideOccurrence(items, id, outcome) {
  const item = items.find(row => row.id === id);
  if (!item || item.status !== 'due' || item.date > demoToday || !['recorded', 'skipped'].includes(outcome.status)) return items;
  const validMoney = n => Number.isFinite(n) && n > 0 && n <= 10000000 && Math.abs(n * 100 - Math.round(n * 100)) < 0.000001;
  if (outcome.status === 'recorded' && (!validMoney(outcome.actualAmount) || !outcome.recordedDate || outcome.recordedDate < item.date || outcome.recordedDate > demoToday)) return items;
  if (outcome.status === 'recorded' && item.type === 'investment' && !(Number.isFinite(outcome.units) && outcome.units > 0 && outcome.units <= 10000000)) return items;
  if (item.type === 'loan' && outcome.status === 'recorded' && (outcome.actualAmount !== item.amount || outcome.recordedDate !== demoToday)) return items;
  if (item.type === 'savings' && outcome.status === 'recorded' && outcome.recordedDate !== demoToday) return items;
  if (outcome.penalty != null && !validMoney(outcome.penalty)) return items;
  return items.map(row => row.id === id ? { ...row, ...outcome, decidedDate: demoToday } : row);
}
export function projectedCommitments(items) {
  return sumMoney([basePlan, ...items.filter(item => item.status !== 'skipped' && !(item.type === 'savings' && item.status === 'recorded')).map(item => item.amount)]);
}
