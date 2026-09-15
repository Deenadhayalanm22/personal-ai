export const API_URL = (import.meta.env.VITE_API_BASE || 'http://localhost:8080').replace(/\/$/, '');
const HEALTH_PATH = import.meta.env.VITE_HEALTH_PATH || '/health';
const HEALTH_TIMEOUT_MS = Number(import.meta.env.VITE_HEALTH_TIMEOUT_MS || 8000);

export class ApiError extends Error {
  constructor(message, status, data = null) { super(message); this.name = 'ApiError'; this.status = status; this.data = data; }
}

async function parseResponse(response) {
  if (response.status === 204) return null;
  const type = response.headers.get('content-type') || '';
  if (type.includes('application/json')) return response.json();
  const text = await response.text();
  return text ? { message: text } : null;
}

async function request(path, options = {}, authenticated = true) {
  const response = await fetch(`${API_URL}${path}`, { credentials: 'include', ...options, headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers } });
  const data = await parseResponse(response);
  if (!response.ok) {
    const fallback = response.status === 401 ? 'Your session has expired.' : response.status === 409 ? 'This expense changed after you opened it.' : 'Something went wrong. Please try again.';
    if (response.status === 401 && authenticated) window.dispatchEvent(new CustomEvent('app:unauthorized'));
    throw new ApiError(data?.message || data?.error || fallback, response.status, data);
  }
  return data;
}

export async function exchangeMagicLink(token) {
  if (!token) throw new ApiError('Magic-link token is missing.', 400);
  return request('/api/web/auth/magic-link', { method: 'POST', body: JSON.stringify({ token }) }, false);
}

export const getSession = () => request('/api/web/auth/session', {}, false);
export const getDemoMode = () => request('/api/web/auth/demo-profile');
export const setDemoMode = (enabled) => request('/api/web/auth/demo-profile', { method: 'PUT', body: JSON.stringify({ enabled }) });
// Health is deliberately public: startup uses it before attempting authenticated data refreshes.
export async function getHealth() {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), HEALTH_TIMEOUT_MS);
  try { return await request(HEALTH_PATH, { signal: controller.signal }, false); }
  finally { clearTimeout(timeout); }
}
export const requestLoginLink = (phoneNumber) => request('/api/web/auth/login-link', { method: 'POST', body: JSON.stringify({ phoneNumber }) }, false);
export const logout = () => request('/api/web/auth/logout', { method: 'POST' }, false);

export const getExpenseCalendar = (month) => request(`/api/web/expenses/calendar?month=${encodeURIComponent(month)}`);
export const getRecentExpenses = (month, limit = 10) => request(`/api/web/expenses?month=${encodeURIComponent(month)}&limit=${encodeURIComponent(limit)}`);
export const getExpenseOptions = () => request('/api/web/expenses/options');
export const getMoneyStories = (month) => request(`/api/web/expenses/monthly?month=${encodeURIComponent(month)}`);
export const createMissingDateContext = (date, timezone) => request('/api/web/expenses/calendar/context', { method: 'POST', body: JSON.stringify({ type: 'MISSING_TRANSACTION_DATE', date, timezone }) });
export const getExpensesForDate = (month, date, limit = 50) => request(`/api/web/expenses?month=${encodeURIComponent(month)}&date=${encodeURIComponent(date)}&limit=${encodeURIComponent(limit)}`);
export const updateExpense = (id, changes) => request(`/api/web/expenses/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify(changes) });
export const deleteExpense = (id) => request(`/api/web/expenses/${encodeURIComponent(id)}`, { method: 'DELETE' });

export const getLoans = () => request('/api/web/loans');
export const createLoan = (loan) => request('/api/web/loans', { method: 'POST', body: JSON.stringify(loan) });
export const updateLoan = (id, changes) => request(`/api/web/loans/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify(changes) });
export const getActions = () => request('/api/web/actions');
export const completeAction = (id) => request(`/api/web/actions/${encodeURIComponent(id)}/complete`, { method: 'POST' });

export const searchMutualFunds = (query) => request(`/api/web/mutual-funds/search?q=${encodeURIComponent(query)}`);
export const getMutualFunds = () => request('/api/web/mutual-funds');
export const getMutualFund = (id) => request(`/api/web/mutual-funds/${encodeURIComponent(id)}`);
export const createMutualFund = (fund) => request('/api/web/mutual-funds', { method: 'POST', body: JSON.stringify(fund) });
export const createMutualFundLumpSum = (id, investment) => request(`/api/web/mutual-funds/${encodeURIComponent(id)}/lump-sums`, { method: 'POST', body: JSON.stringify(investment) });
export const confirmSipOccurrence = (id, month, investment) => request(`/api/web/mutual-funds/${encodeURIComponent(id)}/sip-occurrences/${encodeURIComponent(month)}/confirm`, { method: 'POST', body: JSON.stringify(investment) });

let referenceEntityTypesCache = null;
let referenceEntityTypesRequest = null;

export function clearProfileCaches() {
  referenceEntityTypesCache = null;
  referenceEntityTypesRequest = null;
}

export async function getReferenceEntityTypes() {
  if (referenceEntityTypesCache) return referenceEntityTypesCache;
  if (!referenceEntityTypesRequest) {
    referenceEntityTypesRequest = request('/api/web/reference-entity-types')
      .then(data => { referenceEntityTypesCache = data; return data; })
      .catch(cause => { referenceEntityTypesRequest = null; throw cause; });
  }
  return referenceEntityTypesRequest;
}

export const createReferencePreference = (preference) => request('/api/web/reference-preferences', { method: 'POST', body: JSON.stringify(preference) });
export const getReferencePreferences = () => request('/api/web/reference-preferences');
export const mergeReferencePreferences = (merge) => request('/api/web/reference-preferences/merge', { method: 'POST', body: JSON.stringify(merge) });
