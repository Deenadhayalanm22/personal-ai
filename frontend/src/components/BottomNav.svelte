<script>
  export let view;
  export let onNavigate;
  const items = [['home','⌂','Home'],['transactions','▤','Transactions'],['stories','▱','Stories'],['you','♙','You']];
</script>

<nav class="bottom-nav" aria-label="Main navigation">
  {#each items as item}<button class:active={view===item[0]} on:click={() => onNavigate(item[0])}><span>{item[1]}</span>{item[2]}</button>{/each}
</nav>
