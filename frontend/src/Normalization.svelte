<script>
  import { createReferencePreference, getReferenceEntityTypes, getReferencePreferences, mergeReferencePreferences } from './lib/api.js';

  let status = 'loading', error = '', entityTypes = [], savedPreferences = [];
  let expanded = null, showModal = false, saving = false, formError = '';
  let selectedMerchantIds = [], preferredMerchantName = '', showMergeModal = false, mergeNotice = '', merging = false, mergeError = '';
  let entityType = '', primaryReference = '', alias = '';

  const labels = { MERCHANT: 'Merchant', BENEFICIARY: 'Beneficiary', ACCOUNT: 'Account' };
  const icons = { MERCHANT: 'M', BENEFICIARY: 'B', ACCOUNT: '₹' };
  const typeLabel = type => labels[type] || type.replaceAll('_', ' ').toLowerCase().replace(/^./, value => value.toUpperCase());
  const aliasesFor = item => (item.aliases || []).map(value => typeof value === 'string' ? value : value.alias).filter(Boolean);
  const preferencesFrom = data => Array.isArray(data) ? data : (data?.references || data?.referencePreferences || data?.preferences || data?.items || []);
  $: merchantsForMerge = savedPreferences
    .filter(item => item.referenceId != null && item.status !== 'INACTIVE' && item.status !== 'MERGED')
    .map(item => ({ id: item.referenceId, name: item.primaryReference, entityType: item.entityType, transactionCount: Number(item.transactionCount || 0), icon: icons[item.entityType] || item.primaryReference?.[0]?.toUpperCase() || 'R' }));
  $: selectedMerchants = merchantsForMerge.filter(merchant => selectedMerchantIds.includes(merchant.id));
  $: selectedTransactionCount = selectedMerchants.reduce((total, merchant) => total + merchant.transactionCount, 0);
  $: selectedEntityType = selectedMerchants[0]?.entityType || '';
  function toggleMerchant(id) { selectedMerchantIds = selectedMerchantIds.includes(id) ? selectedMerchantIds.filter(value => value !== id) : [...selectedMerchantIds, id]; mergeNotice = ''; }
  function openMerge() { if (selectedMerchants.length < 2) return; preferredMerchantName = selectedMerchants[0].name; mergeError = ''; showMergeModal = true; }
  function closeMerge() { if (!merging) showMergeModal = false; }
  async function confirmMerge() {
    const name = preferredMerchantName.trim(); if (!name || merging) return;
    merging = true; mergeError = '';
    try {
      const result = await mergeReferencePreferences({ entityType: selectedEntityType, referenceIds: selectedMerchantIds, canonicalName: name });
      const transactionCount = result?.updatedTransactionCount ?? selectedTransactionCount;
      mergeNotice = `${transactionCount} transactions are now grouped under ${result?.canonicalReference?.name || name}.`;
      selectedMerchantIds = []; showMergeModal = false; await refreshPreferences();
    } catch (cause) { mergeError = cause?.message || 'Could not merge these references.'; }
    finally { merging = false; }
  }

  async function loadScreen() {
    status = 'loading'; error = '';
    try {
      const [typeData, preferenceData] = await Promise.all([getReferenceEntityTypes(), getReferencePreferences()]);
      entityTypes = typeData?.entityTypes || [];
      savedPreferences = preferencesFrom(preferenceData);
      entityType = entityType || entityTypes[0] || '';
      status = 'ready';
    } catch (cause) { status = 'error'; error = cause?.message || 'Could not load reference preferences.'; }
  }

  async function refreshPreferences() { savedPreferences = preferencesFrom(await getReferencePreferences()); }

  function openCreate() { entityType = entityTypes[0] || ''; primaryReference = ''; alias = ''; formError = ''; showModal = true; }
  function closeModal() { showModal = false; }

  async function save() {
    const primary = primaryReference.trim(), aliasValue = alias.trim();
    if (!entityType || !primary || !aliasValue) { formError = 'Complete all three fields and provide at least one alias.'; return; }
    saving = true; formError = '';
    try {
      await createReferencePreference({ entityType, primaryReference: primary, alias: aliasValue });
      await refreshPreferences();
      closeModal();
    } catch (cause) { formError = cause?.message || 'Could not save this preference.'; }
    finally { saving = false; }
  }

  loadScreen();
</script>

