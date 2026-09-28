<script>
  // FIN-EPIC-007: source-specific commands reuse the existing owned Money APIs.
  import { onMount } from 'svelte';
  import { money } from './lib/journey.js';
  import TransactionsPanel from './TransactionsPanel.svelte';
  export let api;
  export let onChanged;
  export let month;
  export let timezone;

  let data={};
  let errors={};
  let loading=true;
  let action=null;
  let submitting=false;
  let amount='';
  let date=new Date().toLocaleDateString('en-CA');
  let units='';
  let price='';
  let penalty='';
  let nextDate='';
  let effectiveMonth='';
  let remainingMonths='';
  let detail=null;
  let detailError='';
  let notice='';
  const endpoints={commitments:'/recurring-commitments',savings:'/recurring-commitments/savings',loans:'/loans',funds:'/mutual-funds',stocks:'/stocks',cards:'/credit-cards'};
  async function load() {
    loading=true;
    const names=Object.keys(endpoints);
    const results=await Promise.allSettled(names.map(name=>api(endpoints[name])));
    data=Object.fromEntries(names.map((name,index)=>[name,results[index].status==='fulfilled'?results[index].value:null]));
    errors=Object.fromEntries(names.filter((_,index)=>results[index].status==='rejected').map(name=>[name,'Unavailable. Retry to load.']));
    loading=false;
  }
  onMount(load);
  function start(type,id,mode,planned,dueDate=null,dated=false) {action={type,id,mode,dueDate,dated};amount=planned==null?'':String(planned);date=new Date().toLocaleDateString('en-CA');nextDate='';units='';price='';penalty='';notice='';}
  async function openDetail(type,id) {
    detail={type,id,loading:true,data:null};detailError='';
    const path=type==='loan'?`/loans/${id}/history`:type==='commitment'?`/recurring-commitments/${id}/history`:type==='fund'?`/mutual-funds/${id}`:`/stocks/${id}`;
    try {detail={type,id,loading:false,data:await api(path)};} catch(cause){detail={type,id,loading:false,data:null};detailError=cause.message;}
  }
  function startDetail(mode,item=null) {
    if(!detail)return;
    const {type,id}=detail;
    start(type,id,mode,item?.amount || item?.plannedAmount || '');
    if(item?.id)action={...action,transactionId:item.id};
    if(item?.transactionDate)date=item.transactionDate;
    if(item?.units)units=String(item.units);
    if(mode==='restructure'){effectiveMonth='';remainingMonths='';}
    detail=null;
  }
  function positive(value) {return Number.isFinite(Number(value)) && Number(value)>0;}
  async function submit() {
    if(!action || submitting) return;
    const {type,id,mode}=action;
    if(['record','preclose','lump-sum','purchase','correct-investment','restructure'].includes(mode) && !positive(amount)){notice='Enter an amount greater than zero.';return;}
    if(['record','lump-sum','purchase','correct-investment'].includes(mode) && ['fund','stock'].includes(type) && !positive(units)){notice='Enter units greater than zero.';return;}
    if(mode==='record' && type==='stock' && !positive(price)){notice='Enter an execution price greater than zero.';return;}
    if(mode==='restructure' && (!/^\d{4}-(0[1-9]|1[0-2])$/.test(effectiveMonth) || !Number.isInteger(Number(remainingMonths)) || Number(remainingMonths)<=0)) {notice='Enter a future effective month and positive remaining tenure.';return;}
    if(type==='loan' && mode==='skip' && penalty && !positive(penalty)){notice='The optional bank penalty must be positive.';return;}
    if(type==='commitment' && mode==='record' && (!/^\d{4}-\d{2}-\d{2}$/.test(nextDate) || nextDate <= (action.dueDate || date))) {notice='Choose the next expected date after this occurrence.';return;}
    submitting=true;notice='';
    try {
      const encoded=encodeURIComponent(id), selected=encodeURIComponent(month);
      if(type==='loan' && ['record','skip'].includes(mode)) await api(`/loans/${encoded}/emi-occurrences/${selected}/${mode==='record'?'paid':'skipped'}`,
        {method:'POST',...(mode==='skip'?{body:JSON.stringify({bankPenaltyAmount:penalty?Number(penalty):null})}:{})});
      if(type==='commitment' && ['record','skip'].includes(mode)) await api(mode==='record'?`/recurring-commitments/${encoded}/occurrences/complete`:`/recurring-commitments/${encoded}/occurrences/${encodeURIComponent(action.dated?action.dueDate:month)}/skip`,
        {method:'POST',...(mode==='record'?{body:JSON.stringify({actualAmount:Number(amount),completedAt:date,nextExpectedDate:nextDate,occurrenceDate:action.dated?action.dueDate:null})}:{})});
      if(type==='savings' && ['record','skip'].includes(mode)) await api(`/recurring-commitments/${encoded}/savings/${mode==='record'?'record':'skip'}`,
        {method:'POST',body:JSON.stringify({month,amount:mode==='record'?Number(amount):null})});
      if(type==='fund' && ['record','skip'].includes(mode)) await api(`/mutual-funds/${encoded}/sip-occurrences/${selected}/${mode==='record'?'confirm':'skip'}`,
        {method:'POST',...(mode==='record'?{body:JSON.stringify({amount:Number(amount),transactionDate:date,units:Number(units),calculationSource:'USER_ENTERED'})}:{})});
      if(type==='stock' && ['record','skip'].includes(mode)) await api(`/stocks/${encoded}/monthly-plan-occurrences/${selected}/${mode==='record'?'confirm':'skip'}`,
        {method:'POST',...(mode==='record'?{body:JSON.stringify({amount:Number(amount),transactionDate:date,executionPrice:Number(price),units:Number(units)})}:{})});
      if(type==='loan' && mode==='preclose') await api(`/loans/${encoded}/pre-close`,{method:'POST',body:JSON.stringify({settlementAmount:Number(amount)})});
      if(type==='loan' && mode==='restructure') await api(`/loans/${encoded}/restructures`,{method:'POST',body:JSON.stringify({effectiveMonth,monthlyEmiAmount:Number(amount),remainingTenureMonths:Number(remainingMonths)})});
      if(type==='fund' && mode==='lump-sum') await api(`/mutual-funds/${encoded}/lump-sums`,{method:'POST',body:JSON.stringify({amount:Number(amount),transactionDate:date,units:Number(units),calculationSource:'USER_ENTERED'})});
      if(type==='stock' && mode==='purchase') await api(`/stocks/${encoded}/purchases`,{method:'POST',body:JSON.stringify({amount:Number(amount),transactionDate:date,units:Number(units)})});
      if(type==='fund' && mode==='correct-investment') await api(`/mutual-funds/${encoded}/transactions/${encodeURIComponent(action.transactionId)}`,
        {method:'PATCH',body:JSON.stringify({amount:Number(amount),transactionDate:date,units:Number(units),calculationSource:'USER_ENTERED'})});
      if(type==='stock' && mode==='correct-investment') await api(`/stocks/${encoded}/transactions/${encodeURIComponent(action.transactionId)}`,
        {method:'PATCH',body:JSON.stringify({amount:Number(amount),transactionDate:date,units:Number(units)})});
      action=null;notice='Outcome saved. Your journey is refreshing.';
      await load(); await onChanged();
    } catch(cause) {notice=cause.message || 'The action could not be saved. Review its current status and retry.';await load();}
    finally {submitting=false;}
  }
