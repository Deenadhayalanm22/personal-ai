<script>
  export let card;
  const hidden = new Set(['Loan payment progress', 'This month', 'SIP allocations complete', 'Of monthly salary', 'Salary context']);
  $: components = card?.components || [];
  $: buckets = components.filter(item => item.type === 'MONEY' && !hidden.has(item.label) && Number(item.value) > 0);
  $: total = buckets.reduce((sum, item) => sum + Number(item.value), 0);
  $: isCurrent = card?.cardId === 'commitment';
  $: loanProgress = components.find(item => item.label === 'Loan payment progress');
  $: loanPaid = Number(loanProgress?.displayValue?.split(' paid of ')[0]?.replace(/[^\d.]/g, '') || 0);
  $: sipPaid = Number(components.find(item => item.label === 'SIP allocations complete')?.value || 0);
  $: salary = components.find(item => item.label === 'Of monthly salary' || item.label === 'Salary context');
  const clamp = value => Math.min(100, Math.max(0, value));
  const completed = (item, loanPaid, sipPaid) => item.label === 'Debt repayments' ? loanPaid : item.label === 'Planned investing' ? sipPaid : 0;
</script>

{#if buckets.length}
  <div class="commitment-summary">{isCurrent ? 'This month' : 'Next month'} · {isCurrent ? 'completion where tracked' : 'share of monthly plan'}</div>
  <div class="commitment-progress-list" aria-label={isCurrent ? 'Current month commitments' : 'Next month commitments'}>
    {#each buckets as item}
      {@const done = Math.min(Number(item.value), completed(item, loanPaid, sipPaid))}
      {@const share = total ? clamp(Number(item.value) / total * 100) : 0}
      {@const hasCompletion = isCurrent && (item.label === 'Debt repayments' || item.label === 'Planned investing')}
      {@const percent = hasCompletion ? clamp(done / Number(item.value) * 100) : share}
      <div class:complete={hasCompletion && percent >= 100} class:started={hasCompletion && percent > 0 && percent < 100} class="commitment-progress-row">
        <div class="commitment-progress-heading"><span>{item.label}</span><strong>{item.displayValue}</strong></div>
        <div class="commitment-progress-track" role="progressbar" aria-label={`${item.label}: ${hasCompletion ? 'completed' : 'share of monthly plan'}`} aria-valuemin="0" aria-valuemax="100" aria-valuenow={Math.round(percent)}><i style={`width:${percent}%`}></i></div>
        <small>{hasCompletion ? `${Math.round(percent)}% completed` : `${Math.round(share)}% of monthly plan`}</small>
      </div>
    {/each}
  </div>
  {#if salary}<p class="commitment-salary">{salary.label}: <strong>{salary.displayValue}</strong></p>{/if}
{/if}
