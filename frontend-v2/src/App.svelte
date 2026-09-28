<script>
  // FIN-EPIC-007 — vertical sample journey; all state is local and resets on reload.
  import { onMount, tick } from 'svelte';
  import Landscape from './components/Landscape.svelte';
  import TellUs from './components/TellUs.svelte';
  import TimeSky from './components/TimeSky.svelte';
  import ScheduledItem from './components/ScheduledItem.svelte';
  import { days, money, dayLabel, weekday } from './lib/journey.js';
  import { scheduled, demoToday, sumMoney, decideOccurrence, projectedCommitments } from './lib/schedule.js';
  import { sampleMonths, sampleMonth, validDate, readLocation, journeyUrl, monthName } from './lib/navigation.js';

  const dates = ['2026-09-22', ...days.map(day => day.date).reverse()];
  const initialRoute = readLocation(location.search);
  let view = initialRoute.view;
  let selectedMonth = initialRoute.month;
  let selectedDate = initialRoute.day || (initialRoute.month === sampleMonth ? demoToday : null);
  let expanded = { [selectedDate]: true };
  let captureDate = demoToday;
  let composerOpen = false;
  let additions = {};
  let occurrences = scheduled.map(item => ({ ...item }));
  let activeAction = null;
  let notice = '';
  let dateError = '';
  let detailsDialog;
  let monthlyDialog;
  let detailTitle = '';
  let detailBody = '';
  let returnScroll = 0;
  let firstUse = false;
  let firstUseRecords = [];
  let showAllActivity = {};
  let quietGapOpen = ["2026-09-13", "2026-09-14"].includes(selectedDate);
  let detailHistoryOwned = false;
  $: visibleDates = selectedMonth === sampleMonth ? dates : selectedDate ? [selectedDate] : [];
  $: overdue = occurrences.filter(item => item.status==='due' && item.date < demoToday);
  $: dueCount = occurrences.filter(item => item.status==='due').length;
  $: capturedSpend = sumMoney([1380, ...Object.values(additions).flat().map(row => row.amount)]);
  $: planned = projectedCommitments(occurrences);
  $: invested = sumMoney([4000, ...occurrences.filter(item=>item.type==='investment' && item.status==='recorded').map(item=>item.actualAmount)]);
  $: savedThisMonth = sumMoney([5000, ...occurrences.filter(item=>item.type==='savings' && item.status==='recorded').map(item=>item.actualAmount)]);

  function source(date) { return days.find(day => day.date===date); }
  function dateItems(date, schedule=occurrences) { return schedule.filter(item => item.date===date); }
  function activity(date, extra=additions, schedule=occurrences) {
    return [ ...(source(date)?.activity || []).map(row=>({label:row[0],amount:row[1],note:row[2],detail:source(date).detail})),
      ...(extra[date] || []).map(row=>({...row,note:'Added in this preview',detail:'Temporary sample expense; reload clears it. No real record was saved.'})),
      ...schedule.filter(item=>item.status!=='due' && item.status!=='upcoming' && (item.recordedDate || item.decidedDate)===date).map(item=>({label:item.name,amount:item.status==='skipped'?null:item.actualAmount,note:item.status==='skipped'?'Occurrence skipped':item.type==='savings'?'Money set aside':'Payment recorded',detail:`Due ${dayLabel(item.date)}; outcome recorded ${dayLabel(item.recordedDate || item.decidedDate)}. This is the same occurrence shown on its due date, not an additional expense.`})) ];
  }
  function summary(date, extra=additions, schedule=occurrences) {
    const items=dateItems(date,schedule);
    const pending=items.filter(item=>item.status==='due');
    const base=source(date);
    const added=extra[date]?.length || 0;
    const addedText=added?` · ${added} new ${added===1?'expense':'expenses'}`:'';
    if(!base && !items.length) return 'No sample records available for this date';
    if(date>demoToday) return `${money(sumMoney(items.map(item=>item.amount)))} upcoming · ${items[0]?.name || 'Planned'}`;
    if(base?.kind==='quiet') {
      const quietState=added?`${money(sumMoney(extra[date].map(row=>row.amount)))} in ${added} new ${added===1?'expense':'expenses'} · No published story`:'No activity recorded';
      if(pending.length) return `${money(sumMoney(pending.map(item=>item.amount)))} overdue · ${quietState}`;
      const reviewed=items.filter(item=>item.status==='skipped' || item.status==='recorded').length;
      return `${quietState}${reviewed?` · ${reviewed} item reviewed`:''}`;
    }
    const fact=base?.kind==='life'?`${money(base.amount)} spent · ${base.activity.length} recorded expenses`
      :base?.kind==='shelter'?`${money(base.amount)} set aside · Bill still unpaid`
      :base?.kind==='tree'?`${money(base.amount)} invested`
      :base?.kind==='bridge'?`${money(base.amount)} loan payment recorded`
      :'No activity recorded';
    return `${fact}${pending.length?` · ${pending.length} due to review`:''}${addedText}`;
  }
  function route(next, replace=false) {
    history.replaceState({ scrollY: window.scrollY }, '', location.href);
    history[replace?'replaceState':'pushState']({ scrollY: window.scrollY }, '', journeyUrl(next));
  }
  function toggle(date) { expanded={...expanded,[date]:!expanded[date]};selectedDate=date;route({month:selectedMonth,day:date}); }
  async function reveal(date) {
    if(!validDate(date) || !sampleMonths.includes(date.slice(0,7))) { dateError='Choose a date in August–October 2026 for this sample preview.';return; }
    dateError='';if(['2026-09-13','2026-09-14'].includes(date))quietGapOpen=true;selectedMonth=date.slice(0,7);selectedDate=date;expanded={...expanded,[date]:true};view='journey';route({month:selectedMonth,day:date});await tick();
    const node=document.getElementById(`day-${date}`);node?.scrollIntoView({block:'start',behavior:'instant'});node?.querySelector('.day-toggle')?.focus({preventScroll:true});
  }
  async function changeMonth(month) {
    if(!sampleMonths.includes(month)) return;
    selectedMonth=month;selectedDate=month===sampleMonth?demoToday:null;expanded=selectedDate?{...expanded,[selectedDate]:true}:{};dateError='';route({month});await tick();window.scrollTo(0,0);
  }
  function open(title,body,id=null) { detailTitle=title;detailBody=body;if(id){detailHistoryOwned=true;route({month:selectedMonth,day:selectedDate,detail:id});}detailsDialog.showModal(); }
  function openMonth() { detailHistoryOwned=true;route({month:selectedMonth,day:selectedDate,detail:'month'});monthlyDialog.showModal(); }
  function closeDetail(dialog) { dialog.close();if(detailHistoryOwned){detailHistoryOwned=false;history.back();}else if(readLocation(location.search).detail)route({month:selectedMonth,day:selectedDate},true); }
  function jumpFromMonth(date) { monthlyDialog.close();detailHistoryOwned=false;history.replaceState({scrollY:window.scrollY},'',journeyUrl({month:selectedMonth,day:selectedDate}));reveal(date); }
  function resolveDetail(id) {
    if(id==='month' && selectedMonth===sampleMonth) { monthlyDialog.showModal();return; }
    if(id?.startsWith('story:')) { const base=source(id.slice(6));if(base){detailTitle=base.title;detailBody=`${base.body} ${base.detail}`;detailsDialog.showModal();}return; }
    if(id?.startsWith('task:')) { const item=occurrences.find(row=>row.id===id.slice(5));if(item){const outcome=item.status==='recorded'?`${money(item.actualAmount)} recorded on ${dayLabel(item.recordedDate)}.`:item.status==='skipped'?`Skipped on ${dayLabel(item.decidedDate)}.`:'No payment is recorded.';detailTitle=item.name;detailBody=`${money(item.amount)} planned for ${dayLabel(item.date)}. ${outcome} ${item.note}`;detailsDialog.showModal();}return; }
    if(id?.startsWith('activity:')) { const [,date,index]=id.split(':');const record=activity(date)[Number(index)];if(record){detailTitle=record.label;detailBody=`${record.amount==null?'No payment':money(record.amount)} · ${dayLabel(date)}. ${record.detail}`;detailsDialog.showModal();} }
  }
  async function applyLocation() {
    const next=readLocation(location.search);if(['2026-09-13','2026-09-14'].includes(next.day))quietGapOpen=true;selectedMonth=next.month;selectedDate=next.day || (next.month===sampleMonth?demoToday:null);view=next.view;expanded={...expanded,...(selectedDate?{[selectedDate]:true}:{})};detailHistoryOwned=false;
    if(detailsDialog?.open)detailsDialog.close();if(monthlyDialog?.open)monthlyDialog.close();await tick();
    if(next.detail)resolveDetail(next.detail);
    const target=selectedDate && next.day?document.getElementById(`day-${selectedDate}`):null;
    if(target)target.scrollIntoView({block:'start',behavior:'instant'});else window.scrollTo(0,history.state?.scrollY || 0);
  }
  onMount(()=>{history.scrollRestoration='manual';history.replaceState({scrollY:window.scrollY},'',location.href);const pop=()=>applyLocation();window.addEventListener('popstate',pop);if(initialRoute.day || initialRoute.detail)applyLocation();return()=>window.removeEventListener('popstate',pop);});
  function showItem(item) {
    const outcome=item.status==='recorded'?`${money(item.actualAmount)} recorded on ${dayLabel(item.recordedDate)}${item.units?` with ${item.units} units (derived NAV ${money(item.actualAmount/item.units)})`:''}.`:item.status==='skipped'?`Skipped on ${dayLabel(item.decidedDate)}.`:'No payment is recorded.';
    open(item.name,`${money(item.amount)} planned for ${dayLabel(item.date)}. ${outcome} ${item.type==='savings'?'Savings are not an insurance payment. ':''}This is a sample occurrence. Full plan editing, history and corrections will remain in Money; routine recording can happen here.`,`task:${item.id}`);
  }
  function decide(id,outcome) {
    const result=decideOccurrence(occurrences,id,outcome);if(result===occurrences)return false;
    occurrences=result;notice=`${occurrences.find(item=>item.id===id).name}: sample ${outcome.status==='skipped'?'skip':'record'} confirmed. No real money moved. Reload clears changes.`;return true;
  }
  function addSamples(date,rows) {
    if(firstUse) { firstUseRecords=[...firstUseRecords,...rows];notice=`${rows.length} sample expenses added. This first-use preview resets on reload.`;return; }
    additions={...additions,[date]:[...(additions[date] || []),...rows]};expanded={...expanded,[date]:true};notice=`${rows.length} sample expenses added to ${dayLabel(date)}. Existing published sample stories are unchanged.`;
  }
  function showFirstUse() { firstUse=true;view='journey';captureDate=demoToday;composerOpen=true;notice='';window.scrollTo(0,0); }
  function showSampleJourney() { firstUse=false;view='journey';captureDate=demoToday;composerOpen=false;notice='';window.scrollTo(0,0); }
  async function addFor(date) { captureDate=date;composerOpen=true;await tick();document.getElementById('capture-entry')?.scrollIntoView({block:'center',behavior:'instant'});document.getElementById('day-message')?.focus({preventScroll:true}); }
  async function navigate(next) { if(next===view)return;if(next==='money'){returnScroll=window.scrollY;view=next;route({month:selectedMonth,day:selectedDate,view:'money'});await tick();window.scrollTo(0,0);}else{view=next;route({month:selectedMonth,day:selectedDate});await tick();window.scrollTo(0,returnScroll);} }
