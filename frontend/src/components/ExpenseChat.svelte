<!-- FIN-EPIC-003: docs/jira/personal-expense/FIN-EPIC-003-insights.md -->
<script>
  import { onDestroy, tick } from 'svelte';
  import { askExpenseChat } from '../lib/api.js';
  export let selectedMonth;
  export let connectionStatus;
  let open = false, question = '', messages = [], pending = false, error = '', transcript, input, launcher;
  let requestController;
  let destroyed = false;
  const suggestions = ['Where did my money go?', 'Which were my largest expenses?', 'Compare my spending with the previous month'];
  async function scrollDown() { await tick(); if (transcript) transcript.scrollTop = transcript.scrollHeight; }
  async function show() { open = true; await tick(); input?.focus(); }
  async function close() { open = false; await tick(); launcher?.focus(); }
  function clear() { if (!pending) { messages = []; error = ''; question = ''; input?.focus(); } }
  function history() {
    const recent = messages.slice(-12).map(({ role, content }) => ({ role, content }));
    while (recent.reduce((sum, item) => sum + item.content.length, 0) > 24000) recent.splice(0, 2);
    return recent;
  }
  async function send(text = question) {
    const message = text.trim();
    if (!message || pending || connectionStatus !== 'online') return;
    const previous = history();
    messages = [...messages, { role: 'user', content: message }];
    question = ''; error = ''; pending = true;
    requestController = new AbortController();
    const timeout = setTimeout(() => requestController?.abort(), 95000);
    scrollDown();
    try {
      const response = await askExpenseChat(message, selectedMonth, previous, requestController.signal);
      if (!destroyed) messages = [...messages, { role: 'assistant', content: response.answer, evidence: response.evidence || [] }];
    } catch (cause) {
      if (destroyed) return;
      messages = messages.slice(0, -1);
      question = message;
      error = cause.name === 'AbortError' ? 'That took too long. Try a more focused question.' : cause.message || 'Could not send. Please try again.';
    } finally {
      clearTimeout(timeout);
      if (!destroyed) { pending = false; scrollDown(); await tick(); input?.focus(); }
    }
  }
  onDestroy(() => { destroyed = true; requestController?.abort(); });
  const labels = { category: 'Category', subcategory: 'Subcategory', merchant: 'Merchant', account: 'Account', nature: 'Spending type', day: 'Date', month: 'Month', weekday: 'Weekday', count: 'Records', total: 'Total', average: 'Average', largest: 'Largest', amount: 'Amount' };
  function columns(rows) { return Object.keys(rows[0] || {}).filter(key => key !== 'id'); }
  function cell(key, value, currency) {
    if (['total', 'average', 'largest', 'amount'].includes(key)) return money(value, currency);
    if (key === 'weekday') return ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][Number(value) - 1] || value;
    return value;
  }
  function lastDate(endDate) { const date = new Date(`${endDate}T00:00:00Z`); date.setUTCDate(date.getUTCDate() - 1); return date.toISOString().slice(0, 10); }
  function money(value, currency) { return new Intl.NumberFormat(undefined, { style: 'currency', currency: currency || 'INR', maximumFractionDigits: 2 }).format(value); }
</script>

