<script>
  export let onAction = () => {};
  export let label = 'View details';

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
  role="button"
  tabindex="0"
  aria-label={label}
  on:click|stopPropagation={onAction}
  on:keydown={handleKeydown}
>View details <span aria-hidden="true">→</span></span>

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
</style>
