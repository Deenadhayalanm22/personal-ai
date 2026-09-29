<script>
  // FIN-EPIC-007: live Journey uses only owned server facts. The sample app remains separate.
  import { onMount } from 'svelte';
  import { createReadClient, normalizeStory, profileDate } from './lib/live-read.js';
  import { liveUrl, readLiveLocation } from './lib/live-navigation.js';
  import { money, dayLabel } from './lib/journey.js';
  import ExistingHome from '../../frontend/src/Home.svelte';
  import Conversation from './Conversation.svelte';

  const client = createReadClient();
  const current = new Date();
  const initial=readLiveLocation(location.search,`${current.getFullYear()}-${String(current.getMonth()+1).padStart(2,'0')}`);
  let month = initial.month;
  let view = 'loading';
  let message = '';
  let sections = {};
  let selectedDate = initial.day;
  let activity = {items:[],nextCursor:null};
  let activityState = 'idle';
  let selectedStory = null;
  let selectedPublication = null;
  let editItem = null;
  let editAmount = '';
  let editDate = '';
  let busy = false;
  let demoMode = false;
  let canUseDemoMode = false;
  let detailHistoryOwned = false;
  let focusedMoneyItem = null;
  let monthRequest = 0;
  let activityRequest = 0;


  async function api(path, options={}) {
    const response = await fetch(`/api/web${path}`, {credentials:'include',...options,
      headers:{...(options.body?{'content-type':'application/json'}:{}),...options.headers}});
    if(response.status===401){ client.clear(); view='login'; throw Error('Session expired'); }
    if(!response.ok) throw Error((await response.json().catch(()=>({}))).message || `Request failed (${response.status})`);
    return response.status===204?null:response.json();
  }
  async function initialize() {
    try {
      const profile=await api('/auth/demo-profile');
      demoMode=profile.demoMode;
      canUseDemoMode=profile.canUseDemoMode;
      // The API intentionally exposes no owner ID. Keep each verified session in its own
      // memory-cache namespace, even when two sessions both use the real profile mode.
      client.setOwner(`${crypto.randomUUID()}:${demoMode?'demo':'real'}`);
      view=initial.view;
      await loadMonth();
      if(selectedDate) await selectDay(selectedDate,false);
      if(initial.story) showStory(initial.story,false);
    } catch(cause) { if(view!=='login'){view='error';message=cause.message;} }
  }
  async function loadMonth(force=false) {
    const request=++monthRequest, requestedMonth=month;
    message='';
    const names=['days','projection','occurrences','stories','history'];
    const results=await Promise.allSettled(names.map(name=>client[name](month,undefined,force)));
    if(request!==monthRequest || requestedMonth!==month)return;
    sections=Object.fromEntries(names.map((name,index)=>[name,results[index].status==='fulfilled'
      ? results[index].value : {data:null,freshness:'unavailable',error:results[index].reason}]));
    if(results.some(result=>result.status==='rejected' && result.reason?.status===401)) {client.clear();view='login';return;}
    if(results.every(result=>result.status==='rejected')) message='This month is unavailable. Retry when connected.';
  }
  async function changeMonth(next,push=true) {
    if(!/^\d{4}-(0[1-9]|1[0-2])$/.test(next)) return;
    month=next; selectedDate=null; selectedStory=null; selectedPublication=null; activity={items:[],nextCursor:null};
    if(push)history.pushState({},'',liveUrl({month,view}));
    await loadMonth();
  }
  async function selectDay(date,push=true) {
    if(date.slice(0,7)!==month)return;
    const request=++activityRequest;
    selectedDate=date; activity={items:[],nextCursor:null};activityState='loading';
    if(push) history.pushState({},'',liveUrl({month,day:date,view}));
    try {const page=await client.activity(date);if(request===activityRequest){activity=page;activityState='ready';}} catch(cause) {if(request===activityRequest){activityState='error';message=cause.message;}}
  }
  function navigate(next) {if(view===next)return;const leftMoney=view==='money';view=next;selectedStory=null;selectedPublication=null;history.pushState({},'',liveUrl({month,day:selectedDate,view}));window.scrollTo(0,0);if(leftMoney&&next==='journey')refreshAfterMoneyChange();}
  function openMoney(item) {focusedMoneyItem=item;navigate('money');}
  const homeSection=(section)=>({status:section?.data?'ready':'loading',data:section?.data||null,error:section?.error?.message||''});
  const homeStoriesSection=()=>{const section=homeSection(sections.stories);if(!section.data)return section;const commitment=sections.projection?.data?.state==='AVAILABLE'?sections.projection.data.story:null;return {...section,data:{...section.data,stories:[...(commitment?[commitment]:[]),...(section.data.stories||[])]}};};
  async function switchDemoMode(enabled){const profile=await api('/auth/demo-profile',{method:'PUT',body:JSON.stringify({enabled})});demoMode=profile.demoMode;client.clear();client.setOwner(`${crypto.randomUUID()}:${demoMode?'demo':'real'}`);await loadMonth(true);}
  function showStory(id,push=true) {
    const source=sections.stories?.data?.stories?.find(story=>story.storyId===id);
    if(!source)return;
    try{selectedStory=normalizeStory(source);}catch{message='This published story is unavailable.';return;}
    detailHistoryOwned=push;
    if(push)history.pushState({},'',liveUrl({month,day:selectedDate,view:'journey',story:id}));
  }
  function closeStory() {selectedStory=null;if(detailHistoryOwned){detailHistoryOwned=false;history.back();}else history.replaceState({},'',liveUrl({month,day:selectedDate,view}));}
  async function moreActivity() {
    if(!activity.nextCursor || activityState==='loading') return;
    activityState='loading';
    try {const page=await client.activity(selectedDate,{cursor:activity.nextCursor});activity={items:[...activity.items,...page.items],nextCursor:page.nextCursor};activityState='ready';}
    catch(cause){activityState='error';message=cause.message;}
  }
  function storyFor(date) {
    return (sections.stories?.data?.stories || []).filter(item=>item.period?.startDate<=date && item.period?.endDate>=date);
  }
  function dueFor(date) {return (sections.occurrences?.data?.items || []).filter(item=>item.dueDate===date);}
  function openEdit(item) {editItem=item;editAmount=String(item.amount);editDate=profileDate(item.transactionTime,sections.days?.data?.timezone) || selectedDate;message='';}
  async function saveEdit() {
    if(!editItem || !/^\d{4}-\d{2}-\d{2}$/.test(editDate) || !Number.isFinite(Number(editAmount)) || Number(editAmount)<=0){message='Enter a valid date and positive amount.';return;}
    busy=true;const oldMonth=selectedDate.slice(0,7);
    try {await api(`/expenses/${encodeURIComponent(editItem.id)}`,{method:'PATCH',body:JSON.stringify({amount:Number(editAmount),transactionDate:editDate})});
      client.invalidate(oldMonth);client.invalidate(editDate.slice(0,7));editItem=null;await loadMonth(true);await selectDay(selectedDate,false);message='Expense corrected. Published stories retain their original evidence and may be marked stale until republished.';
    } catch(cause){message=cause.message;} finally {busy=false;}
  }
  async function removeExpense(item) {
    if(!confirm(`Delete ${item.merchant || item.category || 'this expense'}?`)) return;
    busy=true;
    try {await api(`/expenses/${encodeURIComponent(item.id)}`,{method:'DELETE'});client.invalidate(month);await loadMonth(true);await selectDay(selectedDate,false);message='Expense deleted. Prior story publications remain historical.';}
    catch(cause){message=cause.message;} finally{busy=false;}
  }
  async function handoff() {
    if(!selectedDate) return;
    busy=true;
    try {const zone=sections.days?.data?.timezone;if(!zone) throw Error('Timezone unavailable');
      const result=await api('/expenses/calendar/context',{method:'POST',body:JSON.stringify({type:'MISSING_TRANSACTION_DATE',date:selectedDate,timezone:zone})});
      if(result.whatsappUrl) location.href=result.whatsappUrl;
      else message='WhatsApp handoff is unavailable for this profile.';
    }catch(cause){message=cause.message;}finally{busy=false;}
  }
  async function logout() {try{await api('/auth/logout',{method:'POST'});}finally{client.clear();sections={};view='login';location.replace('/portal');}}
  async function refreshAfterMoneyChange() {client.invalidate();await loadMonth(true);if(selectedDate)await selectDay(selectedDate,false);}
  onMount(()=>{initialize();const unauthorized=()=>{client.clear();sections={};view='login';};const pop=async()=>{const next=readLiveLocation(location.search,month);const leftMoney=view==='money';if(next.month!==month)await changeMonth(next.month,false);view=next.view;selectedDate=next.day;selectedStory=null;detailHistoryOwned=false;if(next.day)await selectDay(next.day,false);if(next.story)showStory(next.story,false);if(leftMoney&&next.view==='journey')refreshAfterMoneyChange();};addEventListener('popstate',pop);addEventListener('app:unauthorized',unauthorized);return()=>{removeEventListener('popstate',pop);removeEventListener('app:unauthorized',unauthorized);};});
