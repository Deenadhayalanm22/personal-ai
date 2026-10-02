<script>
  export let card;
  const hidden = new Set(['Loan payment progress', 'This month', 'SIP allocations complete', 'Recurring commitments complete', 'Card bills settled', 'Of monthly salary', 'Salary context', 'Salary after planned commitments']);
  const colors = ['#507d68', '#678a9c', '#b39a53', '#ae7963', '#8577a1', '#71937f'];
  const clamp = value => Math.min(100, Math.max(0, value));
  $: components = card?.components || [];
  $: buckets = components.filter(item => item.type === 'MONEY' && !hidden.has(item.label) && Number(item.value) > 0);
  $: total = buckets.reduce((sum, item) => sum + Number(item.value), 0);
  $: isCurrent = card?.cardId === 'commitment';
  $: loanProgress = components.find(item => item.label === 'Loan payment progress');
  $: loanPaid = Number(loanProgress?.displayValue?.split(' paid of ')[0]?.replace(/[^\d.]/g, '') || 0);
  $: sipPaid = Number(components.find(item => item.label === 'SIP allocations complete')?.value || 0);
  $: cardPaid = Number(components.find(item => item.label === 'Card bills settled')?.value || 0);
  $: recurringPaid = Number(components.find(item => item.label === 'Recurring commitments complete')?.value || 0);
  $: salary = components.find(item => item.label === 'Of monthly salary' || item.label === 'Salary context');
  $: salaryPercent = salary?.label === 'Of monthly salary' ? Number(salary.value) : null;
  $: salaryDifference = components.find(item => item.label === 'Salary after planned commitments');
  $: remaining = salaryPercent === null ? null : Math.max(0, 100 - salaryPercent);
  $: shortage = salaryPercent === null ? null : Math.max(0, salaryPercent - 100);
  $: estimatedDifference = salaryPercent > 0 ? total * (100 / salaryPercent - 1) : null;
  $: estimateCurrency = new Intl.NumberFormat('en-IN', { style: 'currency', currency: buckets[0]?.currency || 'INR', maximumFractionDigits: 0 });
  const completed = item => item.label === 'Debt repayments' ? loanPaid : item.label === 'Planned investing' ? sipPaid : item.label === 'Essential living' ? recurringPaid : item.label === 'Credit-card bills' ? cardPaid : 0;
</script>

{#if buckets.length}
  {#if total > 0}
    <div class="salary-allocation">
      <div class="salary-allocation-heading"><strong>{salaryPercent === null ? "Monthly plan breakdown" : "Monthly salary estimate"}</strong><span>100%</span></div>
      <div class="salary-allocation-track" role="img" aria-label={salaryPercent === null ? "Planned commitment breakdown totaling 100%" : `Planned commitments use ${salaryPercent}% of monthly salary estimate`}>
        {#each buckets as item, index}
          <i style={`width:${Number(item.value) / total * (salaryPercent === null ? 100 : salaryPercent / Math.max(100, salaryPercent) * 100)}%;background:${colors[index % colors.length]}`} title={`${item.label}: ${Math.round(Number(item.value) / total * (salaryPercent === null ? 100 : salaryPercent))}% of ${salaryPercent === null ? "plan" : "salary"}`}></i>
        {/each}
        {#if remaining !== null && remaining > 0}<i class="salary-unallocated" style={`width:${remaining}%`} title={`${remaining.toFixed(1)}% unallocated`}></i>{/if}
      </div>
      <div class="salary-allocation-legend">
        {#each buckets as item, index}
          <span><i style={`background:${colors[index % colors.length]}`}></i>{item.label} {Math.round(Number(item.value) / total * (salaryPercent === null ? 100 : salaryPercent))}%</span>
        {/each}
      </div>
      {#if salaryPercent !== null}<p>
        {#if salaryDifference && Number(salaryDifference.value) < 0}<strong class="salary-shortage">Short by {salaryDifference.displayValue} ({shortage.toFixed(1)}% of salary)</strong>
        {:else if salaryDifference}<strong>{salaryDifference.displayValue} left after planned commitments</strong>
        {:else if estimatedDifference !== null && estimatedDifference < 0}<strong class="salary-shortage">Short by about {estimateCurrency.format(Math.abs(estimatedDifference))} ({shortage.toFixed(1)}% of salary)</strong>
        {:else if estimatedDifference !== null}<strong>About {estimateCurrency.format(estimatedDifference)} left after planned commitments</strong>{/if}
        <span>Salary is an estimate, not an account balance.</span>
      </p>{/if}
    </div>
  {/if}
  <div class="commitment-summary">{isCurrent ? 'This month' : 'Next month'} · commitment progress</div>
  <div class="commitment-progress-list" aria-label={isCurrent ? 'Current month commitments' : 'Next month commitments'}>
    {#each buckets as item}
      {@const hasCompletion = isCurrent && (item.label === 'Debt repayments' || item.label === 'Planned investing' || item.label === 'Essential living' || item.label === 'Credit-card bills')}
      {@const percent = hasCompletion ? clamp(completed(item) / Number(item.value) * 100) : 100}
      <div class:complete={hasCompletion && percent >= 100} class:started={hasCompletion && percent > 0 && percent < 100} class:planned={!hasCompletion} class="commitment-progress-row">
        <div class="commitment-progress-heading"><span>{item.label}</span><strong>{item.displayValue}</strong></div>
        <div class="commitment-progress-track" role="progressbar" aria-label={`${item.label}: ${hasCompletion ? 'completed' : 'planned amount'}`} aria-valuemin="0" aria-valuemax="100" aria-valuenow={Math.round(percent)}><i style={`width:${percent}%`}></i></div>
        {#if item.label === 'Credit-card bills'}<small>Purchases already counted in spending · record bill payment on Home</small>{/if}
        <small>{hasCompletion ? `${Math.round(percent)}% completed` : item.label === 'Credit-card bills' ? '100% planned · see recorded payments on Home' : '100% planned · payment not tracked'}</small>
      </div>
    {/each}
  </div>
  {#if salary && salaryPercent === null}<p class="commitment-salary">{salary.label}: <strong>{salary.displayValue}</strong></p>{/if}
{/if}
