<script>
  export let connectionStatus;
  export let connectionLabel;
  export let onRetry;
  export let onHome;
  export let onProfile;
</script>

<header class="portal-topbar">
  <button class="wordmark" on:click={onHome}><span>₹</span>Money Stories</button>
  <div class="topbar-actions">
    <button class:online={connectionStatus==='online'} class:offline={connectionStatus==='offline'} class="connection-status" on:click={onRetry} aria-label={`Connection status: ${connectionLabel}. Check again.`}><i></i>{connectionLabel}</button>
    <button class="profile-button" on:click={onProfile} aria-label="Open your profile">D</button>
  </div>
</header>
