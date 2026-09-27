<script>
  // FIN-EPIC-007 — Stage 1 sample journey; no API integration or real writes.
  import Landscape from './components/Landscape.svelte';
  import TellUs from './components/TellUs.svelte';
  import TimeSky from './components/TimeSky.svelte';
  import { days, money, dayLabel, weekday } from './lib/journey.js';
  let selected = days.length - 1;
  let view = 'journey';
  let dialog;
  let modalTitle = '';
  let modalBody = '';
  let touchStart;
  let dateError = '';
  let additions = {};
  $: originalDay = days[selected];
  $: added = additions[originalDay.date] || [];
  $: day = { ...originalDay, activity: [...originalDay.activity, ...added.map(row => [row.label, row.amount, 'Added in this preview'])] };
  $: if (originalDay.kind === 'quiet' && added.length) {
    day = { ...day, title: 'New sample expenses added', body: 'Your sample activity is below. No AI story has been generated.', amount: added.reduce((sum, row) => sum + row.amount, 0), amountLabel: 'Sample expenses added', detail: 'These are locally confirmed sample expenses. They disappear on reload. No AI story or real financial record was created.' };
  }
  function addSamples(date, rows) { additions = { ...additions, [date]: [...(additions[date] || []), ...rows] }; }

  function move(delta) { selected = Math.max(0, Math.min(days.length - 1, selected + delta)); dateError = ''; }
  function open(title, body) { modalTitle = title; modalBody = body; dialog.showModal(); }
  function jump(event) { const index = days.findIndex(d => d.date === event.target.value); if(index>=0) { selected=index; dateError=''; } else { dateError='Choose a sample date from 15–21 September 2026.'; } }
  function keyTravel(event) { if(event.target instanceof HTMLInputElement) return; if(event.key==='ArrowLeft'||event.key==='ArrowRight') { event.preventDefault(); move(event.key==='ArrowLeft'?-1:1); } }
  function endTouch(event) { if(!touchStart) return; const dx=event.changedTouches[0].clientX-touchStart.x, dy=event.changedTouches[0].clientY-touchStart.y; if(Math.abs(dx)>55 && Math.abs(dx)>Math.abs(dy)*1.5) move(dx<0?1:-1); touchStart=null; }