<section class="normalization-page">
  <div class="normalization-heading">
    <div><p class="micro-label">FIX YOUR ACCOUNTS</p><h1>Account repair</h1><p>Edit, add, or merge merchant, beneficiary, and account names so your records stay clean and consistent.</p></div>
    <button class="add-normalization" type="button" aria-label="Add reference preference" on:click={openCreate} disabled={status !== 'ready' || !entityTypes.length}>＋</button>
  </div>

  <section class="merge-workspace" aria-labelledby="merchant-merge-title">
    <div class="merge-title-row">
      <div><p class="micro-label">REFERENCE CLEANUP</p><h2 id="merchant-merge-title">Merge matching names</h2><p>Select two or more names of the same type, then choose the name to keep.</p></div>
    </div>
    <div class="merchant-selection" aria-label="Select merchants to merge">
      {#each merchantsForMerge as merchant}
        <label class:selected={selectedMerchantIds.includes(merchant.id)} class:blocked={Boolean(selectedEntityType && selectedEntityType !== merchant.entityType)} class="merchant-option">
          <input type="checkbox" disabled={Boolean(selectedEntityType && selectedEntityType !== merchant.entityType)} checked={selectedMerchantIds.includes(merchant.id)} on:change={() => toggleMerchant(merchant.id)} />
          <span class="merge-label-icon">{merchant.icon}</span><span><strong>{merchant.name}</strong><small>{merchant.entityType === 'ACCOUNT' ? 'Account' : 'Merchant'} · {merchant.transactionCount} {merchant.transactionCount === 1 ? 'transaction' : 'transactions'}</small></span>
        </label>
      {/each}
    </div>
    {#if !merchantsForMerge.length && status === 'ready'}<p class="merge-type-note">Add at least two references of the same type before merging them.</p>{/if}
    <div class="merge-selection-footer"><span>{selectedMerchants.length ? `${selectedMerchants.length} ${selectedEntityType.toLowerCase()} names · ${selectedTransactionCount} transactions selected` : 'Select at least two names to merge'}</span><button class="merge-button" type="button" disabled={selectedMerchants.length < 2} on:click={openMerge}>Merge selected</button></div>
    {#if selectedEntityType}<p class="merge-type-note">You’re selecting {selectedEntityType.toLowerCase()} references. Deselect them before choosing a different reference type.</p>{/if}
    {#if mergeNotice}<p class="merge-success" role="status">✓ {mergeNotice}</p>{/if}
  </section>

  <div class="saved-preferences-heading"><p class="micro-label">SAVED NAMES</p><h2>Your normalization rules</h2></div>

  {#if status === 'loading'}
    <div class="skeleton-panel normalization-skeleton"><i></i><b></b><b></b><b></b></div>
  {:else if status === 'error'}
    <div class="section-error"><h2>Normalization is unavailable</h2><p>{error}</p><button on:click={loadScreen}>Try again</button></div>
  {:else if savedPreferences.length}
    <div class="normalization-list">
      {#each savedPreferences as item}
        <article class:expanded={expanded === item.referenceId} class="normalization-row">
          <button class="normalization-main" on:click={() => expanded = expanded === item.referenceId ? null : item.referenceId} aria-expanded={expanded === item.referenceId}>
            <span class={`entity-icon ${item.entityType === 'ACCOUNT' ? 'account' : ''}`}>{icons[item.entityType] || 'R'}</span>
            <span class="entity-copy"><strong>{item.primaryReference}</strong><small>{typeLabel(item.entityType)} · {aliasesFor(item).length} {aliasesFor(item).length === 1 ? 'alias' : 'aliases'}</small></span>
            <span class:open={expanded === item.referenceId} class="normalization-chevron">⌄</span>
          </button>
          {#if expanded === item.referenceId}
            <div class="alias-details"><div class="alias-label"><span>Your preferred aliases</span></div><div class="alias-pills">{#each aliasesFor(item) as value}<span>{value}</span>{/each}</div></div>
          {/if}
        </article>
      {/each}
    </div>
  {:else}
    <div class="empty-state normalization-empty"><span>≋</span><h2>Add your first preferred name</h2><p>Choose a reference type, then enter its primary name and the aliases you use.</p><button class="wide-button" on:click={openCreate}>＋ Add a preference</button></div>
  {/if}

  <aside class="normalization-tip"><span>✦</span><p><strong>How aliases work</strong> “HDFC credit card” can also respond to “credit card” or “cc”. Enter multiple aliases separated by commas.</p></aside>
</section>

{#if showModal}
  <div class="modal-backdrop normalization-modal-backdrop">
    <form class="modal" on:submit|preventDefault={save}>
      <button class="close" type="button" on:click={closeModal}>×</button><p class="micro-label">ADD PREFERENCE</p><h2>Add a preferred name</h2>
      <label>Reference type<select bind:value={entityType} required><option value="" disabled>Select a type</option>{#each entityTypes as type}<option value={type}>{typeLabel(type)}</option>{/each}</select></label>
      <label>Primary reference<input bind:value={primaryReference} placeholder="e.g. Amazon" required /></label>
      <label>Aliases <span class="optional">Separate with commas</span><input bind:value={alias} placeholder="e.g. AMZN, Amazon India, Amazon Store" required /></label>
      {#if formError}<p class="form-error" role="alert">{formError}</p>{/if}
      <div class="modal-actions"><button class="secondary" type="button" on:click={closeModal}>Cancel</button><button class="primary" type="submit" disabled={saving || !entityType || !primaryReference.trim() || !alias.trim()}>{saving ? 'Saving…' : 'Save preference'}</button></div>
    </form>
  </div>
{/if}

{#if showMergeModal}
  <div class="modal-backdrop normalization-modal-backdrop">
    <div class="modal merge-modal" role="dialog" aria-modal="true" aria-labelledby="merge-modal-title" tabindex="-1">
      <button class="close" type="button" on:click={closeMerge}>×</button><p class="micro-label">MERGE {selectedMerchants.length} {selectedEntityType} NAMES</p><h2 id="merge-modal-title">Choose the name to keep</h2>
      <p class="merge-modal-copy">Enter one name to use across all {selectedTransactionCount} selected transactions. The selected labels will remain as aliases.</p>
      <label class="merge-name-field">Preferred {typeLabel(selectedEntityType).toLowerCase()} name<input bind:value={preferredMerchantName} placeholder="e.g. HDFC Bank" autocomplete="off" /></label>
      <div class="merge-modal-selected"><span>Selected names</span><p>{selectedMerchants.map(merchant => merchant.name).join(' · ')}</p></div>
      {#if mergeError}<p class="form-error" role="alert">{mergeError}</p>{/if}
      <div class="modal-actions"><button class="secondary" type="button" on:click={closeMerge} disabled={merging}>Cancel</button><button class="primary" type="button" disabled={merging || !preferredMerchantName.trim()} on:click={confirmMerge}>{merging ? 'Merging…' : `Merge ${typeLabel(selectedEntityType).toLowerCase()}s`}</button></div>
    </div>
  </div>
{/if}
