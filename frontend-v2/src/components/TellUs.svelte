<script>
  // FIN-EPIC-007: preview -> explicit confirmation -> in-memory sample activity only.
  import { previewExpenses } from '../lib/sampleCapture.js';
  import { dayLabel } from '../lib/journey.js';
  export let date;
  export let latest = false;
  export let onSave;
  let message = '';
  let error = '';
  let rows = [];
  let saved = '';
  let confirmation;
  const format = n => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(n);
  $: total = rows.reduce((sum, row) => sum + Math.round(Number(row.amount) * 100), 0) / 100;
  function preview(event) {
    event.preventDefault(); saved = ''; error = '';
    const result = previewExpenses(message);
    if (result.error) { error = result.error; return; }
    rows = result.rows; confirmation.showModal();
  }
  function save(event) {
    event.preventDefault();
    const confirmed = rows.map(row => ({ label: row.label.trim(), amount: Number(row.amount) }));
    if (!confirmed.length || confirmed.some(row => !row.label || row.label.length > 50 || !Number.isFinite(row.amount) || row.amount <= 0 || row.amount > 10000000 || Math.abs(row.amount * 100 - Math.round(row.amount * 100)) > 0.000001)) return;
    onSave(date, confirmed);
    saved = `${confirmed.length} sample expense${confirmed.length === 1 ? '' : 's'} added to ${dayLabel(date)}. Only this preview is updated; reload clears these entries.`;
    rows = []; message = ''; confirmation.close();
  }
</script>
<section class="tell-us" aria-labelledby="tell-title">
  <h3 id="tell-title">{latest ? 'Anything to add to today’s story?' : `Add something for ${dayLabel(date)}`}</h3>
  <p class="capture-context">{latest ? `Demo today · ${dayLabel(date)} 2026` : `${dayLabel(date)} 2026`} · Review before adding.</p>
  <form onsubmit={preview}>
    <label class="sr-only" for="day-message">Tell us about your spending</label>
    <div class="capture-input"><textarea id="day-message" bind:value={message} maxlength="500" rows="2" placeholder="Lunch ₹180 and Auto ₹90…" aria-describedby="capture-demo" required></textarea><button type="submit">Tell us <span aria-hidden="true">↗</span></button></div>
  </form>
  <p id="capture-demo" class="capture-demo">Demo only · Simple expense examples, no AI connected. Nothing is saved to your account.</p>
  {#if error}<p class="capture-error" role="alert">{error}</p>{/if}
  {#if saved}<p class="capture-success" role="status">{saved}</p>{/if}
</section>
<dialog bind:this={confirmation} aria-labelledby="capture-title">
  <div class="dialog-top"><span class="eyebrow">REVIEW SAMPLE EXPENSES</span><button aria-label="Cancel expense preview" onclick={()=>confirmation.close()}>×</button></div>
  <h2 id="capture-title">Does this look right?</h2>
  <p class="capture-date">Add to <strong>{dayLabel(date)} 2026</strong></p>
  <form onsubmit={save}>
    <div class="expense-previews">{#each rows as row,i}<div class="expense-preview"><label>Description {i+1}<input aria-label={`Expense ${i+1} description`} bind:value={row.label} maxlength="50" pattern=".*\S.*" required/></label><label>Amount (₹)<input aria-label={`Expense ${i+1} amount`} type="number" bind:value={row.amount} min="0.01" max="10000000" step="0.01" required/></label></div>{/each}</div>
    <div class="capture-total"><span>Total</span><strong>{Number.isFinite(total) ? format(total) : 'Check amounts'}</strong></div>
    <p class="capture-demo">Local demo only. These entries disappear on reload. The existing story is not regenerated.</p>
    <button class="dialog-done" type="submit" disabled={!rows.length}>Add {rows.length} sample expense{rows.length===1?'':'s'}</button>
    <button class="capture-back" type="button" onclick={()=>confirmation.close()}>Back to message</button>
  </form>
</dialog>
