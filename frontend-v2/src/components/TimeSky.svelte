<script>
  // FIN-EPIC-007: ambient local clock, unrelated to financial state or selected date.
  import { onMount } from 'svelte';
  let hour = new Date().getHours();
  $: phase = hour >= 5 && hour < 8 ? 'dawn'
    : hour >= 8 && hour < 17 ? 'day'
    : hour >= 17 && hour < 20 ? 'evening' : 'night';
  $: description = { dawn: 'Morning sun over the mountains', day: 'Daytime sun', evening: 'Evening sun behind the mountains', night: 'Moon and stars' }[phase];
  onMount(() => {
    const refresh = () => { hour = new Date().getHours(); };
    const timer = setInterval(refresh, 60000);
    document.addEventListener('visibilitychange', refresh);
    return () => { clearInterval(timer); document.removeEventListener('visibilitychange', refresh); };
  });
</script>
<svg class="time-sky" data-phase={phase} viewBox="0 0 80 42" role="img" aria-label={`${description} · current device time`}>
  <title>{description} · an illustration of local time, not actual sunrise or weather</title>
  <path d="M2 40a38 38 0 0 1 76 0Z" fill={phase === 'night' ? '#e2e6ea' : phase === 'evening' ? '#f3e5d5' : '#eef0df'}/>
  {#if phase === 'night'}
    <path d="M56 9a8 8 0 1 0 8 12A9 9 0 0 1 56 9Z" fill="#9caebc"/>
    <g fill="#879caf"><circle cx="22" cy="13" r="1.2"/><circle cx="35" cy="7" r="1"/><circle cx="69" cy="25" r="1"/></g>
    <path d="M19 25v4m-2-2h4M39 8v3m-1.5-1.5h3" stroke="#9aabba" stroke-width="1" stroke-linecap="round"/>
  {:else}
    <g class="sun" style:--sun-y={phase === 'day' ? '14px' : phase === 'dawn' ? '27px' : '30px'}>
      <circle cx="56" cy="0" r="7" fill={phase === 'evening' ? '#d5a274' : '#d9bf71'}/>
      <path d="M56-10v2m0 16v2M46 0h2m16 0h2M49-7l1 1m12 12 1 1M49 7l1-1m12-12 1-1" stroke={phase === 'evening' ? '#d5a274' : '#d9bf71'} stroke-width="1.3" stroke-linecap="round"/>
    </g>
  {/if}
  <path d="M3 40 22 25 41 40Z" fill={phase === 'night' ? '#b8c4c6' : '#c9d5bb'}/>
  <path d="M38 40 60 29 77 40Z" fill={phase === 'night' ? '#a4b6b7' : '#b3c6a7'}/>
  <path d="M2 40h76" stroke="#b4c2ac" stroke-width="1.2" stroke-linecap="round"/>
</svg>
<style>
  .time-sky{display:block;width:80px;height:42px}
  .sun{transform:translateY(var(--sun-y));transition:transform .6s ease-out}
  @media(prefers-reduced-motion:reduce){.sun{transition:none}}

</style>
