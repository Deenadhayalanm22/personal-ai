<!-- FIN-EPIC-003: docs/jira/personal-expense/FIN-EPIC-003-insights.md -->
<script>
  import { onDestroy, onMount, tick } from 'svelte';
  import FinancialEvidence from './FinancialEvidence.svelte';
  import { getAiCredits, askExpenseChat, getMoneyConversations, saveMoneyConversation } from '../lib/api.js';
  export let selectedMonth;
  export let connectionStatus;
  let open = false, question = '', messages = [], pending = false, error = '', transcript, input, launcher;
  let conversations = [], activeId = null, conversationMonth = null;
  let loadingHistory = true, storageError = '', draftTimer;
  const saves = new Map();
  let requestController;
  let credits = null, creditError = '', loadingCredits = false, retryRequest = null;
  $: creditBlocked = !credits || credits.available <= 0 || credits.paused || !credits.enabled || !credits.configured;
  const formatCredits = value => Number(value).toLocaleString(undefined, { maximumFractionDigits: 6 });
  async function refreshCredits() {
    loadingCredits = true;
    try { const value = await getAiCredits(); if (!destroyed) { credits = value; creditError = ''; } }
    catch (cause) { if (!destroyed) { credits = null; creditError = cause.message || 'Credits could not be loaded.'; } }
    finally { if (!destroyed) loadingCredits = false; }
  }
  let destroyed = false;
  const suggestions = ['Can I cover next month’s commitments with my salary?', 'Where did my money go?', 'Show my loans and planned investments', 'Which were my largest expenses?'];
  async function scrollDown() { await tick(); if (transcript) transcript.scrollTop = transcript.scrollHeight; }
  async function show() { open = true; refreshCredits(); await tick(); input?.focus(); }
  async function close() { open = false; await tick(); launcher?.focus(); }
  function saveConversation() {
    if (!activeId && !messages.length && !question.trim()) return Promise.resolve();
    if (!activeId) {
      activeId = crypto.randomUUID();
      conversationMonth = selectedMonth;
    }
    const existing = conversations.find(chat => chat.id === activeId);
    const firstQuestion = messages.find(message => message.role === 'user')?.content || question.trim();
    const conversation = {
      id: activeId, title: firstQuestion || existing?.title || 'New conversation',
      month: conversationMonth, messages, draft: question
    };
    conversations = existing
      ? conversations.map(chat => chat.id === activeId ? conversation : chat)
      : [conversation, ...conversations];
    const previous = saves.get(conversation.id) || Promise.resolve();
    const current = previous.catch(() => {}).then(() => saveMoneyConversation(conversation));
    saves.set(conversation.id, current);
    current.catch(() => { if (!destroyed) storageError = 'Chat could not be saved. Retry before leaving this page.'; });
    return current;
  }
  async function newChat() {
    if (pending) return;
    clearTimeout(draftTimer);
    try { await saveConversation(); storageError = ''; } catch { return; }
    activeId = null; conversationMonth = null; messages = []; error = ''; question = ''; retryRequest = null;
    input?.focus();
  }
  async function selectConversation(id) {
    if (pending || id === activeId) return;
    clearTimeout(draftTimer);
    try { await saveConversation(); storageError = ''; } catch { return; }
    const conversation = conversations.find(chat => chat.id === id);
    if (!conversation) return;
    activeId = conversation.id; conversationMonth = conversation.month;
    messages = conversation.messages; question = conversation.draft; error = ''; retryRequest = null;
    await scrollDown();
    input?.focus();
  }
  function history() {
    const recent = messages.slice(-12).map(({ role, content }) => ({ role, content }));
    while (recent.reduce((sum, item) => sum + item.content.length, 0) > 24000) recent.splice(0, 2);
    return recent;
  }
  onMount(async () => {
    refreshCredits();
    try {
      const saved = await getMoneyConversations();
      if (destroyed) return;
      conversations = Array.isArray(saved) ? saved : [];
      if (conversations.length) {
        const recent = conversations[0];
        activeId = recent.id; conversationMonth = recent.month;
        messages = recent.messages; question = recent.draft;
      }
    } catch {
      if (!destroyed) storageError = 'Saved chats could not be loaded. Try refreshing the page.';
    } finally { if (!destroyed) loadingHistory = false; }
  });
  function scheduleDraftSave() {
    clearTimeout(draftTimer);
    draftTimer = setTimeout(() => saveConversation(), 500);
  }
  async function send(text = question, recover = false) {
    const message = text.trim();
    if (!message || pending || loadingHistory || connectionStatus !== 'online') return;
    clearTimeout(draftTimer);
    const previous = history();
    const signature = JSON.stringify({ message, month: conversationMonth, history: previous });
    if (creditBlocked && !(recover && retryRequest?.signature === signature)) return;
    messages = [...messages, { role: 'user', content: message }];
    question = ''; error = ''; pending = true;
    saveConversation();
    requestController = new AbortController();
    const timeout = setTimeout(() => requestController?.abort(), 95000);
    scrollDown();
    try {
      const signature = JSON.stringify({ message, month: conversationMonth, history: previous });
      if (!retryRequest || retryRequest.signature !== signature) retryRequest = { signature, id: crypto.randomUUID() };
      const response = await askExpenseChat(message, conversationMonth, previous, requestController.signal, retryRequest.id);
      retryRequest = null;
      if (!destroyed && response.credits) credits = response.credits;
      if (!destroyed) messages = [...messages, { role: 'assistant', content: response.answer, evidence: response.evidence || [] }];
    } catch (cause) {
      if (destroyed) return;
      if (cause.status && cause.data?.code !== 'AI_USAGE_PENDING') retryRequest = null;
      messages = messages.slice(0, -1);
      question = message;
      error = cause.name === 'AbortError' ? 'That took too long. Try a more focused question.' : cause.message || 'Could not send. Please try again.';
    } finally {
      clearTimeout(timeout);
      if (!destroyed) { pending = false; refreshCredits(); try { await saveConversation(); storageError = ''; } catch {} scrollDown(); await tick(); input?.focus(); }
    }
  }
  onDestroy(() => { destroyed = true; clearTimeout(draftTimer); requestController?.abort(); });