</script>
<svelte:head><title>Money Stories · Your journey</title></svelte:head>
<div class="demo-banner"><span class="status-dot"></span> UI V2 PREVIEW <span class="banner-divider">/</span> Stage 01 · Sample data only</div>
<header class="app-header"><button class="brand" onclick={()=>view='journey'}><span class="brand-mark">m<span>•</span></span>money stories<span class="version">v2</span></button><button class="avatar" aria-label="About this preview" onclick={()=>open('Your private preview','Stage 1 uses fictional sample data. No account is connected, and no financial records are read or changed. The original application is separate and unchanged.')}>D</button></header>
<main>
{#if view==='journey'}
  <section class="journey-heading"><div><p class="eyebrow">YOUR MONEY, DAY BY DAY</p><h1>A story in every step.</h1></div><span class="month-label">September 2026</span></section>
  <section class="journey-panel" aria-label="Journey date navigation">
    <div class="scene" ontouchstart={(e)=>touchStart={x:e.touches[0].clientX,y:e.touches[0].clientY}} ontouchend={endTouch} ontouchcancel={()=>touchStart=null}>
      <div class="road-line" aria-hidden="true"></div><div class="traveller-world" style:left={`${8+selected*14}%`}><TimeSky/><div class="traveller" aria-hidden="true"><svg viewBox="0 0 30 42"><circle cx="15" cy="7" r="5" fill="#47684c"/><path d="M14 15 11 27 6 37M12 26 22 37M14 16 23 23M13 17 5 24" fill="none" stroke="#47684c" stroke-width="3.5" stroke-linecap="round"/><path d="M9 13h10v14H9Z" fill="#bc9b67"/></svg></div></div>
      <button class="scene-arrow previous" aria-label="Previous day" disabled={selected===0} onclick={()=>move(-1)}>←</button><button class="scene-arrow next" aria-label="Next day" disabled={selected===days.length-1} onclick={()=>move(1)}>→</button>
    </div>
    <div class="date-rail" aria-label="Sample days">{#each days as item,i}<button onkeydown={keyTravel} aria-label={dayLabel(item.date)} aria-pressed={selected===i} class:selected={selected===i} onclick={()=>{selected=i;dateError='';}}><span>{weekday(item.date).slice(0,3)}</span><strong>{item.date.slice(-2)}</strong><i class:has-event={item.activity.length>0}></i></button>{/each}</div>
    <div class="travel-tools"><div><button onclick={()=>{selected=days.length-1;dateError='';}}>Latest demo day</button><label class="date-jump">Jump to date <input aria-label="Jump to date" type="date" min={days[0].date} max={days.at(-1).date} value={day.date} onchange={jump}/></label></div></div>
    {#if dateError}<p class="date-error" role="alert">{dateError}</p>{/if}
  </section>
  <section class="chapter-content" aria-live="polite" aria-atomic="true">
    <div class="day-heading"><div><p class="eyebrow">{selected===days.length-1?'LATEST DEMO DAY':'EARLIER IN YOUR JOURNEY'}</p><h2>{dayLabel(day.date)} <span>{weekday(day.date)}</span></h2></div><span class="chapter-count">{String(selected+1).padStart(2,'0')} / 07</span></div>
    <div class="content-grid"><div class="story-column"><article class="story-card"><div class="story-copy"><p class="eyebrow">{day.kind==='quiet' && !added.length?'A QUIET DAY':'WHAT CHANGED'}</p><h3>{day.title}</h3>{#if day.amount!==null}<div class="story-value"><strong>{money(day.amount)}</strong><span>{day.amountLabel}</span></div>{/if}<p class="story-body">{day.body}</p>{#if day.activity.length}<button class="text-action" onclick={()=>open(day.title,day.detail)}>View supporting details <span aria-hidden="true">↗</span></button>{/if}</div>{#if ['tree','bridge','shelter'].includes(day.kind)}<div class="story-illustration" aria-hidden="true"><Landscape {day}/></div>{/if}</article>
      {#if added.length && originalDay.kind !== 'quiet'}<p class="sample-update" role="status">{added.length} new sample expense{added.length===1?'':'s'} in activity. The story above is unchanged.</p>{/if}
      {#key day.date}<TellUs date={day.date} latest={selected===days.length-1} onSave={addSamples}/>{/key}
      </div><aside class="activity-card"><div class="activity-heading"><h3>Recorded activity</h3><span>{day.activity.length.toString().padStart(2,'0')}</span></div>{#if day.activity.length}{#each day.activity as activity}<button class="activity-row" onclick={()=>open(activity[0],`${money(activity[1])} · ${dayLabel(day.date)}. ${activity[2]}. ${activity[2]==='Added in this preview'?'Confirmed in this local demo only. This entry disappears on reload.':day.detail}`)}><span class="activity-icon">{day.kind==='tree'?'♧':day.kind==='bridge'?'⌁':day.kind==='shelter'?'⌂':'↗'}</span><span><strong>{activity[0]}</strong><small>{activity[2]}</small></span><b>{money(activity[1])}</b></button>{/each}{:else}<p class="quiet-note">No activity recorded.<br/>Your journey is here when you need it.</p>{/if}</aside></div>
  </section>

{:else}
  <section class="money-page"><p class="eyebrow">THE TOOLS BEHIND YOUR JOURNEY</p><h1>Your money, in one place.</h1><p class="intro">The journey stays simple. The details live here.</p><div class="tools-grid">{#each [['Transactions','Search and correct your recorded activity','Stage 10'],['Commitments','Upcoming payments and money set aside','Stage 11'],['Loans','Your recorded repayment history','Stage 12'],['Investments','Contributions and investment details','Stage 13']] as tool}<button onclick={()=>open(tool[0],`${tool[2]} will connect this tool to the existing application flows. This is a sample-data preview; no real records are available here yet.`)}><span class="eyebrow">PREVIEW</span><h2>{tool[0]} ↗</h2><p>{tool[1]}</p></button>{/each}</div><button class="text-action" onclick={()=>view='journey'}>← Back to your journey</button></section>
{/if}
<footer class="page-footer">Your history. At your pace.<span>UI v2 · Stage 01 / 15</span></footer>
</main>
<nav class="bottom-nav" aria-label="Main navigation"><button class:active={view==='journey'} aria-current={view==='journey'?'page':undefined} onclick={()=>view='journey'}><span aria-hidden="true">⌁</span>Journey</button><button class:active={view==='money'} aria-current={view==='money'?'page':undefined} onclick={()=>view='money'}><span aria-hidden="true">▥</span>Money</button></nav>
<dialog bind:this={dialog} aria-labelledby="detail-title"><div class="dialog-top"><span class="eyebrow">SAMPLE CHAPTER</span><button aria-label="Close details" onclick={()=>dialog.close()}>×</button></div><h2 id="detail-title">{modalTitle}</h2><p>{modalBody}</p><button class="dialog-done" onclick={()=>dialog.close()}>Back to the journey</button></dialog>
