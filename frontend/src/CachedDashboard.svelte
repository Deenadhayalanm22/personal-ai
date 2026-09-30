<!-- FIN-EPIC-004: docs/jira/personal-expense/FIN-EPIC-004-portal-and-references.md -->
<script>
  export let cache;
  export let month;
  export let moneyModules = null;
  export let onRetry;
  const money = (value, currency) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: currency || 'INR', maximumFractionDigits: 0 }).format(Number(value || 0));
  $: calendar = cache.sections.calendar;
  $: recent = cache.sections.recent;
  $: activity = cache.sections.activity;
  $: commitment = cache.sections.commitment?.commitment;
  $: currency = calendar?.currency || 'INR';
  $: monthName = new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' }).format(new Date(`${month}-01T12:00:00`));
</script>

<main class="app-shell cached-dashboard" aria-label="Last saved dashboard">
  <section class="offline-notice" role="status"><span>◌</span><div><strong>Showing your last saved view</strong><p>Connecting to the service. Your information will refresh when it wakes up.</p></div><button on:click={onRetry}>Retry</button></section>
  <section class="app-heading"><div><p class="micro-label">{monthName.toUpperCase()}</p><h1>Your month</h1><p>Saved on {new Date(cache.updatedAt).toLocaleString()}</p></div></section>
  <section class="monthly-overview" aria-label="This month"><p class="micro-label">THIS MONTH</p><h2>Spending & commitments</h2>
    {#if calendar}<div class="overview-row"><span><strong>Recorded expenses</strong><small>{calendar.transactionCount || 0} transactions</small></span><b>{money(calendar.totalSpend, currency)}</b></div>{/if}
    {#if commitment}<div class="overview-row"><span><strong>Planned commitments</strong><small>Scheduled for this month</small></span><b>{commitment.cardFace?.displayValue || ''}</b></div>{/if}
    {#if commitment}<p class="overview-note">Planned commitments are upcoming amounts, not money already spent.</p>{/if}
  </section>
  {#if activity || recent}<section class="home-section"><div class="section-title"><div><p class="micro-label">RECENT ACTIVITY</p><h2>Everything recorded</h2></div></div><div class="compact-list">{#each (activity?.items || (recent?.items || recent?.expenses || []).map(item => ({ label: item.merchant || item.merchantName || 'Expense', description: 'Recorded expense', amount: item.amount }))).slice(0, 5) as item}<div class="row-main"><div><strong>{item.label}</strong><span>{item.description}</span></div><b>{money(item.amount, currency)}</b></div>{:else}<p class="empty-copy">No activity recorded this month yet.</p>{/each}</div></section>{/if}
  {#if moneyModules && (moneyModules.loans?.length || moneyModules.mutualFunds?.length || moneyModules.stocks?.length)}<section class="home-section"><div class="section-title"><div><p class="micro-label">YOUR MONEY</p><h2>Saved plans and investments</h2></div></div><div class="compact-list">{#each [...(moneyModules.loans || []).map(item => ({ name: item.loanName, amount: item.monthlyEmiAmount, type: 'Loan EMI' })), ...(moneyModules.mutualFunds || []).map(item => ({ name: item.schemeName || item.name, amount: item.invested, type: 'Mutual fund invested' })), ...(moneyModules.stocks || []).map(item => ({ name: item.symbol || item.name, amount: item.invested, type: 'Stock invested' }))] as item}<div class="row-main"><div><strong>{item.name}</strong><span>{item.type}</span></div><b>{money(item.amount, currency)}</b></div>{/each}</div></section>{/if}
</main>
