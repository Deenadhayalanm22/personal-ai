<script>
  // FIN-EPIC-007: one decorative visual; all meaning is carried in the summary text.
  export let day;
</script>
<svg class="landmark" viewBox="0 0 140 140" aria-hidden="true">
  <ellipse cx="70" cy="121" rx="49" ry="7" fill="#dbe3cd"/>
  {#if day.kind==='tree'}
    {#if day.tree===1}
      <path d="M70 120V69" stroke="#8c8158" stroke-width="5" stroke-linecap="round"/>
      <path d="M70 96C39 93 37 74 42 69 65 72 71 84 70 96M71 81C73 56 91 49 100 52 100 72 87 81 71 81" fill="#97b57e" stroke="#718e5f" stroke-width="2"/>
    {:else}
      <path d="M70 119V57M70 92 45 66M70 77 89 53" fill="none" stroke="#93815d" stroke-width="6" stroke-linecap="round"/>
      <g fill="#93b17c" stroke="#829f6d" stroke-width="1.5"><circle cx="46" cy="65" r="23"/><circle cx="89" cy="54" r="25"/><circle cx="65" cy="40" r="24"/><circle cx="71" cy="68" r="23"/></g>
      <g fill="#b4ca96"><circle cx="55" cy="35" r="5"/><circle cx="96" cy="53" r="4"/></g>
      <g class="bird"><ellipse cx="92" cy="26" rx="7" ry="5" fill="#c59c54"/><circle cx="97" cy="22" r="4" fill="#668a8b"/><path d="m86 26-7-4 3 6m19-6h4" fill="none" stroke="#668a8b" stroke-width="2"/></g>
    {/if}
  {:else if day.kind==='bridge'}
    <path d="M0 107Q35 94 70 105T140 104V123H0Z" fill="#c1d8d7"/>
    <path d="M17 83 120 70 127 85 21 99Z" fill="#a9b3a0"/><path d="M25 99V119M109 88V113" stroke="#909e89" stroke-width="12"/>
    {#each Array(day.bridge) as _, i}<path d="M0 0h31v15H0Z" transform="translate({21+i*34} {77-i*4})" fill={i===day.bridge-1?'#d9bb7f':'#e0ddc6'} stroke="#9b9f87" stroke-width="2"/>{/each}
    <path d="M91 69h29v14H91Z" fill="none" stroke="#9b9f87" stroke-dasharray="3 4"/>
  {:else if day.kind==='shelter'}
    <path d="M31 68V119H109V68M23 69 70 29 117 69" fill="#e5dbc0" stroke="#a18c63" stroke-width="3" stroke-linejoin="round"/>
    <path d="M23 69 70 29 77 35 36 72Z" fill="#c9a66a"/><path d="M51 117V78H88V117" fill="#b4c49e" stroke="#a18c63" stroke-width="3"/>
    <path d="m78 36 33 34m-21-23 5 23" stroke="#b9ac8a" stroke-width="2"/>
  {/if}
</svg>
