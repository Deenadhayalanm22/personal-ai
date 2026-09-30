<script>
  // FIN-EPIC-007: full expense correction stays in Money and uses FIN-EPIC-002 commands.
  import { onMount } from 'svelte';
  import { money } from './lib/journey.js';
  import { profileDate } from './lib/live-read.js';
  export let api;
  export let month;
  export let timezone;
  export let onChanged;
  let items=[];
  let cursor=null;
  let options={categories:[],merchants:[],accounts:[]};
  let loading=true;
  let notice='';
  let edit=null;
  let remove=null;
  let amount='';let date='';let category='';let subcategory='';let merchantId='';let accountId='';
  let saving=false;
  $: subcategories=options.categories.find(row=>row.name===category)?.subcategories || [];

  async function load(reset=true) {
    loading=true;
    try {
      const params=new URLSearchParams({month,limit:'20'});
      if(!reset && cursor)params.set('beforeId',cursor);
      const page=await api(`/expenses?${params}`);
      items=reset?page.items:[...items,...page.items];cursor=page.nextBeforeId==null?null:String(page.nextBeforeId);
      if(reset) options=await api('/expenses/options');
      notice='';
    }catch(cause){notice=cause.message || 'Transactions are unavailable.';}
    finally{loading=false;}
  }
  onMount(()=>load());
  function openEdit(row) {edit=row;amount=String(row.amount);date=profileDate(row.transactionTime,timezone)||'';category=row.category||'';subcategory=row.subcategory||'';merchantId=row.merchantId==null?'':String(row.merchantId);accountId=row.accountId==null?'':String(row.accountId);notice='';}
  async function save() {
    if(!edit || saving)return;
    if(!Number.isFinite(Number(amount)) || Number(amount)<=0 || !/^\d{4}-\d{2}-\d{2}$/.test(date)){notice='Enter a positive amount and valid effective date.';return;}
    if(category && (!subcategory || !subcategories.includes(subcategory))){notice='Choose a valid subcategory.';return;}
    saving=true;
    try {const changes={amount:Number(amount),transactionDate:date};
      if(category && subcategory){changes.category=category;changes.subcategory=subcategory;}
      if(merchantId)changes.merchantId=Number(merchantId);
      if(accountId)changes.accountId=Number(accountId);
      await api(`/expenses/${encodeURIComponent(edit.id)}`,{method:'PATCH',body:JSON.stringify(changes)});
      edit=null;await load();await onChanged();notice='Correction saved. Affected dates have been refreshed.';
    }catch(cause){notice=cause.message || 'Correction failed.';}finally{saving=false;}
  }
  async function confirmDelete() {
    if(!remove || saving)return;saving=true;
    try {await api(`/expenses/${encodeURIComponent(remove.id)}`,{method:'DELETE'});remove=null;await load();await onChanged();notice='Expense deleted.';}
    catch(cause){notice=cause.message || 'Delete failed.';}finally{saving=false;}
  }
</script>

<section class="transaction-tools"><div class="transaction-heading"><h2>Transactions</h2><button onclick={()=>load()}>Refresh</button></div><p>Search this month’s recorded expenses, correct their facts, or delete a mistaken record.</p>
  {#if notice}<p class="live-message" role="status">{notice}</p>{/if}
  {#if loading && !items.length}<p>Loading transactions…</p>{/if}
  {#each items as row (row.id)}<div class="transaction-row"><div><strong>{row.merchant || row.category || 'Expense'}</strong><small>{profileDate(row.transactionTime,timezone) || 'Effective date unavailable'} · {row.category || 'Unclassified'}{row.subcategory?` / ${row.subcategory}`:''}</small></div><strong>{money(row.amount)}</strong><button onclick={()=>openEdit(row)}>Correct</button><button onclick={()=>remove=row}>Delete</button></div>{:else}{#if !loading}<p>No recorded expenses are available for this month.</p>{/if}{/each}
  {#if cursor}<button onclick={()=>load(false)} disabled={loading}>Load more transactions</button>{/if}
</section>
{#if edit}<div class="live-overlay" role="presentation"><div role="dialog" tabindex="-1" aria-modal="true" aria-label="Correct transaction"><h2>Correct transaction</h2>
  <label>Amount <input type="number" min="0.01" step="0.01" bind:value={amount}/></label><label>Effective date <input type="date" bind:value={date}/></label>
  <label>Category <select bind:value={category} onchange={()=>subcategory=''}><option value="">Keep current</option>{#each options.categories as option}<option value={option.name}>{option.name}</option>{/each}</select></label>
  {#if category}<label>Subcategory <select bind:value={subcategory}><option value="">Choose subcategory</option>{#each subcategories as option}<option value={option}>{option}</option>{/each}</select></label>{/if}
  <label>Merchant <select bind:value={merchantId}><option value="">Keep current</option>{#each options.merchants as option}<option value={option.id}>{option.name}</option>{/each}</select></label>
  <label>Source account <select bind:value={accountId}><option value="">Keep current</option>{#each options.accounts as option}<option value={option.id}>{option.name}</option>{/each}</select></label>
  {#if notice}<p role="alert">{notice}</p>{/if}<button onclick={()=>edit=null} disabled={saving}>Cancel</button><button onclick={save} disabled={saving}>Save correction</button></div></div>{/if}
{#if remove}<div class="live-overlay" role="presentation"><div role="dialog" tabindex="-1" aria-modal="true" aria-label="Delete transaction"><h2>Delete this expense?</h2><p>{remove.merchant || remove.category || 'Expense'} · {money(remove.amount)}</p><p>This removes the expense from current activity.</p><button onclick={()=>remove=null} disabled={saving}>Cancel</button><button onclick={confirmDelete} disabled={saving}>Delete expense</button></div></div>{/if}
