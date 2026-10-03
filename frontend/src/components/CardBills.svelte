<!-- FIN-EPIC-005 / FIN-022: card journeys and settlements belong inside Accounts. -->
<script>
  import CardBillVisual from './CardBillVisual.svelte';
  export let bills=[];
  export let embedded=false;
  export let month;
  export let status='ready';
  export let error='';
  export let currency='INR';
  export let connectionStatus;
  export let onPay=()=>{};
  export let onRetry=()=>{};
  const moneyDecimal=value=>new Intl.NumberFormat('en-IN',{style:'currency',currency,minimumFractionDigits:2,maximumFractionDigits:2}).format(Number(value||0));
  const monthLabel=value=>new Intl.DateTimeFormat('en-IN',{month:'long',year:'numeric',timeZone:'UTC'}).format(new Date(`${value}-01T00:00:00Z`));
  const dateLabel=value=>new Intl.DateTimeFormat('en-IN',{day:'numeric',month:'short',year:'numeric',timeZone:'UTC'}).format(new Date(`${value}T00:00:00Z`));
</script>
  {#if embedded || bills.length || status!=='ready'}
  <section class="home-section" class:embedded aria-label="Credit-card bills"><div class="section-title"><h2>Credit-card bills · {monthLabel(month)}</h2></div>
    <p class="module-reassurance">For purchases already counted in spending. Recording a bill payment adds no new expense.</p>
    {#if status==='error'}<p class="form-error">{error}</p><button class="secondary" on:click={onRetry}>Retry card bills</button>{/if}
    {#if status==='loading'}<p>Loading card bills…</p>{:else if status!=='error'&&!bills.length}<p>No captured bills for this month.</p>{/if}
    {#each bills as bill}
      <article class="card-bill-panel"><header class="card-bill-heading"><div><p class="micro-label">{bill.statementClosed?'BILL PAYMENT':'UPCOMING BILL'}</p><h3>{bill.cardName}</h3></div><span class="card-bill-state">{!bill.statementClosed?'Projected':Number(bill.remaining)>0?'Payment pending':Number(bill.paidAmount)>0?'Captured purchases covered':'No purchases captured'}</span></header>
      <CardBillVisual {bill} {currency}/>
      <div class="card-bill-actions"><strong>{moneyDecimal(bill.remaining)} remaining</strong><button class="primary" disabled={connectionStatus!=='online'||!bill.statementClosed} on:click={()=>onPay(bill)}>Record bill payment</button></div>
      {#if !bill.statementClosed}<small>Projection · statement not closed yet</small>{/if}
      {#if bill.payments?.length}<details class="card-bill-history"><summary>Payment history</summary>{#each bill.payments as payment}<p>{dateLabel(payment.paidAt)} · {moneyDecimal(payment.amount)} paid</p>{/each}</details>{/if}</article>
    {/each}
  </section>

  {/if}
