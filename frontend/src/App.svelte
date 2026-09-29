<!-- FIN-EPIC-003 and FIN-EPIC-004: docs/jira/personal-expense/FIN-EPIC-003-insights.md and FIN-EPIC-004-portal-and-references.md -->
<script>
  import { onMount } from 'svelte';
  import ExpenseChat from './components/ExpenseChat.svelte';
  import Home from './Home.svelte'; import Auth from './Auth.svelte'; import PrivacyPolicy from './PrivacyPolicy.svelte'; import CachedDashboard from './CachedDashboard.svelte';
  import { ApiError, clearProfileCaches, exchangeMagicLink, getDemoMode, getExpenseCalendar, getHealth, getMoneyStories, getRecentExpenses, setDemoMode } from './lib/api.js';

  const CACHE_KEY = 'money-stories.dashboard-cache.v1';
  const PROFILE_CACHE_KEY = 'money-stories.last-verified-profile.v1';
  const initialPath = location.pathname.replace(/\/$/, '') || '/', isPrivacyPage = initialPath === '/privacy-policy';
  const now = new Date(), currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  // Do not mount Home until the session/profile request has succeeded. Home loads
  // protected data on mount, including the actions queue.
  let view = isPrivacyPage ? 'privacy' : 'initializing', selectedMonth = monthFromUrl();
  let calendarSection = state(), recentSection = state(), storiesSection = state(), connectionStatus = 'checking', cacheUpdatedAt = null;
  let connectionRequest = 0;
  let demoMode = false;
  let canUseDemoMode = false;
  let previewCache = null;
  let previewMoneyModules = null;

  function state(data = null) { return { status: data ? 'ready' : 'loading', data, error: '' }; }
  function monthFromUrl() { const value = new URLSearchParams(location.search).get('month'); return /^\d{4}-\d{2}$/.test(value || '') ? value : currentMonth; }
  function unavailableSection(data = null) { return { status: 'ready', data, error: '' }; }
  function cacheKey() { return `${CACHE_KEY}.${demoMode ? 'demo' : 'real'}`; }
  function readPreview() {
    try {
      const profile = JSON.parse(localStorage.getItem(PROFILE_CACHE_KEY) || 'null');
      if (typeof profile?.demoMode !== 'boolean') return;
      const cache = JSON.parse(localStorage.getItem(`${CACHE_KEY}.${profile.demoMode ? 'demo' : 'real'}`) || 'null');
      if (cache?.version === 1 && cache.month === selectedMonth && cache.sections && (cache.sections.calendar || cache.sections.recent || cache.sections.stories) && !Number.isNaN(Date.parse(cache.updatedAt))) {
        previewCache = cache;
        previewMoneyModules = JSON.parse(localStorage.getItem(`money-stories.money-modules-cache.v1.${profile.demoMode ? 'demo' : 'real'}`) || 'null');
      }
    } catch { /* Storage can be unavailable or contain old data. */ }
  }
  function readCache() {
    try { const cache = JSON.parse(localStorage.getItem(cacheKey()) || 'null'); if (!cache?.sections || cache.month !== selectedMonth) return false; calendarSection = cache.sections.calendar ? state(cache.sections.calendar) : unavailableSection(); recentSection = cache.sections.recent ? state(cache.sections.recent) : unavailableSection(); storiesSection = cache.sections.stories ? state(cache.sections.stories) : unavailableSection(); cacheUpdatedAt = cache.updatedAt || null; return true; } catch { return false; }
  }
  function saveCache() {
    try { localStorage.setItem(cacheKey(), JSON.stringify({ version: 1, month: selectedMonth, updatedAt: new Date().toISOString(), sections: { calendar: calendarSection.data, recent: recentSection.data, stories: storiesSection.data } })); cacheUpdatedAt = new Date().toISOString(); } catch { /* Storage can be unavailable in private browsing. The live app still works. */ }
  }
  function emptyFirstRun() { calendarSection = unavailableSection(); recentSection = unavailableSection(); storiesSection = unavailableSection(); }
  function clearSavedDashboard() { try { localStorage.removeItem(PROFILE_CACHE_KEY); for (const mode of ['real', 'demo']) { localStorage.removeItem(`${CACHE_KEY}.${mode}`); localStorage.removeItem(`money-stories.money-modules-cache.v1.${mode}`); } } catch {} }
  function unauthorized() { clearSavedDashboard(); location.replace(`/portal?next=${encodeURIComponent(location.pathname + location.search)}`); }
  async function loadCalendar() { calendarSection = { ...calendarSection, status: calendarSection.data ? 'refreshing' : 'loading', error: '' }; try { calendarSection = { status: 'ready', data: await getExpenseCalendar(selectedMonth), error: '' }; saveCache(); } catch (cause) { if (!(cause instanceof ApiError && cause.status === 401)) calendarSection = calendarSection.data ? { ...calendarSection, status: 'ready', error: '' } : unavailableSection(); } }
  async function loadRecent() { recentSection = { ...recentSection, status: recentSection.data ? 'refreshing' : 'loading', error: '' }; try { recentSection = { status: 'ready', data: await getRecentExpenses(selectedMonth, 5), error: '' }; saveCache(); } catch (cause) { if (!(cause instanceof ApiError && cause.status === 401)) recentSection = recentSection.data ? { ...recentSection, status: 'ready', error: '' } : unavailableSection(); } }
  async function loadStories() { storiesSection = { ...storiesSection, status: storiesSection.data ? 'refreshing' : 'loading', error: '' }; try { storiesSection = { status: 'ready', data: await getMoneyStories(selectedMonth), error: '' }; saveCache(); } catch (cause) { if (!(cause instanceof ApiError && cause.status === 401)) storiesSection = storiesSection.data ? { ...storiesSection, status: 'ready', error: '' } : unavailableSection(); } }
  async function refreshWhenOnline() {
    const requestId = ++connectionRequest;
    connectionStatus = navigator.onLine ? 'checking' : 'offline';
    if (!navigator.onLine) { if (!calendarSection.data && !recentSection.data && !storiesSection.data) emptyFirstRun(); return; }
    try {
      await getHealth();
      if (requestId !== connectionRequest) return;
      connectionStatus = 'online';
      await Promise.allSettled([loadCalendar(), loadRecent(), loadStories()]);
    } catch {
      if (requestId !== connectionRequest) return;
      connectionStatus = 'offline';
      if (!calendarSection.data && !recentSection.data && !storiesSection.data) emptyFirstRun();
    }
  }
  function changeMonth(month, updateHistory = true) { selectedMonth = month; if (updateHistory) history.pushState({}, '', `/dashboard?month=${encodeURIComponent(month)}`); if (connectionStatus === 'online') { loadCalendar(); loadRecent(); loadStories(); } else { emptyFirstRun(); } }
  async function changeDemoMode(enabled) {
    if (!canUseDemoMode) return;
    const result = await setDemoMode(enabled);
    clearSavedDashboard();
    demoMode = result.demoMode;
    try { localStorage.setItem(PROFILE_CACHE_KEY, JSON.stringify({ demoMode })); } catch {}
    clearProfileCaches();
    emptyFirstRun();
    cacheUpdatedAt = null;
    if (connectionStatus === 'online') await refreshWhenOnline();
  }
  async function initialize() {
    if (isPrivacyPage) return;
    if (initialPath === '/access') { const token = new URLSearchParams(location.search).get('token'); if (!token) { view = 'invalid-link'; return; } view = 'magic-loading'; try { await exchangeMagicLink(token); clearSavedDashboard(); const next = sessionStorage.getItem('portal-next') || '/dashboard'; sessionStorage.removeItem('portal-next'); location.replace(next); } catch (cause) { view = cause instanceof ApiError && cause.status === 401 ? 'invalid-link' : (!navigator.onLine ? 'magic-offline' : 'magic-error'); } return; }
    if (initialPath === '/portal') { const next = new URLSearchParams(location.search).get('next'); if (next?.startsWith('/')) sessionStorage.setItem('portal-next', next); view = 'login'; return; }
    readPreview();
    // Only the inert preview uses the last verified mode; Home waits for the live profile response.
    try { const profile = await getDemoMode(); demoMode = profile.demoMode; canUseDemoMode = profile.canUseDemoMode; try { localStorage.setItem(PROFILE_CACHE_KEY, JSON.stringify({ demoMode })); } catch {} } catch (cause) {
      // request() has already notified the app and started the sign-in redirect.
      // Keeping the preview/loading view here prevents Home from issuing protected calls
      // while the browser changes pages.
      if (cause instanceof ApiError && cause.status === 401) return;
      return;
    }
    view = 'dashboard';
    if (!readCache()) emptyFirstRun(); await refreshWhenOnline();
  }
  onMount(() => { const auth = () => unauthorized(), pop = () => changeMonth(monthFromUrl(), false), online = () => refreshWhenOnline(), offline = () => { connectionRequest += 1; connectionStatus = 'offline'; }; addEventListener('app:unauthorized', auth); addEventListener('popstate', pop); addEventListener('online', online); addEventListener('offline', offline); initialize(); return () => { removeEventListener('app:unauthorized', auth); removeEventListener('popstate', pop); removeEventListener('online', online); removeEventListener('offline', offline); }; });
