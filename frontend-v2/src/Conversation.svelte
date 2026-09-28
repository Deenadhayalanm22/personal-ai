<script>
  // FIN-EPIC-007: read-only context prototype. No generated answer or financial mutation.
  export let stories=[];
  export let month;
  let selectedId='';
  let mode='summary';
  $: selected=stories.find(story=>story.storyId===selectedId) || null;
  $: if(!selectedId && stories.length) selectedId=stories[0].storyId;
</script>

<section class="conversation"><p class="eyebrow">READ-ONLY ASSISTANT CONTEXT PREVIEW</p><h1>Explore a published story</h1>
  <p>Select the story and the information you want to inspect. This view shows only the published copy and evidence supplied by the server; it does not answer new questions or record expenses.</p>
  {#if stories.length}<label>Story context <select bind:value={selectedId}>{#each stories as story}<option value={story.storyId}>{story.cardFace?.heading || story.storyType}</option>{/each}</select></label>
    {#if selected}<div class="conversation-context"><p>Coverage: {selected.period?.displayLabel || `${selected.period?.startDate} to ${selected.period?.endDate}`}</p><p>Published: {new Date(selected.generatedAt).toLocaleString()} · revision {selected.revision}</p><small>Story ID: {selected.storyId}</small></div>
      <div class="conversation-options"><button class:active={mode==='summary'} onclick={()=>mode='summary'}>What did this story say?</button><button class:active={mode==='evidence'} onclick={()=>mode='evidence'}>What supports it?</button></div>
      {#if mode==='summary'}<article><h2>{selected.cardFace?.heading}</h2>{#each [...(selected.cards || [])].sort((a,b)=>a.sequence-b.sequence) as card}<h3>{card.title}</h3><p>{card.body}</p>{/each}</article>
      {:else}<article><h2>Published evidence</h2>{#each selected.evidence?.transactions || [] as row}<p>{row.dateLabel} · {row.merchantLabel || row.categoryLabel} · {row.amount?.displayValue}</p>{:else}<p>Evidence is unavailable in this publication.</p>{/each}</article>{/if}
    {/if}
  {:else}<p>No published story context is available for {month}. Select a different month in Journey.</p>{/if}
</section>