</script>

{#if open}
  <section class="expense-chat" aria-label="Money assistant">
    <header>
      <div><span class="eyebrow">YOUR MONEY, IN CONTEXT</span><h2>Ask about your money</h2></div>
      <button class="icon-button" aria-label="Close money chat" on:click={close}>×</button>
    </header>
    <div class="chat-context"><span>Exploring {conversationMonth || selectedMonth}</span><button on:click={newChat} disabled={pending || loadingHistory || (!messages.length && !question.trim())}>New chat</button></div>
    <div class="credit-status" aria-live="polite">
      <span>{credits ? `${formatCredits(credits.available)} credits available` : 'Credits unavailable'}{credits?.reserved > 0 ? ` · ${formatCredits(credits.reserved)} on hold` : ''}</span>
      <button on:click={refreshCredits} disabled={loadingCredits || pending}>Refresh credits</button>
    </div>
    {#if credits?.paused}<p class="credit-notice">Your AI access is paused. Contact your administrator.</p>
    {:else if credits && (!credits.enabled || !credits.configured)}<p class="credit-notice">Money chat is currently unavailable. Contact your administrator.</p>
    {:else if credits && credits.available <= 0}<p class="credit-notice">You’ve used your AI credits. Contact your administrator for more credits.</p>{/if}
    {#if creditError}<p class="credit-notice" role="alert">{creditError}</p>{/if}
    {#if conversations.length}
      <div class="history-heading"><span>Recent chats</span><span>Saved to your profile</span></div>
      <nav class="chat-history" aria-label="Recent money chats">
        {#if !activeId}<span class="history-draft" aria-current="true">New conversation</span>{/if}
        {#each conversations as conversation (conversation.id)}
          <button class:active={conversation.id === activeId} aria-current={conversation.id === activeId ? 'true' : undefined} title={conversation.title} disabled={pending || loadingHistory} on:click={() => selectConversation(conversation.id)}>
            <span class="history-title">{conversation.title}</span><span class="history-month">{conversation.month}</span>
          </button>
        {/each}
      </nav>
    {/if}
    <div class="transcript" bind:this={transcript} role="log" aria-live="polite" aria-label="Money conversation" aria-busy={pending}>
      {#if !messages.length}
        <div class="welcome"><span class="spark">✦</span><h3>See how it all adds up.</h3><p>Ask a question, then dig deeper. Explore expenses, loans, investments and commitments, or compare a what-if plan. I won’t change your records.</p></div>
        <div class="suggestions">{#each suggestions as suggestion}<button disabled={pending || loadingHistory || creditBlocked || connectionStatus !== 'online'} on:click={() => send(suggestion)}>{suggestion}<span aria-hidden="true">↗</span></button>{/each}</div>
      {/if}
      {#each messages as message}
        <article class:user={message.role === 'user'} class="message">
          <span class="speaker">{message.role === 'user' ? 'You' : 'Money assistant'}</span>
          <p>{message.content}</p>
          {#if message.evidence?.length}
            <details><summary>Based on {message.evidence.length} data {message.evidence.length === 1 ? 'query' : 'queries'}</summary>
              {#each message.evidence as source}
                <FinancialEvidence {source} />
              {/each}
            </details>
          {/if}
        </article>
      {/each}
      {#if pending}<p class="thinking" role="status">Checking your financial records…</p>{/if}
    </div>
    <form on:submit|preventDefault={() => send()}>
      {#if error}<p class="chat-error" role="alert">{error}</p>
        {#if retryRequest && JSON.parse(retryRequest.signature).message === question.trim()}<button type="button" class="recover-request" disabled={pending || connectionStatus !== 'online'} on:click={() => send(question, true)}>Check previous answer</button>{/if}
      {/if}
      {#if storageError}<p class="chat-error" role="alert">{storageError}</p>{/if}
      {#if connectionStatus !== 'online'}<p class="chat-error" role="status">Connect to the service to ask about your money.</p>{/if}
      <label class="sr-only" for="expense-question">Your money question</label>
      <div class="composer"><textarea id="expense-question" bind:this={input} bind:value={question} maxlength="2000" rows="2" placeholder="Ask about your money…" disabled={pending || loadingHistory || connectionStatus !== 'online'} on:input={scheduleDraftSave} on:keydown={(event) => { if (event.key === 'Escape') close(); if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); send(); } }}></textarea><button type="submit" aria-label="Send question" disabled={pending || loadingHistory || creditBlocked || connectionStatus !== 'online' || !question.trim()}>↑</button></div>
      <small>AI credits are charged by usage, including follow-ups and scope checks.</small>
      <small>Based on recorded data. Scenarios are estimates, not changes.</small>
    </form>
  </section>
{:else}
  <button class="chat-launcher" bind:this={launcher} on:click={show}><span aria-hidden="true">✦</span> Ask about your money</button>
{/if}

<style>
  .recover-request{margin-bottom:8px;padding:7px 10px;border:1px solid #a6bba9;border-radius:8px;background:#eef4ed;color:#234c3c;cursor:pointer;font:inherit;font-size:12px}
  .credit-status{display:flex;justify-content:space-between;align-items:center;gap:8px;padding:8px 20px;font-size:11px;background:#f5f7f1}.credit-status button{border:0;background:none;text-decoration:underline;color:#355d47;cursor:pointer;font:inherit}.credit-notice{padding:6px 20px;margin:0;font-size:12px;color:#975336}

  .chat-launcher{position:fixed;right:24px;bottom:92px;z-index:45;display:flex;align-items:center;gap:10px;background:#234c3c;color:#fff;border:0;border-radius:24px;padding:13px 19px;box-shadow:0 6px 24px #16332330;font-family:inherit;font-size:14px;font-weight:600;cursor:pointer}
  .expense-chat{position:fixed;right:24px;bottom:88px;width:410px;height:min(690px,calc(100dvh - 116px));z-index:60;background:#fffefb;border:1px solid #d9e2d9;border-radius:22px;box-shadow:0 16px 70px #183c3433;display:flex;flex-direction:column;overflow:hidden;color:#233b30;font-family:inherit}
  header{display:flex;align-items:center;justify-content:space-between;padding:20px 20px 15px;background:#eff4ed}h2{font-size:20px;margin:5px 0 0;letter-spacing:-.4px}.eyebrow{font-size:9px;letter-spacing:1.5px;color:#627467}.icon-button{border:0;background:transparent;font-size:28px;color:#52685c;cursor:pointer;padding:6px 10px}
  .chat-context{display:flex;justify-content:space-between;align-items:center;padding:10px 20px;border-bottom:1px solid #e7ebe3;font-size:11px;color:#657468}.chat-context button{border:0;background:none;color:#355d47;text-decoration:underline;cursor:pointer;font-size:11px}
  .history-heading{display:flex;justify-content:space-between;gap:8px;padding:10px 18px 5px;color:#657468;font-size:10px}.history-heading span:first-child{font-weight:600;color:#36573d}
  .chat-history{display:flex;gap:8px;overflow-x:auto;flex-shrink:0;padding:4px 18px 12px;border-bottom:1px solid #e7ebe3;overscroll-behavior-x:contain}.chat-history button,.history-draft{flex:0 0 155px;min-width:0;box-sizing:border-box;border:1px solid #dce4d8;border-radius:10px;background:#fff;padding:9px 11px;text-align:left;color:#36573d;font-family:inherit}.chat-history button{cursor:pointer}.chat-history button.active,.history-draft{background:#e0ece2;border-color:#7f9d83}.history-title{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px;font-weight:600}.history-month{display:block;font-size:10px;color:#657468;margin-top:4px}.history-draft{font-size:12px;display:flex;align-items:center}.chat-history button:focus-visible{outline:2px solid #315b43;outline-offset:1px}
  .transcript{overflow-y:auto;flex:1;padding:18px;min-height:0;overscroll-behavior:contain}.welcome{text-align:left;padding:10px 3px 15px}.spark{font-size:29px;color:#517e51}.welcome h3{font-size:23px;line-height:1.2;letter-spacing:-.6px;margin:12px 0}.welcome p{font-size:13px;line-height:1.7;color:#6b756b}.suggestions{display:grid;gap:8px}.suggestions button{display:flex;justify-content:space-between;text-align:left;gap:12px;border:1px solid #dce4d8;background:white;padding:12px;border-radius:10px;font:inherit;font-size:12px;color:#36573d;cursor:pointer}
  .message{padding:13px 14px;background:#f0f3eb;border-radius:14px;margin:0 18px 14px 0}.message.user{margin:0 0 14px 28px;background:#e0ece2}.speaker{font-size:10px;font-weight:700;color:#52694f}.message p{white-space:pre-wrap;overflow-wrap:anywhere;font-size:13px;line-height:1.65;margin:6px 0 0}details{font-size:10px;margin-top:12px;border-top:1px solid #ccd7c8;padding-top:9px}summary{cursor:pointer}.thinking{font-size:12px;color:#64725c;padding:10px}
  form{padding:12px 16px 16px;border-top:1px solid #e5e9df}.composer{display:flex;align-items:center;gap:8px;border:1px solid #d4dfcf;background:#fff;border-radius:12px;padding:8px}.composer textarea{flex:1;min-width:0;resize:none;border:0;outline:0;background:transparent;font:inherit;font-size:13px;line-height:1.5;color:#243c30;padding:3px}.composer:focus-within{outline:2px solid #73916c}.composer button{border:0;background:#315b43;color:white;border-radius:9px;width:34px;height:34px;font-size:23px;cursor:pointer}button:disabled{opacity:.45;cursor:default}small{display:block;text-align:center;font-size:9px;color:#7b8276;margin-top:9px}.chat-error{color:#975336;font-size:12px;line-height:1.5;margin:0 0 9px}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
  @media(max-width:600px){.chat-launcher{right:14px;bottom:83px;padding:11px 15px}.expense-chat{right:8px;left:8px;width:auto;bottom:80px;height:min(660px,calc(100dvh - 96px));border-radius:18px}}
</style>
