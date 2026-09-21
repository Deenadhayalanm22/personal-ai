<script>
  export let detail;
  export let actionLabel = '';
  export let onAction = () => {};
  export let testId = undefined;

  function handleKeydown(event) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      event.stopPropagation();
      onAction();
    }
  }
</script>

<div class="due-reminder" data-testid={testId} role="status">
  <span class="due-reminder-detail"><span aria-hidden="true">●</span> {detail}</span>
  {#if actionLabel}
    <button type="button" class="due-reminder-action" on:click|stopPropagation={onAction} on:keydown={handleKeydown}>{actionLabel}</button>
  {/if}
</div>

<style>
  .due-reminder{display:flex;width:100%;max-width:100%;align-items:center;justify-content:space-between;gap:10px;margin:10px 0 0;padding:9px 10px;border:1px solid #e4a49c;border-radius:10px;background:#fff4f1;color:#a34635;font:700 9px/1.35 'DM Sans',sans-serif;vertical-align:top}
  .due-reminder-detail{min-width:0}
  .due-reminder-action{flex:0 0 auto;margin:0;padding:0;border:0;border-radius:0;background:transparent;color:inherit;font:800 9px/1.35 'DM Sans',sans-serif;white-space:nowrap;text-decoration:underline;text-underline-offset:3px;cursor:pointer}
  .due-reminder-action:focus-visible{outline:2px solid currentColor;outline-offset:3px;border-radius:2px}
</style>
