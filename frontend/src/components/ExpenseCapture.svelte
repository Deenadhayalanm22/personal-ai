<!-- FIN-EPIC-001, FIN-EPIC-003: selected-date manual and optional conversational capture with explicit server preview. -->
<script>
  import { onMount, onDestroy, tick } from 'svelte';
  import { getExpenseOptions, prepareManualExpense, getAiCredits, prepareExpenseCapture, confirmExpenseCapture, cancelExpenseCapture } from '../lib/api.js';
  import VoiceQuestion from './VoiceQuestion.svelte';
  export let date;
  export let connectionStatus = 'checking';
  export let onExit = () => {};
  export let onRecorded = () => {};
  export let onViewExpense = () => {};
  let mode = 'manual', options = null, optionsError = '', amount = '', entryDate = date, category = '', subcategory = '', merchant = '', account = '', manualRetry = null;
  $: subcategories = options?.categories?.find(item=>item.name===category)?.subcategories || [];
  let question = '', turns = [], messages = [], preview = null, extractionId = null;
  let pending = false, error = '', recordedDate = null, input, previewElement, controller, destroyed = false;
  let credits = null, retry = null, voiceBusy = false, voiceReview = false, beforeVoice = '', voiceControl;
  $: reusableRetry = retry?.signature === JSON.stringify({date,message:question.trim(),turns});
  $: blocked = !credits || credits.available <= 0 || credits.paused || !credits.enabled || !credits.configured;
  const label = value => new Date(`${value}T12:00:00`).toLocaleDateString(undefined,{day:'numeric',month:'long',year:'numeric'});
  async function loadOptions() {
    optionsError='';
    try { const value=await getExpenseOptions(); if (!destroyed) options=value; }
    catch (cause) { if (!destroyed) optionsError=cause.message || 'Could not load expense choices.'; }
  }
  function chooseMode(value) {
    mode=value; error='';
    if (mode==='ai') refreshCredits();
    tick().then(()=>input?.focus());
  }
  async function prepareManual() {
    if (pending || !options || connectionStatus!=='online') return;
    const body={date:entryDate,amount,category,subcategory,merchant:merchant.trim() || null,account:account.trim() || null};
    const signature=JSON.stringify(body);
    if (!manualRetry || manualRetry.signature!==signature) manualRetry={signature,id:crypto.randomUUID()};
    await run(async signal=>{
      const response=await prepareManualExpense({...body,requestId:manualRetry.id},signal);
      if (destroyed) return;
      preview=response.preview;extractionId=response.extractionId;
      await tick();previewElement?.scrollIntoView({block:'center',behavior:'instant'});previewElement?.focus({preventScroll:true});
    });
  }
  async function refreshCredits() {
    try { const value = await getAiCredits(); if (!destroyed) credits = value; }
    catch (cause) { if (!destroyed) { credits=null;if (mode==='ai') error = cause.message || 'Could not load AI credits.'; } }
  }
  function reviewVoice(text) {
    beforeVoice = question;
    const combined = [question.trim(),text].filter(Boolean).join('\n');
    if (combined.length > 2000) { error = 'Shorten your description to 2,000 characters.'; return; }
    question = combined; voiceReview = true; tick().then(()=>input?.focus());
  }
  async function run(action) {
    pending = true; error = ''; controller = new AbortController();
    const timer = setTimeout(()=>controller?.abort(),95000);
    try { await action(controller.signal); }
    catch (cause) { if (!destroyed) { if (!extractionId && cause.status && cause.data?.code!=='AI_USAGE_PENDING') retry=null; error = cause.name==='AbortError'?'That took too long. Retry to check the same request.':cause.message || 'Could not complete this request.'; } }
    finally { clearTimeout(timer); if (!destroyed) { pending=false; if (mode==='ai') refreshCredits(); await tick(); input?.focus(); } }
  }
  async function send() {
    const message = question.trim();
    if (!message || pending || voiceBusy || extractionId || recordedDate || connectionStatus!=='online') return;
    const signature = JSON.stringify({date,message,turns});
    if (blocked && retry?.signature!==signature) return;
    if (!retry || retry.signature!==signature) retry = {signature,id:crypto.randomUUID()};
    await run(async signal => {
      const response = await prepareExpenseCapture({date,message,turns,requestId:retry.id},signal);
      if (destroyed) return;
      turns = [...turns,message];
      messages = [...messages,{role:'user',text:message},{role:'assistant',text:response.answer}];
      preview = response.preview; extractionId=response.extractionId; question='';retry=null;voiceReview=false;
      await tick(); if (!destroyed && extractionId) { previewElement?.scrollIntoView({block:'center',behavior:'instant'}); previewElement?.focus({preventScroll:true}); }
    });
  }
  async function edit() {
    if (pending) return;
    await run(async signal => {
      await cancelExpenseCapture(extractionId,signal);
      if (destroyed) return;
      extractionId=null;preview=null;manualRetry=null;
      messages=[...messages,{role:'assistant',text:'Tell me what to change. I’ll prepare a new preview.'}];
    });
  }
  async function record() {
    if (pending || connectionStatus!=='online') return;
    await run(async signal => {
      const result = await confirmExpenseCapture(extractionId,signal);
      if (destroyed) return;
      recordedDate = result.date;
      // Confirmation has succeeded even if refreshing a dashboard fails.
      try { await onRecorded(result.date); } catch { if (!destroyed) error='Expense recorded. Refresh the dashboard to see it.'; }
    });
  }
  async function cancel() {
    if (pending || voiceBusy) return;
    if (extractionId && !recordedDate) {
      await run(async signal => { await cancelExpenseCapture(extractionId,signal); if (!destroyed) onExit(); });
    } else onExit();
  }
  onMount(()=>{loadOptions();tick().then(()=>input?.focus());});
  onDestroy(()=>{destroyed=true;controller?.abort();voiceControl?.cancel();});
