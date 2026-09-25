<script>
  export let occurrences = [];
  export let selectedDate = '';
  export let onSelect = () => {};
  export let compact = false;
  const label = date => new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' }).format(new Date(`${date}T12:00:00`));
  $: paid = occurrences.filter(item => item.status === 'COMPLETED').length;
  $: skipped = occurrences.filter(item => item.status === 'SKIPPED').length;
</script>

<div class:compact class="cadence-progress" data-testid="cadence-progress">
  <div class="cadence-progress-caption"><strong>{paid} of {occurrences.length} paid</strong>{#if skipped}<span>{skipped} skipped</span>{/if}</div>
  <div class="cadence-progress-scroll">
    <div class="cadence-progress-segments" style={`--cadence-count:${occurrences.length}`}>
      {#each occurrences as occurrence}
        <button type="button" class="cadence-segment" class:paid={occurrence.status==='COMPLETED'} class:skipped={occurrence.status==='SKIPPED'} class:due={occurrence.status==='DUE'} class:selected={selectedDate===occurrence.dueDate} aria-label={`${label(occurrence.dueDate)} ${occurrence.status==='COMPLETED'?'paid':occurrence.status==='SKIPPED'?'skipped':occurrence.status==='DUE'?'due':'upcoming'}`} aria-pressed={selectedDate===occurrence.dueDate} title={`${label(occurrence.dueDate)} · ${occurrence.status.toLowerCase()}`} on:click={()=>onSelect(occurrence.dueDate)}></button>
      {/each}
    </div>
  </div>
  {#if !compact}<div class="cadence-progress-legend"><span><i class="paid"></i>Paid</span><span><i class="skipped"></i>Skipped</span><span><i class="due"></i>Due</span><span><i class="upcoming"></i>Upcoming</span></div>{/if}
</div>

<style>
  .cadence-progress{width:100%;min-width:0}
  .cadence-progress-caption{display:flex;gap:10px;margin:0 0 9px;color:#526159;font-size:10px}
  .cadence-progress-caption strong{color:#25332b;font-weight:800}
  .cadence-progress-scroll{max-width:100%;overflow-x:auto;padding:3px 2px}
  .cadence-progress-segments{display:grid;grid-template-columns:repeat(var(--cadence-count),minmax(10px,1fr));gap:4px;min-width:max(100%,calc(var(--cadence-count) * 15px))}
  .cadence-segment{height:16px;padding:0;border:0;border-radius:4px;background:#dce5de;cursor:pointer}
  .cadence-segment.paid,.cadence-progress-legend i.paid{background:#3a9664}
  .cadence-segment.skipped,.cadence-progress-legend i.skipped{background:#cc6254}
  .cadence-segment.due,.cadence-progress-legend i.due{background:#e2a48b}
  .cadence-segment.selected{outline:2px solid #254e3b;outline-offset:2px}
  .cadence-segment:focus-visible{outline:2px solid #254e3b;outline-offset:2px}
  .cadence-progress-legend{display:flex;flex-wrap:wrap;gap:10px;margin-top:8px;color:#67776b;font-size:9px}
  .cadence-progress-legend span{display:inline-flex;align-items:center;gap:4px}
  .cadence-progress-legend i{width:7px;height:7px;border-radius:2px;background:#dce5de}
  .compact .cadence-progress-caption{margin-bottom:6px;font-size:9px}
  .compact .cadence-segment{height:12px}
</style>
