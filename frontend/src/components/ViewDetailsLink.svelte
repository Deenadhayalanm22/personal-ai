<script>
  export let onAction = () => {};
  export let label = 'View details';
  export let status = null;

  function handleKeydown(event) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      event.stopPropagation();
      onAction();
    }
  }
</script>

<span
  class="view-details-link"
  class:with-status={Boolean(status)}
  role="button"
  tabindex="0"
  aria-label={status ? `${label}, ${status}` : label}
  on:click|stopPropagation={onAction}
  on:keydown={handleKeydown}
><span class="view-details-label">View details <span aria-hidden="true">→</span></span>{#if status}<strong class="view-details-status">{status}</strong>{/if}</span>

<style>
  .view-details-link {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    margin: 0;
    padding: 0;
    border: 0;
    background: transparent;
    color: var(--green);
    font: 800 10px/1.2 'DM Sans', sans-serif;
    white-space: nowrap;
    cursor: pointer;
  }

  .view-details-link:hover { text-decoration: underline; text-underline-offset: 3px; }
  .view-details-link:focus-visible { outline: 2px solid #4e9d8d; outline-offset: 3px; border-radius: 4px; }
  .view-details-label{display:inline-flex;align-items:center;justify-content:flex-end;gap:3px;width:100%}
  .view-details-link.with-status{display:flex;flex-direction:column;align-items:stretch;gap:8px}
  .view-details-status{display:block;text-align:left;font:800 10px/1.2 'DM Sans',sans-serif}
</style>
