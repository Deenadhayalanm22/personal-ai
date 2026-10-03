<!-- FIN-EPIC-005 / FIN-025: shared expense-account identity for planning and insights. -->
<script>
  import { onMount } from 'svelte';
  import { getAccounts, createAccount, configureAccount } from '../lib/api.js';
  import './accounts.css';
  export let connectionStatus;
  export let onChanged=async()=>{};
  let accounts=[],status='loading',error='',editing=null,showForm=false,saving=false,formError='';
  let form={accountReferenceId:'',name:'',type:'BANK',issuerName:'',statementDay:'',dueDay:'',startMonth:''};
  async function load(){status='loading';error='';try{const result=await getAccounts();accounts=result.accounts||[];status='ready';}catch(cause){status='error';error=cause?.message||'Could not load accounts.';}}
  onMount(load);
  function open(account=null){editing=account;form={accountReferenceId:account?String(account.id):'',name:account?.name||'',type:account?.type==='CREDIT_CARD'?'CREDIT_CARD':'BANK',issuerName:account?.issuerName||'',statementDay:account?.statementDay||'',dueDay:account?.dueDay||'',startMonth:account?.startMonth||''};formError='';showForm=true;}
  function useExisting(){const account=accounts.find(a=>String(a.id)===form.accountReferenceId);if(account)form={...form,name:account.name};}
  async function save(){
    if(saving)return;saving=true;formError='';
    const payload={accountReferenceId:form.accountReferenceId?Number(form.accountReferenceId):null,name:form.name.trim(),type:form.type,
      issuerName:form.type==='CREDIT_CARD'?form.issuerName.trim():null,statementDay:form.type==='CREDIT_CARD'?Number(form.statementDay):null,dueDay:form.type==='CREDIT_CARD'?Number(form.dueDay):null,startMonth:form.type==='CREDIT_CARD'?(form.startMonth||(editing?.startMonth?'':null)):null};
    try{if(editing)await configureAccount(editing.id,payload);else await createAccount(payload);showForm=false;await load();await onChanged();}
    catch(cause){formError=cause?.message||'Could not save this account.';}finally{saving=false;}
  }
</script>
<section class="accounts-module" aria-label="Accounts">
  <header class="accounts-heading"><div><p class="micro-label">YOUR MONEY SOURCES</p><h3>Accounts</h3><p>Your income context, bank accounts and credit-card billing details.</p></div><button class="primary" disabled={connectionStatus!=='online'} on:click={()=>open()}>＋ Add account</button></header>
  <div class="accounts-income"><slot /></div>
  <div class="accounts-list-heading"><h4>Your accounts</h4>{#if status==='ready'}<span>{accounts.filter(a=>a.type==='BANK').length} bank / debit · {accounts.filter(a=>a.type==='CREDIT_CARD').length} credit cards</span>{/if}</div>
  {#if status==='loading'}<p class="loan-state">Loading accounts…</p>
  {:else if status==='error'}<p class="form-error">{error}</p><button class="secondary" on:click={load}>Retry accounts</button>
  {:else if !accounts.length}<p class="accounts-empty">Add an account now, or configure a name already used in an expense. Name and type are enough.</p>
  {:else}<div class="account-list">{#each accounts as account}
    <article class="account-card" class:credit={account.type==='CREDIT_CARD'}>
      <div class="account-card-heading"><span class="account-symbol" aria-hidden="true">{account.type==='CREDIT_CARD'?'▣':'◉'}</span><div><h4>{account.name}</h4><span class="account-type">{account.type==='CREDIT_CARD'?'Credit card':account.type==='BANK'?'Bank / debit':'Choose account type'}</span></div><button class="account-edit" disabled={connectionStatus!=='online'} aria-label={`Configure ${account.name}`} on:click={()=>open(account)}>🔧</button></div>
      {#if account.type==='CREDIT_CARD'}<div class="account-facts"><div><span>Bill generates</span><strong>Day {account.statementDay}</strong></div><div><span>Payment due</span><strong>Day {account.dueDay}</strong></div></div><p>{#if account.startMonth}Bills included from {account.startMonth} · {/if}{account.issuerName} · Purchases count as spending. Review and settle the bill below.</p>
      {:else if account.type==='BANK'}<p>Use this account name when recording expenses.</p>
      {:else}<p>Already linked to recorded expenses. Choose bank/debit or credit card to configure it.</p><button class="account-receive" disabled={connectionStatus!=='online'} on:click={()=>open(account)}>Set account type</button>{/if}
    </article>
  {/each}</div>{/if}
  <slot name="bills" />
  <p class="accounts-note">Account details help organize expenses and provide context for planning and AI insights.</p>

</section>
{#if showForm}<div class="modal-backdrop accounts-backdrop"><div class="modal loan-form" role="dialog" aria-modal="true" tabindex="-1" aria-labelledby="account-title"><button class="close" aria-label="Close account form" disabled={saving} on:click={()=>showForm=false}>×</button><p class="micro-label">ACCOUNTS</p><h2 id="account-title">{editing?'Configure account':'Add account'}</h2><form on:submit|preventDefault={save}>
  {#if !editing}<label>Use an existing expense account (optional)<select bind:value={form.accountReferenceId} on:change={useExisting}><option value="">Create a new account</option>{#each accounts.filter(a=>a.type==='UNCONFIGURED') as a}<option value={String(a.id)}>{a.name}</option>{/each}</select></label>{/if}
  <label>Account name<input required maxlength="120" bind:value={form.name} disabled={saving||Boolean(form.accountReferenceId)} placeholder="e.g. HDFC bank account"/></label>
  <label>Account type<select bind:value={form.type} disabled={saving||Boolean(editing&&editing.type!=='UNCONFIGURED')}><option value="BANK">Bank / debit account</option><option value="CREDIT_CARD">Credit card</option></select></label>
  {#if form.type==='CREDIT_CARD'}<label>Bank / issuer<input required maxlength="120" bind:value={form.issuerName} disabled={saving}/></label><div class="account-form-pair"><label>Bill generation day<input required type="number" min="1" max="28" bind:value={form.statementDay} disabled={saving}/></label><label>Payment due day<input required type="number" min="1" max="28" bind:value={form.dueDay} disabled={saving}/></label></div><label>Start tracking bills from (optional)<input type="month" bind:value={form.startMonth} disabled={saving}/></label><p class="module-reassurance">This is the first month whose bill appears in your plan. Leave it empty to include all recorded billing periods.</p><p class="module-reassurance">Generation-day purchases go into the following bill. Paying a bill does not add another expense.</p>
  {:else}<p class="module-reassurance">Name and type are enough to connect this account to your expenses.</p>{/if}
  {#if formError}<p class="form-error" role="alert">{formError}</p>{/if}<div class="modal-actions"><button type="button" class="secondary" disabled={saving} on:click={()=>showForm=false}>Cancel</button><button class="primary" disabled={saving||connectionStatus!=='online'}>{saving?'Saving…':'Save account'}</button></div>
</form></div></div>{/if}
