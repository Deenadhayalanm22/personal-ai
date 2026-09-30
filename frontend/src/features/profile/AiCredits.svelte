<!-- FIN-EPIC-003 and FIN-EPIC-004: usage-based money chat credits and super-admin grants. -->
<script>
  import { onMount, onDestroy } from 'svelte';
  import { getAiCreditPermissions, getAiCredits, getAiCreditLedger, getAiCreditUsers, grantAiCredits, setAiCreditAccess,
    getAiCreditUserLedger, getPendingAiCredits, resolveAiCredits } from '../../lib/api.js';
  let admin = false;
  let balance, ledger = [], users = [], holds = [], search = '', selected = '', amount = '', note = '';
  let error = '', success = '', busy = false, destroyed = false, grantRetry;
  let resolution = '', confirmed = '', reason = '';
  const format = value => Number(value).toLocaleString(undefined, { maximumFractionDigits: 6 });
  async function load() {
    error = ''; busy = true;
    try {
      const permissions = await getAiCreditPermissions();
      if (destroyed) return;
      admin = permissions.admin === true;
      const values = await Promise.all([getAiCredits(), admin && selected ? getAiCreditUserLedger(selected) : getAiCreditLedger(), ...(admin ? [getAiCreditUsers(search), getPendingAiCredits()] : [])]);
      if (destroyed) return;
      [balance, ledger] = values;
      if (admin) { users = values[2]; holds = values[3]; }
    } catch (cause) { if (!destroyed) error = cause.message; }
    finally { if (!destroyed) busy = false; }
  }
  async function grant() {
    busy = true; error = ''; success = '';
    const signature = JSON.stringify([selected, amount, note]);
    if (!grantRetry || grantRetry.signature !== signature) grantRetry = { signature, id: crypto.randomUUID() };
    try {
      await grantAiCredits(selected, { amount: Number(amount), note, requestId: grantRetry.id });
      if (destroyed) return;
      grantRetry = null; amount = ''; note = ''; success = 'Credits added.';
      await load();
    } catch (cause) { if (!destroyed) error = cause.message; }
    finally { if (!destroyed) busy = false; }
  }
  async function pause(user) {
    busy = true; error = ''; success = '';
    try { await setAiCreditAccess(user.id, !user.paused); if (!destroyed) await load(); }
    catch (cause) { if (!destroyed) error = cause.message; }
    finally { if (!destroyed) busy = false; }
  }
  async function inspect(user) {
    busy = true; error = '';
    try { const value = await getAiCreditUserLedger(user.id); if (!destroyed) { ledger = value; selected = String(user.id); } }
    catch (cause) { if (!destroyed) error = cause.message; }
    finally { if (!destroyed) busy = false; }
  }
  async function resolve() {
    busy = true; error = ''; success = '';
    try {
      await resolveAiCredits(resolution, Number(confirmed), reason);
      if (destroyed) return;
      resolution = ''; confirmed = ''; reason = ''; success = 'Usage reconciled; unused held credits released.'; await load();
    } catch (cause) { if (!destroyed) error = cause.message; }
    finally { if (!destroyed) busy = false; }
  }
  onMount(load);
  onDestroy(() => { destroyed = true; });
