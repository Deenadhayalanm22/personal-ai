<script>
  // FIN-EPIC-007: inline sample actions; production must reuse domain commands and server eligibility.
  import { demoToday } from '../lib/schedule.js';
  import { money, dayLabel } from '../lib/journey.js';
  export let item;
  export let activeAction;
  export let onStart;
  export let onDecide;
  export let onDetails;
  let mode = '';
  let amount = item.amount;
  let units;
  let penalty;
  let recordedDate = demoToday;
  let error = '';
  $: editing = activeAction === item.id && mode !== '' && item.status === 'due';
  $: verb = item.type === 'investment' ? 'Record investment' : item.type === 'savings' ? 'Record savings' : 'Record payment';
  $: statusLabel = item.status === 'due' ? (item.date < demoToday ? 'Overdue' : 'Due today') : item.status === 'upcoming' ? 'Upcoming' : item.status === 'skipped' ? 'Skipped' : 'Recorded';
  function start(action) { mode = action; amount = item.amount; units = undefined; penalty = undefined; recordedDate = demoToday; error = ''; onStart(item.id); }
  function submit(event) {
    event.preventDefault();
    const result = onDecide(item.id, mode === 'skip'
      ? { status: 'skipped', penalty: item.type === 'loan' && penalty ? Number(penalty) : null }
      : { status: 'recorded', actualAmount: item.type === 'loan' ? item.amount : Number(amount), recordedDate, units: item.type === 'investment' ? Number(units) : null });
    if (result) { mode = ''; onStart(null); } else error = 'Check the amount and date. This item may already have an outcome.';
  }
</script>
<article class="scheduled-item" class:needs-action={item.status === 'due'} data-testid={`task-${item.id}`} aria-label={item.name}>
  <div class="schedule-top"><div><p class="item-status" class:overdue={item.status==='due' && item.date<demoToday} class:done={item.status==='recorded'}>{statusLabel} <span>· {item.type === 'savings' ? 'Set aside' : item.type === 'investment' ? 'Investing' : item.type === 'loan' ? 'Loan' : 'Commitment'}</span></p><h4>{item.name}</h4></div><div class="task-value"><strong>{money(item.amount)}</strong><small>planned</small></div></div>
  {#if item.status === 'recorded'}
    <p class="outcome">{money(item.actualAmount)} {item.type==='savings'?'set aside':'recorded'} · {dayLabel(item.recordedDate)}{#if item.units} · {item.units} units{/if}</p>
  {:else if item.status === 'skipped'}
    <p class="outcome">Skipped on {dayLabel(item.decidedDate)}.{item.type==='loan'?' Sample schedule extends by one month.':item.type==='savings'?' Savings are unchanged; no amount was set aside.':' Only this occurrence is skipped.'}{#if item.penalty} Penalty recorded: {money(item.penalty)}.{/if}</p>
  {:else}<p class="task-note">{item.note}</p>{/if}
  {#if item.status === 'due' && !editing}<div class="task-actions"><button onclick={()=>start('record')}>{verb}</button><button class="quiet-button" onclick={()=>start('skip')}>Skip</button></div>{/if}
  {#if editing}
    <form class="inline-form" onsubmit={submit} aria-label={`${mode==='skip'?'Skip':'Record'} ${item.name}`}>
      {#if mode === 'skip'}
        <p>Skip this occurrence? {item.type==='loan'?'The demo will extend the remaining schedule by one month.':item.type==='savings'?'No savings are recorded; the plan is not automatically redistributed.':'The regular plan stays in place.'}</p>
        {#if item.type==='loan'}<label>Bank penalty, if any (₹)<input type="number" bind:value={penalty} min="0.01" max="10000000" step="0.01"/></label>{/if}
      {:else}
        {#if item.type==='loan'}<p>Confirm {money(item.amount)} was paid on {dayLabel(demoToday)}. This records an EMI; it does not transfer money.</p>
        {:else}<label>{item.type==='savings'?'Amount set aside (₹)':'Actual amount (₹)'}<input type="number" bind:value={amount} min="0.01" max="10000000" step="0.01" required/></label>{/if}
        {#if item.type==='investment'}<label>Units received<input type="number" bind:value={units} min="0.000001" max="10000000" step="0.000001" required/></label>{/if}
        {#if item.type==='commitment' || item.type==='investment'}<label>Recorded payment date<input type="date" bind:value={recordedDate} min={item.date} max={demoToday} required/></label>{/if}
        {#if item.type==='savings'}<p>For {dayLabel(demoToday)}. This increases recorded savings; it does not pay the insurance bill.</p>{/if}
      {/if}
      <p class="local-note">Demo only · temporary change · no real payment or account update</p>
      {#if error}<p role="alert" class="capture-error">{error}</p>{/if}
      <div class="task-actions"><button type="submit">{mode==='skip'?'Confirm sample skip':'Confirm sample record'}</button><button class="quiet-button" type="button" onclick={()=>{mode='';onStart(null);}}>Cancel</button></div>
    </form>
  {/if}
  {#if !editing}<button class="item-details" onclick={()=>onDetails(item)}>View details <span aria-hidden="true">↗</span></button>{/if}
</article>
