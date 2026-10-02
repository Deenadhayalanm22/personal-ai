<script>
  import AiCredits from './AiCredits.svelte';
  export let demoMode;
  export let canUseDemoMode;
  export let demoSwitching;
  export let demoError;
  export let loggingOut;
  export let signOutError;
  export let onNavigate;
  export let onOpenMoney;
  export let onManageNames;
  export let onDemoModeChange;
  export let onSignOut;
</script>

<section class="workspace-heading"><div><p class="micro-label">YOUR DATA, YOUR CHOICE</p><h1>You</h1><p>Manage your expense data and optional money details.</p></div></section>
<section class="settings-card"><p class="micro-label">YOUR DATA</p><button on:click={onManageNames}><span>✦</span><div><strong>Manage names</strong><small>Merge merchants, accounts and beneficiary aliases</small></div><b>Open ›</b></button><button on:click={onOpenMoney}><span>↗</span><div><strong>Optional money modules</strong><small>Goals, investing, safety and budgets</small></div><b>Manage ›</b></button></section>

{#if canUseDemoMode}<section class="settings-card"><p class="micro-label">PRESENTATION</p><label class="demo-mode-toggle"><span><strong>Demo mode</strong><small>Use a separate profile while presenting. Your real data stays hidden.</small></span><input type="checkbox" checked={demoMode} disabled={demoSwitching} on:change={onDemoModeChange}/></label>{#if demoError}<p class="form-error">{demoError}</p>{/if}</section>{/if}
{#key demoMode}<AiCredits />{/key}
<section class="settings-card"><p class="micro-label">ACCOUNT</p><button on:click={onSignOut} disabled={loggingOut}><span>↪</span><div><strong>{loggingOut?'Signing out…':'Sign out'}</strong><small>End your secure session on this device</small></div><b>›</b></button>{#if signOutError}<p class="form-error">{signOutError}</p>{/if}</section>
