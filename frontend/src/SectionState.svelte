<script>
  export let section;
  export let title;
  export let retry;
</script>

{#if section.status === 'loading'}
  <article class="panel skeleton-panel" aria-label={`Loading ${title}`}><i></i><b></b><b></b></article>
{:else if section.status === 'error'}
  <article class="panel section-error" role="alert">
    <h2>{title} unavailable</h2><p>{section.error}</p>
    <button class="secondary" on:click={retry}>Try again</button>
  </article>
{:else}
  <slot></slot>
{/if}
