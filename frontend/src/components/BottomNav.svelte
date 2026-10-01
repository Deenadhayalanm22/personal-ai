<script>
  export let view;
  export let onNavigate;
  const items = [['home','⌂','Home'],['chat','✦','Ask AI','Ask about your money'],['you','♙','You']];
</script>

<nav class="bottom-nav" aria-label="Main navigation">
  {#each items as item}<button data-nav-chat={item[0]==='chat'?true:undefined} aria-label={item[3]||item[2]} class:active={view===item[0]} class:assistant-nav={item[0]==='chat'} on:click={() => onNavigate(item[0])}><span>{item[1]}</span>{item[2]}</button>{/each}
</nav>