</script>

<svelte:head><title>Money Stories · Your journey</title></svelte:head>
<div class="demo-banner"><span class="status-dot"></span> UI V2 PREVIEW <span>/</span> Stage 04 · Sample data only · Demo today: 21 Sep</div>
<header class="app-header"><button class="brand" onclick={()=>navigate('journey')}><span class="brand-mark">m<span>•</span></span>money stories<span class="version">v2</span></button><div class="header-actions"><button class="avatar" aria-label="About this preview" onclick={()=>open('Your private preview','The demo clock is fixed at 21 September 2026; the tiny sky uses your actual device time. All amounts are fictional. No live financial data is connected and all new entries and payment decisions clear on reload.')}>D</button></div></header>
<main>
{#if view==='journey'}
  <section class="journey-heading"><div><p class="eyebrow">YOUR MONEY, DAY BY DAY</p><h1>A story in every step.</h1></div><div class="traveller-world"><TimeSky/><svg class="traveller" viewBox="0 0 30 42" aria-hidden="true"><circle cx="15" cy="7" r="5" fill="#47684c"/><path d="M14 15 11 27 6 37M12 26 22 37M14 16 23 23M13 17 5 24" fill="none" stroke="#47684c" stroke-width="3.5" stroke-linecap="round"/><path d="M9 13h10v14H9Z" fill="#bc9b67"/></svg></div></section>
  {#if firstUse}
    <section class="first-use-overview" aria-label="First-use preview"><p class="eyebrow">NEW MEMBER PREVIEW · SEPTEMBER 2026</p><h2>Your story starts here.</h2><p>{firstUseRecords.length?'Your first sample entry is below. Add another whenever you want.':'No money activity has been recorded yet. Tell us about an expense when you are ready; this preview uses examples only.'}</p>{#if firstUseRecords.length}<div class="first-use-total"><span>Recorded sample expenses</span><strong>{money(sumMoney(firstUseRecords.map(row=>row.amount)))}</strong></div>{/if}</section>
    <section class="composer-shell" id="capture-entry"><button class="composer-toggle" aria-expanded={composerOpen} aria-controls="capture-content" onclick={()=>composerOpen=!composerOpen}><span><strong>{firstUseRecords.length?'Add another expense':'Tell us about your first expense'}</strong><small>For example, Lunch ₹180. Review before adding.</small></span><span aria-hidden="true">{composerOpen?'−':'＋'}</span></button><div id="capture-content" hidden={!composerOpen}><TellUs compact date={demoToday} latest onSave={addSamples}/></div></section>
    {#if notice}<p class="sample-notice" role="status">{notice}</p>{/if}
    {#if firstUseRecords.length}<section class="first-use-activity" aria-label="First recorded day"><p class="eyebrow">21 SEPTEMBER · FIRST RECORDED DAY</p><h2>{money(sumMoney(firstUseRecords.map(row=>row.amount)))} recorded in {firstUseRecords.length} {firstUseRecords.length===1?'expense':'expenses'}</h2>{#each firstUseRecords as record}<div class="first-use-row"><span>{record.label}</span><strong>{money(record.amount)}</strong></div>{/each}<p class="quiet-note">No story has been generated from this sample entry.</p></section>{/if}
    <button class="text-action" onclick={showSampleJourney}>← Return to populated sample journey</button>
  {:else}
  {#if selectedMonth===sampleMonth}
  <section class="month-overview" aria-label="September overview"><div class="month-top"><div><p class="eyebrow">THE BIGGER PICTURE</p><h2>September at a glance</h2></div><button class="text-action" onclick={openMonth}>View month <span aria-hidden="true">↗</span></button></div><div class="month-values"><div><span>Recorded expenses</span><strong data-testid="monthly-spend">{money(capturedSpend)}</strong></div><div><span>Planned commitments</span><strong data-testid="monthly-plan">{money(planned)}</strong></div></div><div class="month-foot"><span>Latest sample position · 21 Sep</span><span>{dueCount} {dueCount===1?'item needs':'items need'} review</span></div></section>
  <section class="composer-shell" id="capture-entry"><button class="composer-toggle" aria-expanded={composerOpen} aria-controls="capture-content" onclick={()=>composerOpen=!composerOpen}><span><strong>{captureDate===demoToday?'Anything to add to today’s story?':`Add something for ${dayLabel(captureDate)}`}</strong><small>Tell us in your own words. Review before adding.</small></span><span aria-hidden="true">{composerOpen?'−':'＋'}</span></button><div id="capture-content" hidden={!composerOpen}>{#key captureDate}<TellUs compact date={captureDate} latest={captureDate===demoToday} onSave={addSamples}/>{/key}{#if captureDate!==demoToday}<button class="text-action" onclick={()=>captureDate=demoToday}>Use demo today · 21 September</button>{/if}</div></section>
  {#if overdue.length}<button class="overdue-reminder" onclick={()=>reveal(overdue[0].date)}><span class="reminder-dot"></span><span>{overdue.length} overdue {overdue.length===1?'item':'items'} still {overdue.length===1?'needs':'need'} attention <small>{overdue.map(item=>`${item.name} · ${dayLabel(item.date)}`).join(', ')}</small></span><span aria-hidden="true">↗</span></button>{/if}
  {:else}
  <section class="empty-month" aria-label="No sample data for this month"><p class="eyebrow">{monthName(selectedMonth).toUpperCase()}</p><h2>No sample records for this month</h2><p>This preview has recorded examples in September. Choose a date to inspect an empty sample day, or return to September.</p><button class="text-action" onclick={()=>changeMonth(sampleMonth)}>View September ↗</button></section>
  {/if}
  <div class="history-toolbar"><h2>Your journey</h2><div><label class="month-switch"><span class="sr-only">Choose month</span><input aria-label="Choose month" type="month" min="2026-08" max="2026-10" value={selectedMonth} onchange={(event)=>changeMonth(event.target.value)}/></label><button class="text-action" onclick={()=>reveal(demoToday)}>Today</button><label class="date-jump"><span class="sr-only">Jump to date</span><input aria-label="Jump to date" type="date" min="2026-08-01" max="2026-10-31" value={selectedDate || ''} onchange={(event)=>reveal(event.target.value)}/></label></div></div>
  {#if dateError}<p class="capture-error" role="alert">{dateError}</p>{/if}
  {#if notice}<p class="sample-notice" role="status">{notice}</p>{/if}
  <div class="timeline" aria-label="Daily journey">
    {#each visibleDates as date (date)}
      {@const base=source(date)}
      {@const items=dateItems(date,occurrences)}
      {@const records=activity(date,additions,occurrences)}
      {@const newOutcomes=occurrences.filter(item=>(item.status==='recorded' || item.status==='skipped') && (item.recordedDate || item.decidedDate)===date)}
      {#if date==='2026-09-14' && !quietGapOpen}
      <section class="date-group quiet-gap" aria-label="Quiet stretch 13 to 14 September"><div class="date-stone" aria-hidden="true"><span>SEP</span><strong>13–14</strong></div><div class="day-card"><button class="day-toggle" onclick={()=>quietGapOpen=true}><span><span class="date-title">A quiet stretch</span><span class="day-summary">13–14 September · No sample activity recorded</span></span><span class="expand-mark" aria-hidden="true">＋</span></button></div></section>
      {:else if date!=='2026-09-13' || quietGapOpen}
      {#if date==='2026-09-14' && quietGapOpen}<button class="text-action collapse-gap" onclick={()=>{quietGapOpen=false;selectedDate=demoToday;route({month:sampleMonth,day:demoToday});}}>Collapse quiet stretch</button>{/if}
      <section class="date-group" class:is-today={date===demoToday} class:is-future={date>demoToday} id={`day-${date}`} aria-label={dayLabel(date)}>
        <div class="date-stone" aria-hidden="true"><span>{monthName(date.slice(0,7)).slice(0,3).toUpperCase()}</span><strong>{date.slice(-2)}</strong></div>
        <div class="day-card"><button class="day-toggle" aria-expanded={!!expanded[date]} aria-controls={`content-${date}`} onclick={()=>toggle(date)}><span class="day-heading"><span class="date-title">{date===demoToday?'Today':date==='2026-09-22'?'Tomorrow':dayLabel(date)} <small>{weekday(date)}</small></span><span class="day-summary">{summary(date,additions,occurrences)}</span></span><span class="expand-mark" aria-hidden="true">{expanded[date]?'−':'＋'}</span></button>
          <div id={`content-${date}`} class="day-content" hidden={!expanded[date]}>
            {#if base && base.kind!=='quiet'}<article class="day-story"><div><p class="eyebrow">YOUR STORY · {dayLabel(date).toUpperCase()}</p><h3>{base.title}</h3><p>{base.body}</p><button class="text-action" onclick={()=>open(base.title,`${base.body} ${base.detail}`,`story:${date}`)}>View supporting details <span aria-hidden="true">↗</span></button></div>{#if ['tree','bridge','shelter'].includes(base.kind)}<div class="story-illustration" aria-hidden="true"><Landscape day={base}/></div>{/if}</article>{/if}
            {#if items.length}<div class="schedule-list" aria-label={`Scheduled items for ${dayLabel(date)}`}><h3 class="schedule-heading">{date>demoToday?'Coming up':items.some(item=>item.status==='due')?'Needs your attention':'Scheduled item'}</h3>{#each items as item (item.id)}<ScheduledItem {item} {activeAction} onStart={(id)=>activeAction=id} onDecide={decide} onDetails={showItem}/>{/each}</div>{/if}
            {#if additions[date]?.length || newOutcomes.length}<p class="sample-update">New sample activity is shown below. {base?.kind!=='quiet'?'Published sample story unchanged.':'No AI story generated.'}</p>{/if}
            {#if !base && !items.length}<p class="quiet-note">No sample records are available for this date. This is not a zero-spend claim.</p>{/if}
            {#if date<=demoToday && selectedMonth===sampleMonth}<section class="activity-section" aria-label={`Activity for ${dayLabel(date)}`}><div class="activity-heading"><h3>Recorded activity</h3><span>{records.length}</span></div>{#each (showAllActivity[date]?records:records.slice(0,3)) as record,i}<button class="activity-row" onclick={()=>open(record.label,`${record.amount==null?'No payment':money(record.amount)} · ${dayLabel(date)}. ${record.detail}`,`activity:${date}:${i}`)}><span><strong>{record.label}</strong><small>{record.note}</small></span><b>{record.amount==null?'Skipped':money(record.amount)}</b><span aria-hidden="true">↗</span></button>{:else}<p class="quiet-note">No activity recorded. This does not mean nothing was spent.</p>{/each}{#if records.length>3}<button class="text-action activity-more" onclick={()=>showAllActivity={...showAllActivity,[date]:!showAllActivity[date]}}>{showAllActivity[date]?'Show fewer':`Show all ${records.length} items`}</button>{/if}</section><button class="add-day" onclick={()=>addFor(date)}>＋ Add something for {date===demoToday?'today':dayLabel(date)}</button>{/if}
          </div>
        </div>
      </section>
      {/if}
    {/each}
  </div>
  {/if}
{:else}
  <section class="money-page"><p class="eyebrow">THE TOOLS BEHIND YOUR JOURNEY</p><h1>Your money, in one place.</h1><p class="intro">Record routine payments in the journey. Manage the full details here.</p><div class="tools-grid">{#each [['Transactions','Search and correct recorded expenses','Stage 10'],['Commitments','Schedules, savings and payment history','Stage 11'],['Loans','Repayment history and schedule changes','Stage 12'],['Investments','Funds, stocks and contribution history','Stage 13'],['Credit cards','Billing cycles and projected statements','Stage 13']] as tool}<button onclick={()=>open(tool[0],`${tool[2]} will connect the full management flow. No real records are available in this sample preview.`)}><span class="eyebrow">PREVIEW</span><h2>{tool[0]} ↗</h2><p>{tool[1]}</p></button>{/each}</div><button class="text-action" onclick={()=>navigate('journey')}>← Back to your journey</button></section>
{/if}
<footer class="page-footer">Your history. At your pace.<span>{#if !firstUse}<button class="preview-switch" onclick={showFirstUse}>Preview first-use state</button> · {/if}UI v2 · Stage 04 / 15</span></footer>
</main>
<nav class="bottom-nav" aria-label="Main navigation"><button class:active={view==='journey'} aria-current={view==='journey'?'page':undefined} onclick={()=>navigate('journey')}><span aria-hidden="true">⌁</span>Journey</button><button class:active={view==='money'} aria-current={view==='money'?'page':undefined} onclick={()=>navigate('money')}><span aria-hidden="true">▥</span>Money</button></nav>
<dialog bind:this={detailsDialog} oncancel={(event)=>{event.preventDefault();closeDetail(detailsDialog);}} aria-labelledby="detail-title"><div class="dialog-top"><span class="eyebrow">SAMPLE DETAILS</span><button aria-label="Close details" onclick={()=>closeDetail(detailsDialog)}>×</button></div><h2 id="detail-title">{detailTitle}</h2><p>{detailBody}</p><button class="dialog-done" onclick={()=>closeDetail(detailsDialog)}>Back to the journey</button></dialog>
<dialog bind:this={monthlyDialog} oncancel={(event)=>{event.preventDefault();closeDetail(monthlyDialog);}} aria-labelledby="month-title"><div class="dialog-top"><span class="eyebrow">LATEST SAMPLE POSITION · 21 SEP</span><button aria-label="Close monthly overview" onclick={()=>closeDetail(monthlyDialog)}>×</button></div><h2 id="month-title">September, together.</h2><div class="monthly-details"><div><span>Recorded expenses</span><strong>{money(capturedSpend)}</strong></div><div><span>Planned commitments</span><strong>{money(planned)}</strong></div><div><span>Recorded investment contributions</span><strong>{money(invested)}</strong></div><div><span>Recorded savings this month</span><strong>{money(savedThisMonth)}</strong></div></div><p>These measures overlap. They are not added together and do not represent an account balance. Recording a payment here does not create a second expense.</p><p>Sample projection: ₹16,000 loan payments, ₹4,000 investment contributions and ₹5,000 savings already recorded in September, plus included scheduled items. Skipped items leave the active projection; a completed savings contribution also leaves it. Ordinary completed commitments remain planned.</p><h3 class="monthly-subtitle">Scheduled items</h3>{#each occurrences as item}<button class="month-source" onclick={()=>jumpFromMonth(item.date)}><span>{item.name}<small>{dayLabel(item.date)} · {item.status}</small></span><strong>{money(item.amount)} ↗</strong></button>{/each}<p class="local-note">Next-month projections and historical monthly snapshots arrive in later stages. This prototype has one sample month.</p></dialog>
