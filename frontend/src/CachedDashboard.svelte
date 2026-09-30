<!-- FIN-EPIC-004: docs/jira/personal-expense/FIN-EPIC-004-portal-and-references.md -->
<script>
  export let cache;
  export let month;
  export let moneyModules = null;
  export let onRetry;
  const money = (value, currency) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: currency || 'INR', maximumFractionDigits: 0 }).format(Number(value || 0));
  $: calendar = cache.sections.calendar;
  $: recent = cache.sections.recent;
  $: commitment = cache.sections.commitment?.commitment;
  $: currency = calendar?.currency || 'INR';
  $: monthName = new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' }).format(new Date(`${month}-01T12:00:00`));
</script>

<main class="app-shell cached-dashboard" aria-label="Last saved dashboard">
  <section class="offline-notice" role="status"><span>◌</span><div><strong>Showing your last saved view</strong><p>Connecting to the service. Your information will refresh when it wakes up.</p></div><button on:click={onRetry}>Retry</button></section>
  <section class="app-heading"><div><p class="micro-label">{monthName.toUpperCase()}</p><h1>Your month</h1><p>Saved on {new Date(cache.updatedAt).toLocaleString()}</p></div></section>
  {#if calendar}<div class="spending-hero"><span>MONTHLY SPENDING</span><strong>{money(calendar.totalSpend, currency)} <small>spent</small></strong><b>{calendar.transactionCount || 0} transactions</b></div>{/if}
  {#if commitment}<section class="home-section"><div class="section-title"><div><p class="micro-label">MONTHLY PLAN</p><h2>Your commitments</h2></div></div><div class="home-story"><span class="story-source">{'Monthly commitment'}</span><strong>{commitment.cardFace?.heading || 'Monthly commitment'}</strong><b>{commitment.cardFace?.displayValue || ''}</b></div></section>{/if}
  {#if recent}<section class="home-section"><div class="section-title"><div><p class="micro-label">RECENT ACTIVITY</p><h2>Your latest expenses</h2></div></div><div class="compact-list">{#each (recent.items || recent.expenses || []).slice(0, 5) as item}<div class="row-main"><div><strong>{item.merchant || item.merchantName || item.description || 'Expense'}</strong><span>{item.category?.name || item.category || 'Uncategorised'}</span></div><b>− {money(item.amount, currency)}</b></div>{:else}<p class="empty-copy">No expenses recorded yet.</p>{/each}</div></section>{/if}
  {#if moneyModules && (moneyModules.loans?.length || moneyModules.mutualFunds?.length || moneyModules.stocks?.length)}<section class="home-section"><div class="section-title"><div><p class="micro-label">YOUR MONEY</p><h2>Saved plans and investments</h2></div></div><div class="compact-list">{#each [...(moneyModules.loans || []).map(item => ({ name: item.loanName, amount: item.monthlyEmiAmount, type: 'Loan EMI' })), ...(moneyModules.mutualFunds || []).map(item => ({ name: item.schemeName || item.name, amount: item.invested, type: 'Mutual fund invested' })), ...(moneyModules.stocks || []).map(item => ({ name: item.symbol || item.name, amount: item.invested, type: 'Stock invested' }))] as item}<div class="row-main"><div><strong>{item.name}</strong><span>{item.type}</span></div><b>{money(item.amount, currency)}</b></div>{/each}</div></section>{/if}
</main>
