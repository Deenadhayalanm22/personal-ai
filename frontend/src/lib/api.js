export const API_URL = (import.meta.env.VITE_API_BASE || 'http://localhost:8080').replace(/\/$/, '');
const HEALTH_PATH = import.meta.env.VITE_HEALTH_PATH || '/health';
const HEALTH_TIMEOUT_MS = Number(import.meta.env.VITE_HEALTH_TIMEOUT_MS || 8000);
let unauthorizedNotified = false;

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
    // Several requests can fail together when a session expires. One redirect is
    // enough; suppressing the rest avoids a burst of navigation attempts.
    if (response.status === 401 && authenticated && !unauthorizedNotified) {
      unauthorizedNotified = true;
      window.dispatchEvent(new CustomEvent('app:unauthorized'));
    }
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
export const getRecurringCommitments = () => request('/api/web/recurring-commitments');
export const getRecurringCommitmentHistory = (id) => request(`/api/web/recurring-commitments/${encodeURIComponent(id)}/history`);
export const createRecurringCommitment = (commitment) => request('/api/web/recurring-commitments', { method: 'POST', body: JSON.stringify(commitment) });
export const updateRecurringCommitment = (id, commitment) => request(`/api/web/recurring-commitments/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify(commitment) });
export const deleteRecurringCommitment = (id) => request(`/api/web/recurring-commitments/${encodeURIComponent(id)}`, { method: 'DELETE' });
export const completeRecurringCommitment = (id, month) => request(`/api/web/recurring-commitments/${encodeURIComponent(id)}/occurrences/${encodeURIComponent(month)}/done`, { method: 'POST' });
export const getCommitmentReview = (month) => request(`/api/web/recurring-commitments/review?month=${encodeURIComponent(month)}`);
export const resolveCommitmentReview = (transactionId, commitmentId) => request(`/api/web/recurring-commitments/review/${encodeURIComponent(transactionId)}`, { method: 'POST', body: JSON.stringify({ commitmentId }) });
export const getCreditCards = () => request('/api/web/credit-cards');
export const createCreditCard = (card) => request('/api/web/credit-cards', { method: 'POST', body: JSON.stringify(card) });
export const updateCreditCard = (id, card) => request(`/api/web/credit-cards/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify(card) });

export const getLoans = () => request('/api/web/loans');
export const getLoanHistory = (id) => request(`/api/web/loans/${encodeURIComponent(id)}/history`);
export const createLoan = (loan) => request('/api/web/loans', { method: 'POST', body: JSON.stringify(loan) });
export const updateLoan = (id, changes) => request(`/api/web/loans/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify(changes) });
export const deleteLoan = (id) => request(`/api/web/loans/${encodeURIComponent(id)}`, { method: 'DELETE' });
export const markLoanEmiPaid = (id, month) => request(`/api/web/loans/${encodeURIComponent(id)}/emi-occurrences/${encodeURIComponent(month)}/paid`, { method: 'POST' });
export const getActions = () => request('/api/web/actions');
export const completeAction = (id) => request(`/api/web/actions/${encodeURIComponent(id)}/complete`, { method: 'POST' });

export const searchMutualFunds = (query) => request(`/api/web/mutual-funds/search?q=${encodeURIComponent(query)}`);
export const getMutualFunds = () => request('/api/web/mutual-funds');
export const getMutualFund = (id) => request(`/api/web/mutual-funds/${encodeURIComponent(id)}`);
export const createMutualFund = (fund) => request('/api/web/mutual-funds', { method: 'POST', body: JSON.stringify(fund) });
export const createMutualFundSip = (id, plan) => request(`/api/web/mutual-funds/${encodeURIComponent(id)}/sip`, { method: 'POST', body: JSON.stringify(plan) });
export const deleteMutualFund = (id) => request(`/api/web/mutual-funds/${encodeURIComponent(id)}`, { method: 'DELETE' });
export const createMutualFundLumpSum = (id, investment) => request(`/api/web/mutual-funds/${encodeURIComponent(id)}/lump-sums`, { method: 'POST', body: JSON.stringify(investment) });
export const confirmSipOccurrence = (id, month, investment) => request(`/api/web/mutual-funds/${encodeURIComponent(id)}/sip-occurrences/${encodeURIComponent(month)}/confirm`, { method: 'POST', body: JSON.stringify(investment) });
export const updateSipOccurrence = (id, month, investment) => request(`/api/web/mutual-funds/${encodeURIComponent(id)}/sip-occurrences/${encodeURIComponent(month)}`, { method: 'PATCH', body: JSON.stringify(investment) });
export const updateMutualFundTransaction = (id, transactionId, investment) => request(`/api/web/mutual-funds/${encodeURIComponent(id)}/transactions/${encodeURIComponent(transactionId)}`, { method: 'PATCH', body: JSON.stringify(investment) });
export const searchStocks = (query) => request(`/api/web/stocks/search?q=${encodeURIComponent(query)}`);
export const getStocks = () => request('/api/web/stocks');
export const getStock = (id) => request(`/api/web/stocks/${encodeURIComponent(id)}`);
export const createStock = (stock) => request('/api/web/stocks', { method: 'POST', body: JSON.stringify(stock) });
export const deleteStock = (id) => request(`/api/web/stocks/${encodeURIComponent(id)}`, { method: 'DELETE' });
export const createStockMonthlyPlan = (id, plan) => request(`/api/web/stocks/${encodeURIComponent(id)}/monthly-plan`, { method: 'POST', body: JSON.stringify(plan) });
export const confirmStockMonthlyPlan = (id, month, confirmation) => request(`/api/web/stocks/${encodeURIComponent(id)}/monthly-plan-occurrences/${encodeURIComponent(month)}/confirm`, { method: 'POST', body: JSON.stringify(confirmation) });
export const getIncomeOutlook = () => request('/api/web/income-outlook');
export const saveIncomeProfile = (profile) => request('/api/web/income-outlook/salary', { method: 'PUT', body: JSON.stringify(profile) });

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