</script>
<section class="settings-card ai-credits" aria-label="AI credits">
  <h2>AI credits</h2>
  <p>Money chat uses credits based on AI usage. Longer conversations can cost more. Credits are added by your administrator and do not automatically refill.</p>
  {#if balance}<p><strong>{format(balance.available)} credits available</strong> · {format(balance.reserved)} on hold{balance.paused ? ' · Access paused' : ''}</p>{/if}
  <button on:click={load} disabled={busy}>Refresh credits</button>
  {#if admin}
    <h3>Manage friends’ credits</h3>
    <form on:submit|preventDefault={load} class="search"><label>Find user<input bind:value={search} maxlength="100" placeholder="Phone number or profile identifier" /></label><button disabled={busy}>Search</button></form>
    <p class="hint">Showing up to 100 matching profiles. Search to narrow the list.</p>
    <div class="users">
      {#each users as user (user.id)}
        <div class="user-row"><div><strong>{user.externalUserId}</strong><small>{user.channel} · {format(user.available)} available · {format(user.reserved)} held</small></div><button disabled={busy} on:click={() => inspect(user)}>Select / history</button><button disabled={busy} on:click={() => pause(user)}>{user.paused ? 'Resume' : 'Pause'}</button></div>
      {/each}
    </div>
    <form on:submit|preventDefault={grant} class="grant-form">
      <label>Grant to<select bind:value={selected} required><option value="">Choose a user</option>{#each users as user}<option value={String(user.id)}>{user.externalUserId} ({user.channel})</option>{/each}</select></label>
      <label>Credits to add<input type="number" min="0.000001" max="1000000" step="0.000001" bind:value={amount} required /></label>
      <label>Reason<input bind:value={note} maxlength="300" required /></label>
      <button disabled={busy || !selected || !amount || !note.trim()}>Add credits</button>
    </form>
    {#if holds.length}
      <h3>Usage needing review</h3>
      <p>Verify provider usage before settling a hold. Enter the confirmed cost in credits; use zero only if no usage was billed. Only reservations older than two minutes appear.</p>
      <form on:submit|preventDefault={resolve} class="grant-form">
        <label>Held request<select bind:value={resolution} required><option value="">Choose a reservation</option>{#each holds as hold}<option value={hold.id}>{hold.externalUserId} · {format(hold.reserved)} credits held · {hold.id}</option>{/each}</select></label>
        <label>Confirmed credit cost<input type="number" min="0" max={holds.find(hold => hold.id === resolution)?.reserved || 0} step="0.000001" bind:value={confirmed} required /></label>
        <label>Verification note<input bind:value={reason} maxlength="300" required /></label>
        <button disabled={busy || !resolution || confirmed === '' || !reason.trim()}>Settle verified usage</button>
      </form>
    {/if}
  {/if}
  {#if error}<p role="alert" class="error">{error}</p>{/if}
  {#if success}<p role="status">{success}</p>{/if}
  <details><summary>Recent credit activity{admin && selected ? ' for selected user' : ''}</summary>
    {#if !ledger.length}<p>No credit activity yet.</p>{/if}
    {#each ledger as entry}<div class="entry"><span>{entry.kind} · {entry.note}</span><strong>{format(entry.amount)}</strong></div>{/each}
  </details>
</section>
<style>
  .ai-credits{margin:20px 0;padding:20px;border:1px solid #d9e2d9;border-radius:16px;background:#fffefb;color:#233b30}.ai-credits h2{font-size:20px;margin-top:0}.ai-credits h3{font-size:16px;margin-top:24px}.ai-credits p{font-size:13px;line-height:1.6}.ai-credits button{width:auto;display:inline-block;border:1px solid #a6bba9;background:#eef4ed;color:#234c3c;border-radius:8px;padding:8px 12px;cursor:pointer;font:inherit;font-size:12px}.ai-credits button:disabled{opacity:.5;cursor:default}.ai-credits label{display:grid;gap:6px;font-size:13px;justify-content:stretch;align-items:stretch;padding:0;border:0;font-weight:500}.ai-credits input,.ai-credits select{min-width:0;width:100%;height:auto;min-height:40px;box-sizing:border-box;padding:9px;border:1px solid #bacbbd;border-radius:8px;background:white;font:inherit}.grant-form{display:grid;gap:12px;margin:18px 0}.search{display:flex;gap:10px;align-items:end}.search label{flex:1;min-width:0}.users{max-height:280px;overflow:auto}.user-row{display:flex;align-items:center;gap:8px;padding:10px 0;border-bottom:1px solid #e4ebe3;flex-wrap:wrap}.user-row>div{flex:1;min-width:140px;overflow-wrap:anywhere}.user-row small{display:block;color:#657468;font-size:11px}.entry{display:flex;justify-content:space-between;gap:15px;font-size:12px;padding:9px 0;border-bottom:1px solid #e4ebe3}.hint{color:#657468}.error{color:#975336}summary{cursor:pointer;margin-top:18px;font-size:13px}
</style>