</script>

<svelte:head><title>Money Stories · Your journey</title></svelte:head>
{#if view==='loading'}<main class="center-page" aria-live="polite"><span class="brand-orb">₹</span><span class="spinner"></span><h1>Connecting to your dashboard…</h1><p>The service may take a few minutes to wake up.</p><button class="center-action" onclick={initialize}>Retry</button></main>
{:else if view==='login'}<main class="live-state"><h1>Your session ended</h1><a href="/portal">Sign in again</a></main>
{:else if view==='error'}<main class="live-state"><h1>Journey unavailable</h1><p>{message}</p><button onclick={initialize}>Retry</button></main>
{:else}
<header class="live-header"><strong>money stories <small>v2</small></strong><nav aria-label="Main navigation"><button class:active={view==='journey'} onclick={()=>navigate('journey')}>Journey</button><button class:active={view==='money'} onclick={()=>{focusedMoneyItem=null;navigate('money')}}>Money</button><button class:active={view==='conversation'} onclick={()=>navigate('conversation')}>Explore</button></nav><div><span>{demoMode?'Demo profile':''}</span><button onclick={logout}>Sign out</button></div></header>
{#if view==='journey'}
<main class="live-layout">
  <div class="live-heading"><div><p class="eyebrow">YOUR MONEY, DAY BY DAY</p><h1>Your journey</h1></div><label>Month <input type="month" value={month} onchange={event=>changeMonth(event.target.value)}/></label></div>
  {#if message}<p class="live-message" role="status">{message}</p>{/if}
  {#if sections.days?.freshness==='stale' || sections.projection?.freshness==='stale'}<p class="live-message">Showing an older saved read. Last update: {new Date(sections.days?.loadedAt || sections.projection?.loadedAt).toLocaleString()}</p>{/if}
  <section class="live-overview"><p class="eyebrow">MONTHLY CONTEXT</p>
    {#if sections.projection?.data?.state==='AVAILABLE' && sections.projection.data.story}<h2>{sections.projection.data.story.cardFace?.heading || 'Monthly commitments'}</h2><p>{sections.projection.data.story.cardFace?.displayValue}</p><small>Current live projection · updated {new Date(sections.projection.data.asOf).toLocaleString()}</small>
    {:else}<h2>Projection unavailable</h2><p>Historical commitments are not reconstructed from today’s plans.</p>{/if}
    {#if sections.days?.data}<p>Recorded expenses in {month}: {money(sections.days.data.days.reduce((sum,day)=>sum+Number(day.recordedExpenses || 0),0))} <small>Expense records only; this is not an account balance.</small></p>{/if}
  </section>
  <button class="live-retry" onclick={()=>loadMonth(true)}>Refresh month</button>
  {#if sections.days?.data}
    <div class="live-timeline">{#each [...sections.days.data.days].reverse() as day (day.date)}
      <section class="live-day" id={`day-${day.date}`}><button class="live-day-toggle" aria-expanded={selectedDate===day.date} onclick={()=>selectDay(day.date)}><span><strong>{dayLabel(day.date)}</strong><small>{day.recordedExpenseCount} recorded {day.recordedExpenseCount===1?'expense':'expenses'} · {money(day.recordedExpenses)}</small></span><span>↗</span></button>
      {#if selectedDate===day.date}<div class="live-day-body">
        {#each storyFor(day.date) as story (story.storyId)}<button class="live-story-preview" onclick={()=>showStory(story.storyId)}><span class="eyebrow">PUBLISHED STORY · {story.period.displayLabel}</span><strong>{story.cardFace?.heading}</strong><small>Published {new Date(story.generatedAt).toLocaleDateString()} · version {story.revision}</small></button>{/each}
        {#each dueFor(day.date) as item (item.key)}<div class="live-occurrence"><span><strong>{item.label}</strong><small>{item.status.toLowerCase()} · {item.source.replaceAll('_',' ').toLowerCase()} · {item.plannedAmount==null?'Amount unavailable':money(item.plannedAmount)} planned</small></span><div class="live-occurrence-actions"><button onclick={()=>openMoney(item)}>Review in Money</button></div></div>{/each}
        <h3>Recorded activity</h3>
        {#if activityState==='loading' && !activity.items.length}<p>Loading activity…</p>{/if}
        {#each activity.items as item (item.id)}<div class="live-activity"><span><strong>{item.merchant || item.category || 'Expense'}</strong><small>{item.originalMessage}</small></span><span>{money(item.amount)}</span><button onclick={()=>openEdit(item)}>Correct</button><button onclick={()=>removeExpense(item)} disabled={busy}>Delete</button></div>{:else}{#if activityState==='ready'}<p>No expense records on this date. Other activity may exist.</p>{/if}{/each}
        {#if activity.nextCursor}<button onclick={moreActivity} disabled={activityState==='loading'}>Load more activity</button>{/if}
        {#if activityState==='error'}<button onclick={()=>selectDay(day.date,false)}>Retry activity</button>{/if}
        <button onclick={handoff} disabled={busy}>Tell us about this date on WhatsApp</button>
      </div>{/if}</section>
    {/each}</div>
  {:else}<section class="live-state"><h2>Daily summaries unavailable</h2><p>Missing data cannot be shown as zero.</p><button onclick={()=>loadMonth(true)}>Retry</button></section>{/if}
  <section class="live-history"><h2>Published story history</h2><p>Published story versions are shown only when the server can return them. The current API provides the latest story, without past publication versions.</p>
    {#each sections.history?.data?.publications || [] as publication (publication.publicationId)}<button onclick={()=>selectedPublication=publication}><strong>{new Date(publication.publishedAt).toLocaleString()}</strong><span>{publication.latest?'Latest':'Original publication'} · {publication.stories.length} {publication.stories.length===1?'story':'stories'} · {publication.state.toLowerCase()}</span></button>{:else}<p>Earlier publications are unavailable through the current API.</p>{/each}
  </section>
</main>
{:else if view==='money'}<div class="existing-money">{#key month}<ExistingHome
  calendarSection={homeSection(sections.days)} recentSection={homeSection(sections.recent)} storiesSection={homeStoriesSection()}
  selectedMonth={month} connectionStatus="online" cacheUpdatedAt={null} {demoMode} {canUseDemoMode}
  onDemoModeChange={switchDemoMode} onMonthChange={changeMonth}
  refreshCalendar={()=>loadMonth(true)} refreshRecent={()=>loadMonth(true)} refreshStories={()=>loadMonth(true)}
  onRetryConnection={()=>loadMonth(true)} onLogout={logout} embeddedMoney
  {focusedMoneyItem} onCloseEmbeddedMoney={()=>navigate('journey')}/>{/key}</div>
{:else}<main class="live-layout"><Conversation month={month} stories={sections.stories?.data?.stories || []}/></main>{/if}
{#if editItem}<div class="live-overlay" role="presentation"><div role="dialog" tabindex="-1" aria-modal="true" aria-label="Correct expense"><h2>Correct expense</h2><label>Amount <input type="number" min="0.01" step="0.01" bind:value={editAmount}/></label><label>Effective date <input type="date" bind:value={editDate}/></label><div><button onclick={()=>editItem=null}>Cancel</button><button onclick={saveEdit} disabled={busy}>Save correction</button></div></div></div>{/if}
{#if selectedStory}<div class="live-overlay" role="presentation"><div role="dialog" tabindex="-1" aria-modal="true" aria-label="Published story"><button class="live-close" onclick={closeStory}>Close</button><p class="eyebrow">PUBLISHED {new Date(selectedStory.generatedAt).toLocaleString()}</p><h2>{selectedStory.cardFace?.heading}</h2><p>Coverage: {selectedStory.period.displayLabel} ({selectedStory.period.startDate} to {selectedStory.period.endDate})</p><p>Version {selectedStory.revision}</p>{#each [...(selectedStory.cards || [])].sort((a,b)=>a.sequence-b.sequence) as card}<article><h3>{card.title}</h3><p>{card.body}</p></article>{/each}<h3>Supporting evidence</h3>{#each selectedStory.evidence?.transactions || [] as row}<p>{row.dateLabel} · {row.merchantLabel || row.categoryLabel} · {row.amount?.displayValue}</p>{:else}<p>Evidence is unavailable in this publication.</p>{/each}<small>Story reference: {selectedStory.storyId}</small></div></div>{/if}
{#if selectedPublication}<div class="live-overlay" role="presentation"><div role="dialog" tabindex="-1" aria-modal="true" aria-label="Story publication"><button class="live-close" onclick={()=>selectedPublication=null}>Close</button><h2>{selectedPublication.latest?'Latest':'Original'} publication</h2><p>Published {new Date(selectedPublication.publishedAt).toLocaleString()}</p>{#each selectedPublication.stories as story}<button class="live-story-preview" onclick={()=>{selectedStory=normalizeStory(story);selectedPublication=null;}}><strong>{story.cardFace?.heading}</strong><small>{story.period?.displayLabel} · version {story.revision}</small></button>{/each}</div></div>{/if}
{/if}
