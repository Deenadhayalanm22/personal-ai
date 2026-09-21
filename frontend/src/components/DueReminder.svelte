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

<div class="due-reminder" data-testid={testId}>
  <span>● {detail}</span>
  {#if actionLabel}
    <span class="due-reminder-action" role="button" tabindex="0" on:click|stopPropagation={onAction} on:keydown={handleKeydown}>{actionLabel}</span>
  {/if}
</div>

<style>
  .due-reminder{display:inline-flex;width:fit-content;max-width:100%;align-items:center;justify-content:flex-start;gap:10px;margin:9px 0 0;padding:8px 10px;border:1px solid #e4a49c;border-radius:9px;background:#fff4f1;color:#a34635;font-size:9px;font-weight:700;vertical-align:top}
  .due-reminder-action{margin-left:0;padding:0;border:0;border-radius:0;background:transparent;color:inherit;font-size:9px;font-weight:800;white-space:nowrap;text-decoration:underline;text-underline-offset:3px;cursor:pointer}
  .due-reminder-action:focus-visible{outline:2px solid currentColor;outline-offset:3px;border-radius:2px}
</style>