</script>
{#if view === 'privacy'}<PrivacyPolicy />
{:else if view === 'login'}<Auth />
{:else if view === 'dashboard'}<Home {calendarSection} {recentSection} {storiesSection} {selectedMonth} {connectionStatus} {cacheUpdatedAt} {demoMode} {canUseDemoMode} onDemoModeChange={changeDemoMode} onMonthChange={changeMonth} refreshCalendar={loadCalendar} refreshRecent={loadRecent} refreshStories={loadStories} onRetryConnection={refreshWhenOnline} onLogout={() => { clearSavedDashboard(); location.replace('/portal?message=' + encodeURIComponent('You’ve been signed out.')); }} />
{#key demoMode}<ExpenseChat {selectedMonth} {connectionStatus} />{/key}
{:else if view === 'invalid-link'}<main class="center-page expired" role="alert"><span class="brand-orb">!</span><h1>This sign-in link is invalid, expired, or has already been used.</h1><a class="center-action" href="/portal">Request a new link</a></main>
{:else if view === 'magic-offline' || view === 'magic-error'}<main class="center-page expired" role="alert"><span class="brand-orb">↻</span><h1>{view === 'magic-offline' ? 'You appear to be offline.' : 'We couldn’t sign you in right now.'}</h1><button class="center-action" on:click={initialize}>Try again</button></main>
{:else if previewCache}<CachedDashboard cache={previewCache} moneyModules={previewMoneyModules} month={selectedMonth} onRetry={initialize} />
{:else}<main class="center-page" aria-live="polite"><span class="brand-orb">₹</span><span class="spinner"></span><h1>Connecting to your dashboard…</h1><p>The service may take a few minutes to wake up.</p><button class="center-action" on:click={initialize}>Retry</button></main>{/if}
