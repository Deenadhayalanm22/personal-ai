<!-- FIN-EPIC-003/005/006: read-only financial chat evidence. -->
<script>
  export let source;
  const labels = { name: 'Name', day: 'Date', count: 'Records', total: 'Total', average: 'Average', largest: 'Largest', amount: 'Amount', monthly_emi_amount: 'Monthly EMI', invested_amount: 'Invested', planned_contribution: 'Planned contribution', planned_amount: 'Planned', paid_amount: 'Paid', actual_amount: 'Actual payment', unit_price: 'Unit price', recorded_saved: 'Recorded savings' };
  function title(key) { return labels[key] || key.replaceAll('_', ' ').replace(/^./, letter => letter.toUpperCase()); }
  function columns(rows) { return Object.keys(rows[0] || {}).filter(key => key !== 'id'); }
  function money(value) { return value == null ? 'Unavailable' : new Intl.NumberFormat(undefined, { style: 'currency', currency: source.currency || 'INR', maximumFractionDigits: 2 }).format(value); }
  function cell(key, value) {
    if (value == null) return 'Not recorded';
    if (['total', 'average', 'largest', 'amount', 'unit_price', 'original_principal', 'monthly_emi_amount', 'planned_contribution', 'recorded_saved', 'savings_used'].includes(key) || key.endsWith('_amount')) return money(value);
    if (key === 'weekday') return ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][Number(value) - 1] || value;
    return typeof value === 'boolean' ? (value ? 'Yes' : 'No') : value;
  }
  function lastDate(endDate) { const date = new Date(`${endDate}T00:00:00Z`); date.setUTCDate(date.getUTCDate() - 1); return date.toISOString().slice(0, 10); }
  function gap(value) { return value == null ? 'Exact comparison unavailable' : `${money(Math.abs(value))} ${value < 0 ? 'shortfall' : 'remaining'}`; }
</script>

<div class="evidence">
  {#if source.kind === 'plan' || source.kind === 'scenario'}
    <strong>{source.kind === 'scenario' ? 'What-if scenario' : 'Monthly plan'} · {source.month}</strong>
    {#if source.kind === 'scenario'}<span class="hypothetical">Hypothetical only · No records changed</span>{/if}
    <div class="table-wrap"><table aria-label={source.kind === 'scenario' ? 'Scenario comparison' : 'Monthly plan totals'}>
      <thead><tr><th>Measure</th><th>Recorded plan</th>{#if source.kind === 'scenario'}<th>What-if</th>{/if}</tr></thead>
      <tbody><tr><th>Planned total</th><td>{money(source.baselineTotal)}</td>{#if source.kind === 'scenario'}<td>{money(source.proposedTotal)}</td>{/if}</tr>
      <tr><th>Against salary estimate</th><td>{gap(source.baselineAfterIncome)}</td>{#if source.kind === 'scenario'}<td>{gap(source.proposedAfterIncome)}</td>{/if}</tr></tbody>
    </table></div>
    {#if source.incomeStatus !== 'EXACT_MONTHLY_ESTIMATE'}<span>Share an exact, regular monthly salary estimate in Your money to compare it with this plan.</span>{/if}
    {#each source.items as item}
      <div class="plan-item"><strong>{item.label}</strong><span>{item.dueDate || 'Date not recorded'} · {money(item.baseline)}{#if item.reduction > 0} → {money(item.proposed)}{/if}</span><small>{item.condition}</small></div>
    {/each}
    {#each source.limitations as note}<p class="note">{note}</p>{/each}
  {:else}
    {#if source.kind === 'records'}
      <strong>{title(source.module)} · {source.view === 'history' ? 'Recorded history' : 'Recorded data'}</strong>
      <span>{source.matchingCount} matching records</span>
      <p class="note">{source.note}</p>
    {:else}
      <strong>{source.query.startDate} – {lastDate(source.query.endDate)}</strong>
      <span>{source.matchingCount} matching records · {money(source.matchingTotal)}</span>
      {#if source.query.groupBy.length}<span>Grouped by {source.query.groupBy.join(', ')}</span>{/if}
      {#each source.query.filters as filter}<span>{title(filter.field)} {{ eq: 'is', ne: 'excludes', contains: 'contains', gte: 'at least', lte: 'at most' }[filter.operator]} {filter.value}</span>{/each}
    {/if}
    {#if source.truncated}<span>Showing {source.rows.length} results; more records are available.</span>{/if}
    {#if source.rows.length}<div class="table-wrap"><table><thead><tr>{#each columns(source.rows) as column}<th>{title(column)}</th>{/each}</tr></thead><tbody>{#each source.rows as row}<tr>{#each columns(source.rows) as column}<td>{cell(column, row[column])}</td>{/each}</tr>{/each}</tbody></table></div>{/if}
  {/if}
</div>

<style>
  .evidence{display:grid;gap:7px;margin-top:12px;overflow-wrap:anywhere;font-size:11px}.table-wrap{max-height:220px;overflow:auto;background:#ffffffa0;border-radius:6px}table{border-collapse:collapse;font-size:10px;width:100%}th,td{text-align:left;padding:7px;border-bottom:1px solid #dce4d8;white-space:nowrap}th{font-weight:600}.hypothetical{font-weight:600;color:#765b27}.plan-item{display:grid;gap:4px;padding:8px 0;border-bottom:1px solid #dce4d8}.plan-item small,.note{font-size:10px;line-height:1.5;color:#5b6b5c}.note{margin:0}
</style>