{#if open}
  <section class="expense-chat" aria-label="Expense assistant">
    <header>
      <div><span class="eyebrow">YOUR RECORDED SPENDING</span><h2>Ask about expenses</h2></div>
      <button class="icon-button" aria-label="Close expense chat" on:click={close}>×</button>
    </header>
    <div class="chat-context"><span>Exploring {selectedMonth}</span><button on:click={clear} disabled={pending || !messages.length}>New chat</button></div>
    <div class="transcript" bind:this={transcript} role="log" aria-live="polite" aria-label="Expense conversation" aria-busy={pending}>
      {#if !messages.length}
        <div class="welcome"><span class="spark">✦</span><h3>Make sense of your spending.</h3><p>Ask a question, then dig deeper. I can read your recorded expenses, but can’t change them.</p></div>
        <div class="suggestions">{#each suggestions as suggestion}<button disabled={pending || connectionStatus !== 'online'} on:click={() => send(suggestion)}>{suggestion}<span aria-hidden="true">↗</span></button>{/each}</div>
      {/if}
      {#each messages as message}
        <article class:user={message.role === 'user'} class="message">
          <span class="speaker">{message.role === 'user' ? 'You' : 'Expense assistant'}</span>
          <p>{message.content}</p>
          {#if message.evidence?.length}
            <details><summary>Based on {message.evidence.length} expense {message.evidence.length === 1 ? 'query' : 'queries'}</summary>
              {#each message.evidence as source}
                <div class="evidence"><strong>{source.query.startDate} – {lastDate(source.query.endDate)}</strong>
                  <span>{source.matchingCount} matching records · {money(source.matchingTotal, source.currency)}</span>
                  {#if source.query.groupBy.length}<span>Grouped by {source.query.groupBy.join(', ')}</span>{/if}
                  {#each source.query.filters as filter}<span>{labels[filter.field] || filter.field} {{ eq: 'is', ne: 'excludes', contains: 'contains', gte: 'at least', lte: 'at most' }[filter.operator]} {filter.value}</span>{/each}
                  {#if source.truncated}<span>Showing the first {source.rows.length} results; total includes all matches.</span>{/if}
                  {#if source.rows.length}<div class="evidence-table"><table><thead><tr>{#each columns(source.rows) as column}<th>{labels[column] || column}</th>{/each}</tr></thead><tbody>{#each source.rows as row}<tr>{#each columns(source.rows) as column}<td>{cell(column, row[column], source.currency)}</td>{/each}</tr>{/each}</tbody></table></div>{/if}
                </div>
              {/each}
            </details>
          {/if}
        </article>
      {/each}
      {#if pending}<p class="thinking" role="status">Reading your expenses…</p>{/if}
    </div>
    <form on:submit|preventDefault={() => send()}>
      {#if error}<p class="chat-error" role="alert">{error}</p>{/if}
      {#if connectionStatus !== 'online'}<p class="chat-error" role="status">Connect to the service to ask about your expenses.</p>{/if}
      <label class="sr-only" for="expense-question">Your expense question</label>
      <div class="composer"><textarea id="expense-question" bind:this={input} bind:value={question} maxlength="2000" rows="2" placeholder="Ask about your expenses…" disabled={pending || connectionStatus !== 'online'} on:keydown={(event) => { if (event.key === 'Escape') close(); if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); send(); } }}></textarea><button type="submit" aria-label="Send question" disabled={pending || connectionStatus !== 'online' || !question.trim()}>↑</button></div>
      <small>Answers use recorded expenses and may need checking.</small>
    </form>
  </section>
{:else}
  <button class="chat-launcher" bind:this={launcher} on:click={show}><span aria-hidden="true">✦</span> Ask about expenses</button>
{/if}

<style>
  .chat-launcher{position:fixed;right:24px;bottom:92px;z-index:45;display:flex;align-items:center;gap:10px;background:#234c3c;color:#fff;border:0;border-radius:24px;padding:13px 19px;box-shadow:0 6px 24px #16332330;font-family:inherit;font-size:14px;font-weight:600;cursor:pointer}
  .expense-chat{position:fixed;right:24px;bottom:88px;width:410px;height:min(690px,calc(100dvh - 116px));z-index:60;background:#fffefb;border:1px solid #d9e2d9;border-radius:22px;box-shadow:0 16px 70px #183c3433;display:flex;flex-direction:column;overflow:hidden;color:#233b30;font-family:inherit}
  header{display:flex;align-items:center;justify-content:space-between;padding:20px 20px 15px;background:#eff4ed}h2{font-size:20px;margin:5px 0 0;letter-spacing:-.4px}.eyebrow{font-size:9px;letter-spacing:1.5px;color:#627467}.icon-button{border:0;background:transparent;font-size:28px;color:#52685c;cursor:pointer;padding:6px 10px}
  .chat-context{display:flex;justify-content:space-between;align-items:center;padding:10px 20px;border-bottom:1px solid #e7ebe3;font-size:11px;color:#657468}.chat-context button{border:0;background:none;color:#355d47;text-decoration:underline;cursor:pointer;font-size:11px}
  .transcript{overflow-y:auto;flex:1;padding:18px;min-height:0;overscroll-behavior:contain}.welcome{text-align:left;padding:10px 3px 15px}.spark{font-size:29px;color:#517e51}.welcome h3{font-size:23px;line-height:1.2;letter-spacing:-.6px;margin:12px 0}.welcome p{font-size:13px;line-height:1.7;color:#6b756b}.suggestions{display:grid;gap:8px}.suggestions button{display:flex;justify-content:space-between;text-align:left;gap:12px;border:1px solid #dce4d8;background:white;padding:12px;border-radius:10px;font:inherit;font-size:12px;color:#36573d;cursor:pointer}
  .message{padding:13px 14px;background:#f0f3eb;border-radius:14px;margin:0 18px 14px 0}.message.user{margin:0 0 14px 28px;background:#e0ece2}.speaker{font-size:10px;font-weight:700;color:#52694f}.message p{white-space:pre-wrap;overflow-wrap:anywhere;font-size:13px;line-height:1.65;margin:6px 0 0}details{font-size:10px;margin-top:12px;border-top:1px solid #ccd7c8;padding-top:9px}summary{cursor:pointer}.evidence{display:grid;gap:5px;margin-top:10px;overflow-wrap:anywhere}.evidence-table{max-height:190px;overflow:auto;background:#fff9;border-radius:6px}table{border-collapse:collapse;font-size:10px;width:100%}th,td{text-align:left;padding:7px;border-bottom:1px solid #dce4d8;white-space:nowrap}th{font-weight:600}.thinking{font-size:12px;color:#64725c;padding:10px}
  form{padding:12px 16px 16px;border-top:1px solid #e5e9df}.composer{display:flex;align-items:center;gap:8px;border:1px solid #d4dfcf;background:#fff;border-radius:12px;padding:8px}.composer textarea{flex:1;min-width:0;resize:none;border:0;outline:0;background:transparent;font:inherit;font-size:13px;line-height:1.5;color:#243c30;padding:3px}.composer:focus-within{outline:2px solid #73916c}.composer button{border:0;background:#315b43;color:white;border-radius:9px;width:34px;height:34px;font-size:23px;cursor:pointer}button:disabled{opacity:.45;cursor:default}small{display:block;text-align:center;font-size:9px;color:#7b8276;margin-top:9px}.chat-error{color:#975336;font-size:12px;line-height:1.5;margin:0 0 9px}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
  @media(max-width:600px){.chat-launcher{right:14px;bottom:83px;padding:11px 15px}.expense-chat{right:8px;left:8px;width:auto;bottom:80px;height:min(660px,calc(100dvh - 96px));border-radius:18px}}
</style>
