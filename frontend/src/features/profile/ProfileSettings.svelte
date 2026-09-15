<script>
  export let demoMode;
  export let demoSwitching;
  export let demoError;
  export let loggingOut;
  export let signOutError;
  export let storyControls;
  export let onNavigate;
  export let onOpenMoney;
  export let onDemoModeChange;
  export let onSignOut;
</script>

<section class="workspace-heading"><div><p class="micro-label">YOUR DATA, YOUR CHOICE</p><h1>You</h1><p>Control the information that informs your stories.</p></div></section>
<section class="settings-card"><p class="micro-label">YOUR DATA</p><button on:click={() => onNavigate('transactions')}><span>▤</span><div><strong>Expense capture</strong><small>AI-captured expenses and cleanup</small></div><b>On ›</b></button><button on:click={onOpenMoney}><span>↗</span><div><strong>Optional money modules</strong><small>Goals, investing, safety and budgets</small></div><b>Manage ›</b></button></section>
<section class="settings-card"><p class="micro-label">STORY CONTROLS</p>{#each [['spending','Use spending in stories'],['investments','Use investments in stories'],['planning','Use goals and budgets in stories']] as control}<label><span>{control[1]}</span><input type="checkbox" bind:checked={storyControls[control[0]]}/></label>{/each}</section>
<section class="settings-card"><p class="micro-label">PRESENTATION</p><label class="demo-mode-toggle"><span><strong>Demo mode</strong><small>Use a separate profile while presenting. Your real data stays hidden.</small></span><input type="checkbox" checked={demoMode} disabled={demoSwitching} on:change={onDemoModeChange}/></label>{#if demoError}<p class="form-error">{demoError}</p>{/if}</section>
<section class="settings-card"><p class="micro-label">ACCOUNT</p><button on:click={onSignOut} disabled={loggingOut}><span>↪</span><div><strong>{loggingOut?'Signing out…':'Sign out'}</strong><small>End your secure session on this device</small></div><b>›</b></button>{#if signOutError}<p class="form-error">{signOutError}</p>{/if}</section>