</script>

<section class="capture" aria-label="Add expense conversation">
  <header><div><p class="eyebrow">ADD EXPENSE</p><h2>{recordedDate?'Expense recorded':'Add an expense'}</h2></div><button aria-label="Close expense capture" on:click={cancel} disabled={pending || voiceBusy}>×</button></header>
  <p class="date-context">Adding an expense for <strong>{label(date)}</strong></p>
  {#if !recordedDate}
    {#if !extractionId}
      <div class="mode-choice" aria-label="Expense entry method">
        <button aria-pressed={mode==='manual'} on:click={()=>chooseMode('manual')} disabled={pending || voiceBusy}>Enter manually</button>
        <button aria-pressed={mode==='ai'} on:click={()=>chooseMode('ai')} disabled={pending || voiceBusy || connectionStatus!=='online'}>Describe with AI</button>
      </div>
    {/if}
    {#if mode==='ai'}
    <p class="intro">Tell me what you paid for. I’ll check your saved names, ask for any missing details, and show a preview before recording.</p>
    {#if credits}<p class="credits">Available AI credits: {Number(credits.available).toLocaleString()} <button on:click={refreshCredits}>Refresh</button></p>{/if}
    {#if blocked}<p class="notice">AI capture needs available credits and enabled AI access. You can enter this expense manually without credits.</p>{/if}
    <div role="log" aria-live="polite">{#each messages as message}<article class:user={message.role==='user'}><strong>{message.role==='user'?'You':'Money assistant'}</strong><p>{message.text}</p></article>{/each}</div>
    {/if}
    {#if extractionId && preview}
      <section class="preview" aria-label="Expense preview" tabindex="-1" bind:this={previewElement}><h3>Review expense</h3><dl>
        <div><dt>Amount</dt><dd>{new Intl.NumberFormat(undefined,{style:'currency',currency:preview.currency || 'INR'}).format(Number(preview.amount))}</dd></div>
        <div><dt>Date</dt><dd>{label(preview.date)}</dd></div>
        <div><dt>Merchant</dt><dd>{preview.merchant || 'Not specified'}</dd></div>
        <div><dt>Category</dt><dd>{preview.category} · {preview.subcategory}</dd></div>
        <div><dt>Account</dt><dd>{preview.account || 'Not specified'}</dd></div>
      </dl><div class="actions"><button class="primary" on:click={record} disabled={pending || connectionStatus!=='online'}>Record expense</button><button on:click={edit} disabled={pending}>Edit details</button><button on:click={cancel} disabled={pending}>Cancel</button></div></section>
    {:else if mode==='manual'}
      <p class="intro">Enter the details yourself. No AI credits needed. Merchant and account are optional.</p>
      {#if optionsError}<p role="alert">{optionsError} <button on:click={loadOptions}>Retry choices</button></p>{/if}
      <form class="manual-form" on:submit|preventDefault={prepareManual}>
        <label>Amount<input type="number" bind:this={input} bind:value={amount} min="0.01" step="0.01" required disabled={pending}/></label>
        <label>Date<input type="date" bind:value={entryDate} required disabled={pending}/></label>
        <div><label for="capture-category">Category</label><select id="capture-category" bind:value={category} required disabled={pending || !options} on:change={()=>subcategory=''}><option value="">Choose category</option>{#each options?.categories || [] as item}<option value={item.name}>{item.name}</option>{/each}</select></div>
        <div><label for="capture-subcategory">Subcategory</label><select id="capture-subcategory" bind:value={subcategory} required disabled={pending || !category}><option value="">Choose subcategory</option>{#each subcategories as name}<option value={name}>{name}</option>{/each}</select></div>
        <label>Merchant (optional)<input list="capture-merchants" bind:value={merchant} maxlength="200" placeholder="Saved or new name" disabled={pending}/></label>
        <datalist id="capture-merchants">{#each options?.merchants || [] as item}<option value={item.name}/>{/each}</datalist>
        <label>Account (optional)<input list="capture-accounts" bind:value={account} maxlength="200" placeholder="Saved or new name" disabled={pending}/></label>
        <datalist id="capture-accounts">{#each options?.accounts || [] as item}<option value={item.name}/>{/each}</datalist>
        <button class="primary" type="submit" disabled={pending || !options || connectionStatus!=='online'}>{pending?'Preparing…':'Review expense'}</button>
        <small>Nothing is recorded until you confirm the preview.</small>
      </form>
    {:else}
      <form on:submit|preventDefault={send}>
        <label for="capture-description">{turns.length?'Your reply':'Expense description'}</label>
        {#if voiceReview}<p>Review your voice text below. <button type="button" on:click={()=>{question=beforeVoice;voiceReview=false}}>Discard voice text</button></p>{/if}
        <div class="composer"><textarea id="capture-description" bind:this={input} bind:value={question} rows="3" maxlength="2000" placeholder="For example: paid 450 for dinner at Saravana Bhavan" disabled={pending || voiceBusy || connectionStatus!=='online'}></textarea><VoiceQuestion bind:this={voiceControl} bind:busy={voiceBusy} disabled={pending || blocked || connectionStatus!=='online'} onTranscript={reviewVoice}/></div>
        <button class="primary" type="submit" disabled={pending || voiceBusy || (!reusableRetry && blocked) || !question.trim() || connectionStatus!=='online'}>{pending?'Preparing…':retry?'Retry expense preview':'Prepare expense'}</button>
        <small>AI credits are charged for each preparation and follow-up. Nothing is recorded until you confirm.</small>
      </form>
    {/if}
  {:else}
    <p role="status">Your expense for {label(recordedDate)} has been recorded.</p>
    <button class="primary" on:click={()=>onViewExpense(recordedDate)}>View expense</button><button on:click={onExit}>Back to Ask AI</button>
  {/if}
  {#if pending && !recordedDate}<p role="status">{extractionId?'Saving your choice…':'Checking the expense and your saved names…'}</p>{/if}
  {#if connectionStatus!=='online'}<p class="notice" role="status">{connectionStatus==='checking'?'App is not online yet. Waiting for the service to respond. AI sending is disabled.':'App is not online. Reconnect to record an expense or use AI.'}</p>{/if}
  {#if error}<p role="alert" class="notice">{error}</p>{/if}
</section>
<style>
  .capture{max-width:760px;margin:0 auto 24px;padding:22px;background:var(--paper);border:1px solid var(--line);border-radius:22px;color:var(--ink);font:400 11px/1.5 'DM Sans',sans-serif;box-shadow:var(--shadow)}
  .capture header{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:16px}.capture h2{font:700 19px/1.25 Manrope,sans-serif;margin:0}.eyebrow{margin:0 0 7px;color:var(--green);font-size:10px;font-weight:800;letter-spacing:.14em}
  .capture button{font:800 11px/1.4 'DM Sans',sans-serif;padding:10px 14px;border:1px solid var(--line);border-radius:10px;background:var(--wash);color:var(--ink);cursor:pointer}.capture header button{display:grid;place-items:center;width:34px;height:34px;padding:0;font-size:20px}.capture button:disabled{opacity:.42;cursor:not-allowed}.capture .primary,.mode-choice button[aria-pressed="true"]{border-color:var(--green);background:var(--green);color:#fff}
  .date-context{margin:0 0 16px;padding:12px;background:#edf3e3;border-radius:10px;font-size:11px}.date-context strong{font-family:Manrope,sans-serif;font-weight:800}.intro,.credits{font-size:11px;line-height:1.5;color:var(--muted);margin:12px 0}.notice{padding:12px;background:#fff1dc;border-radius:10px;font-size:11px}
  article{margin:14px 0;padding:12px;background:var(--wash);border-radius:10px}article.user{margin-left:24px;background:#edf3e3}article strong{font:700 11px/1.4 Manrope,sans-serif}article p{white-space:pre-wrap;margin:6px 0}
  .preview{padding:16px;border:1px solid var(--line);border-radius:15px}.preview h3{font:800 15px/1.3 Manrope,sans-serif;margin:0 0 12px}dl{margin:0}dl>div{display:flex;justify-content:space-between;gap:16px;margin:10px 0}dt{color:var(--muted)}dd{margin:0;text-align:right;overflow-wrap:anywhere;font:700 11px/1.5 Manrope,sans-serif}.actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:18px}
  label{display:block;margin-bottom:7px;font-size:11px;font-weight:800}.composer{display:flex;align-items:center;gap:10px}textarea{font:400 11px/1.5 'DM Sans',sans-serif;width:100%;min-width:0;padding:11px;border:1px solid var(--line);border-radius:10px;background:#fff;color:var(--ink);resize:vertical}.capture form>button{margin-top:12px}small{display:block;margin-top:12px;color:var(--muted);font-size:10px;line-height:1.5}
  button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible{outline:3px solid #91ad48;outline-offset:3px}.mode-choice{display:flex;flex-wrap:wrap;gap:8px}.manual-form{display:grid;grid-template-columns:1fr 1fr;gap:14px}.manual-form label{margin:0}.manual-form input,.manual-form select{display:block;box-sizing:border-box;width:100%;min-width:0;margin-top:7px;padding:11px;border:1px solid var(--line);border-radius:10px;background:#fff;color:var(--ink);font:400 11px/1.5 'DM Sans',sans-serif}.manual-form small{grid-column:1/-1}.manual-form datalist{display:none}@media(max-width:640px){.manual-form{grid-template-columns:1fr}.capture{padding:16px}}
</style>
