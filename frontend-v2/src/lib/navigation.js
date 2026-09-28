// FIN-EPIC-007: bounded sample navigation. Live month availability comes with stage 5.
export const sampleMonths = ['2026-08', '2026-09', '2026-10'];
export const sampleMonth = '2026-09';

export function validDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) return false;
  const parsed = new Date(`${value}T12:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

export function readLocation(search) {
  const query = new URLSearchParams(search);
  const requestedDay = query.get('day');
  const day = validDate(requestedDay) && sampleMonths.includes(requestedDay.slice(0, 7)) ? requestedDay : null;
  const requestedMonth = query.get('month');
  const month = day?.slice(0, 7) || (sampleMonths.includes(requestedMonth) ? requestedMonth : sampleMonth);
  const view = query.get('view') === 'money' ? 'money' : 'journey';
  const detail = view === 'journey' ? query.get('detail') : null;
  return { month, day, view, detail };
}

export function journeyUrl({ month = sampleMonth, day = null, view = 'journey', detail = null } = {}) {
  const query = new URLSearchParams();
  if (month !== sampleMonth) query.set('month', month);
  if (day) query.set('day', day);
  if (view === 'money') query.set('view', 'money');
  if (detail) query.set('detail', detail);
  return `/${query.size ? `?${query}` : ''}`;
}

export function monthName(month) {
  const date = new Date(`${month}-01T12:00:00Z`);
  return new Intl.DateTimeFormat('en-IN', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(date);
}