</script>

<section class="money-tools"><div class="live-heading"><div><p class="eyebrow">SOURCE RECORDS AND COMMANDS</p><h1>Your money</h1></div><button onclick={load}>Refresh</button></div>
  <p>Review scheduled outcomes here. Each command goes to its owning service. Recorded investments, money set aside, loan payments and ordinary expenses keep their distinct meanings.</p>
  <TransactionsPanel {api} {month} {timezone} {onChanged}/>
  {#if notice}<p class="live-message" role="status">{notice}</p>{/if}
  {#if loading}<p>Loading your money…</p>{/if}
  <h2>Commitments</h2>{#if errors.commitments}<p>{errors.commitments}</p>{/if}
  {#each data.commitments?.items || [] as item (item.id)}
    <article class="money-source"><div><h3>{item.label}</h3><p>{item.planningAmount==null?'Amount unavailable':money(item.planningAmount)} planned · {item.status}</p></div><button onclick={()=>openDetail('commitment',item.id)}>History</button>
      {#each (item.currentOccurrences || []).filter(row=>row.month===month) as row}<p>{row.dueDate || month} · {row.status}</p>{#if row.status==='DUE'}<button onclick={()=>start('commitment',item.id,'record',item.planningAmount,row.dueDate,['DAY','WEEK'].includes(item.recurrenceUnit))}>Record payment</button><button onclick={()=>start('commitment',item.id,'skip',null,row.dueDate,['DAY','WEEK'].includes(item.recurrenceUnit))}>Skip</button>{/if}{/each}
    </article>
  {:else}<p>No commitments are available.</p>{/each}
  <h2>Savings plans</h2>{#if errors.savings}<p>{errors.savings}</p>{/if}
  {#each data.savings || [] as plan (plan.id)}<article class="money-source"><div><h3>Savings for commitment {plan.commitmentId}</h3><p>{money(plan.saved)} saved toward {money(plan.targetAmount)} · {plan.currentStatus}</p><small>Setting money aside does not pay the bill.</small></div>
    {#if plan.currentStatus==='DUE'}<button onclick={()=>start('savings',plan.commitmentId,'record',plan.monthlyAmount)}>Record savings</button><button onclick={()=>start('savings',plan.commitmentId,'skip')}>Skip</button>{/if}</article>{:else}<p>No savings plans are available.</p>{/each}
  <h2>Loans</h2>{#if errors.loans}<p>{errors.loans}</p>{/if}
  {#each data.loans?.loans || [] as loan (loan.id)}<article class="money-source"><div><h3>{loan.loanName}</h3><p>{loan.status} · {loan.remainingEmiCount} EMI(s) remaining</p></div><button onclick={()=>openDetail('loan',loan.id)}>History and options</button>
    {#each (loan.emiOccurrences || []).filter(row=>row.month===month) as emi}<p>{emi.dueDate} · {money(emi.plannedAmount)} · {emi.status}</p>{#if emi.status==='DUE'}<button onclick={()=>start('loan',loan.id,'record',emi.plannedAmount)}>Record EMI</button><button onclick={()=>start('loan',loan.id,'skip')}>Skip EMI</button>{/if}{/each}</article>{:else}<p>No loans are available.</p>{/each}
  <h2>Mutual funds</h2>{#if errors.funds}<p>{errors.funds}</p>{/if}
  {#each data.funds?.mutualFunds || [] as fund (fund.id)}<article class="money-source"><div><h3>{fund.schemeName}</h3><p>{money(fund.invested)} contributed · {fund.currentValue==null?'Valuation unavailable':`${money(fund.currentValue)} current value`}</p></div><button onclick={()=>openDetail('fund',fund.id)}>History and options</button>
    {#if fund.currentSip?.scheduledMonth===month}<p>SIP · {fund.currentSip.status} · {money(fund.currentSip.amount)}</p>{#if fund.currentSip.status==='DUE'}<button onclick={()=>start('fund',fund.id,'record',fund.currentSip.amount)}>Record SIP</button><button onclick={()=>start('fund',fund.id,'skip')}>Skip SIP</button>{/if}{/if}</article>{:else}<p>No mutual funds are available.</p>{/each}
  <h2>Stocks</h2>{#if errors.stocks}<p>{errors.stocks}</p>{/if}
  {#each data.stocks?.stocks || [] as stock (stock.id)}<article class="money-source"><div><h3>{stock.name}</h3><p>{money(stock.invested)} contributed · {stock.currentValue==null?'Valuation unavailable':`${money(stock.currentValue)} current value`}</p></div><button onclick={()=>openDetail('stock',stock.id)}>History and options</button>
    {#if String(stock.currentMonthlyPlan?.month)===month}<p>Monthly plan · {stock.currentMonthlyPlan.status} · {money(stock.currentMonthlyPlan.amount)}</p>{#if stock.currentMonthlyPlan.status==='DUE'}<button onclick={()=>start('stock',stock.id,'record',stock.currentMonthlyPlan.amount)}>Record purchase</button><button onclick={()=>start('stock',stock.id,'skip')}>Skip purchase</button>{/if}{/if}</article>{:else}<p>No stocks are available.</p>{/each}
  <h2>Credit cards</h2>{#if errors.cards}<p>{errors.cards}</p>{/if}
  {#each data.cards?.cards || [] as card (card.id)}<article class="money-source"><h3>{card.cardName}</h3><p>{card.issuerName} · statement day {card.statementDay} · bill due day {card.dueDay}</p><small>Bill projections appear in the current commitment story. This is not a second expense.</small></article>{:else}<p>No configured credit cards are available.</p>{/each}
</section>
{#if detail}<div class="live-overlay" role="presentation"><div role="dialog" tabindex="-1" aria-modal="true" aria-label="Source history"><button class="live-close" onclick={()=>detail=null}>Close</button><h2>{detail.type} history and options</h2>
  {#if detail.loading}<p>Loading owned history…</p>{:else if detailError}<p role="alert">{detailError}</p>{:else}
    {#if detail.type==='loan'}{#each detail.data?.history || [] as row}<p>{row.dueDate} · {row.status} · {row.paidAmount==null?'No payment recorded':money(row.paidAmount)}</p>{:else}<p>No EMI history is available.</p>{/each}
      {#if (data.loans?.loans || []).find(row=>row.id===detail.id)?.status==='ACTIVE'}<button onclick={()=>startDetail('preclose')}>Pre-close loan</button><button onclick={()=>startDetail('restructure')}>Restructure future EMIs</button>{/if}
    {:else if detail.type==='commitment'}{#each detail.data?.history || [] as row}<p>{row.dueDate || row.month} · {row.status} · {row.actualAmount==null?'No payment recorded':money(row.actualAmount)}</p>{:else}<p>No payment history is available.</p>{/each}
    {:else if detail.type==='fund'}{#each detail.data?.history || [] as row}<p>{row.transactionDate || row.dueDate || row.scheduledMonth} · {row.kind} · {row.status} · {row.amount==null?'Amount unavailable':money(row.amount)} {#if row.status==='CONFIRMED'}<button onclick={()=>startDetail('correct-investment',row)}>Correct</button>{/if}</p>{:else}<p>No fund history is available.</p>{/each}<button onclick={()=>startDetail('lump-sum')}>Record lump sum</button>
    {:else if detail.type==='stock'}{#each detail.data?.history || [] as row}<p>{row.transactionDate || row.scheduledMonth} · {row.kind} · {row.status} · {row.amount==null?'Amount unavailable':money(row.amount)} {#if row.status==='CONFIRMED'}<button onclick={()=>startDetail('correct-investment',row)}>Correct</button>{/if}</p>{:else}<p>No stock history is available.</p>{/each}<button onclick={()=>startDetail('purchase')}>Record purchase</button>{/if}
  {/if}
</div></div>{/if}
{#if action}<div class="live-overlay" role="presentation"><div role="dialog" tabindex="-1" aria-modal="true" aria-label="Confirm source outcome"><h2>{({record:'Record',skip:'Skip',preclose:'Pre-close',restructure:'Restructure','lump-sum':'Record lump sum',purchase:'Record purchase','correct-investment':'Correct investment'})[action.mode]} {action.type}</h2><p>Review the source and month before confirming. This changes your real records.</p>
  {#if ['record','preclose','lump-sum','purchase','correct-investment','restructure'].includes(action.mode) && !(action.mode==='record' && action.type==='loan')}<label>{action.mode==='preclose'?'Settlement amount':action.mode==='restructure'?'New monthly EMI':'Actual amount'} <input type="number" min="0.01" step="0.01" bind:value={amount}/></label>{/if}
  {#if ['record','lump-sum','purchase','correct-investment'].includes(action.mode) && ['commitment','fund','stock'].includes(action.type)}<label>Effective date <input type="date" bind:value={date}/></label>{/if}
  {#if action.mode==='record' && action.type==='commitment'}<label>Next expected date <input type="date" bind:value={nextDate}/></label>{/if}
  {#if ['record','lump-sum','purchase','correct-investment'].includes(action.mode) && ['fund','stock'].includes(action.type)}<label>Units <input type="number" min="0.000001" step="any" bind:value={units}/></label>{/if}
  {#if action.mode==='record' && action.type==='stock'}<label>Execution price <input type="number" min="0.01" step="any" bind:value={price}/></label>{/if}
  {#if action.mode==='skip' && action.type==='loan'}<label>Bank penalty, if charged <input type="number" min="0.01" step="0.01" bind:value={penalty}/></label>{/if}
  {#if action.mode==='restructure'}<label>Future effective month <input type="month" bind:value={effectiveMonth}/></label><label>Remaining months <input type="number" min="1" step="1" bind:value={remainingMonths}/></label>{/if}
  {#if notice}<p role="alert">{notice}</p>{/if}<button onclick={()=>action=null} disabled={submitting}>Cancel</button><button onclick={submit} disabled={submitting}>{submitting?'Saving…':'Confirm'}</button></div></div>{/if}
