<!-- FIN-EPIC-005 / FIN-022: visual explanation of captured spending and separate settlement. -->
<script>
  export let bill;
  export let currency='INR';
  const money=value=>new Intl.NumberFormat('en-IN',{style:'currency',currency,minimumFractionDigits:2,maximumFractionDigits:2}).format(Number(value||0));
  const date=value=>value?new Intl.DateTimeFormat('en-IN',{day:'numeric',month:'short',year:'numeric',timeZone:'UTC'}).format(new Date(`${value}T00:00:00Z`)):'Not provided';
  const followingDay=value=>{if(!value)return null;const day=new Date(`${value}T00:00:00Z`);day.setUTCDate(day.getUTCDate()+1);return day.toISOString().slice(0,10);};
  $: generated=bill.statementGeneratedAt||followingDay(bill.statementEnd);
  $: monthName=new Intl.DateTimeFormat('en-IN',{month:'long',year:'numeric',timeZone:'UTC'}).format(new Date(`${bill.month}-01T00:00:00Z`));
  $: comparisonMax=Math.max(Number(bill.monthlyPurchaseAmount)||0,Number(bill.projectedAmount)||0,1);
  $: total=Math.max(0,Number(bill.projectedAmount)||0);
  $: paid=Math.max(0,Number(bill.paidAmount)||0);
  $: unmatched=Math.max(0,Number(bill.unmatchedPaymentAmount ?? (paid-total))||0);
  $: percentage=total>0?Math.min(100,paid/total*100):0;
</script>
<div class="bill-visual">
  {#if bill.monthlyPurchaseAmount!=null}
    <div class="bill-comparison" role="group" aria-label={`${bill.cardName} monthly spending and bill comparison`}>
      <p class="comparison-title">Two views of {monthName}</p>
      <div><span>Purchases in {monthName}</span><strong>{money(bill.monthlyPurchaseAmount)}</strong><div class="comparison-track" aria-hidden="true"><span class="purchase-bar" style={`width:${Math.max(0,Number(bill.monthlyPurchaseAmount)||0)/comparisonMax*100}%`}></span></div><small>Included in this month’s spending</small></div>
      <div><span>Bill due in {monthName}</span><strong>{money(total)}</strong><div class="comparison-track" aria-hidden="true"><span class="due-bar" style={`width:${total/comparisonMax*100}%`}></span></div><small>Purchases from the billing period below</small></div>
    </div>
  {/if}
  <ol class="bill-cycle" aria-label={`${bill.cardName} billing cycle`}>
    <li><span class="cycle-dot" aria-hidden="true"></span><span class="cycle-label">Purchases</span><strong>{date(bill.periodStart)}<br/>– {date(bill.statementEnd)}</strong><small>Counted as spending on purchase dates</small></li>
    <li class:cycle-reached={bill.statementClosed}><span class="cycle-dot" aria-hidden="true"></span><span class="cycle-label">Bill generates</span><strong>{date(generated)}</strong><small>{bill.statementClosed?'Purchase period closed':'Amount can change until this date'}</small></li>
    <li><span class="cycle-dot" aria-hidden="true"></span><span class="cycle-label">Payment due</span><strong>{date(bill.dueDate)}</strong><small>Payment clears this bill</small></li>
  </ol>
  <div class="bill-amounts"><div><span>{bill.statementClosed?'Captured bill':'Projected bill'}</span><strong>{money(total)}</strong></div><div><span>Paid</span><strong>{money(paid)}</strong></div><div><span>Still due</span><strong>{money(bill.remaining)}</strong></div></div>
  {#if unmatched>0}<div class="bill-unmatched" role="note"><strong>Unmatched payment · {money(unmatched)}</strong><p>You paid more than the purchases captured here. Keep this difference for review: it may include missing purchases, fees or an earlier balance. No expense is added automatically.</p></div>{/if}
  {#if bill.statementClosed && total>0}
    <div class="bill-progress-label"><span>Payment progress</span><strong>{Math.round(percentage)}% paid</strong></div>
    <div class="bill-progress" role="progressbar" aria-label={`${bill.cardName} bill payment progress`} aria-valuemin="0" aria-valuemax="100" aria-valuenow={percentage} aria-valuetext={`${money(paid)} paid of ${money(total)}`}><span style={`width:${percentage}%`}></span></div>
  {:else if bill.statementClosed}<p class="bill-projection-note">No purchases captured for this bill. You can still record the actual payment.</p>
  {:else}<p class="bill-projection-note">Building the next bill from recorded purchases. Payment progress begins after generation.</p>{/if}
</div>
<style>
.bill-unmatched{margin-top:1rem;padding:12px;border-radius:12px;background:#fff4d5;font-size:11px}.bill-unmatched strong{font-family:Manrope,sans-serif}.bill-unmatched p{margin:.5rem 0 0;line-height:1.5;color:var(--muted)}
.bill-comparison{display:grid;grid-template-columns:1fr 1fr;gap:1rem;margin-bottom:1.5rem}.comparison-title{grid-column:1/-1;font-size:11px;font-weight:700;margin:0}.bill-comparison>div>span,.bill-comparison small{display:block;font-size:10px;color:var(--muted)}.bill-comparison strong{display:block;margin:.4rem 0}.comparison-track{height:8px;background:#e2e8dd;border-radius:8px;overflow:hidden;margin:.5rem 0}.comparison-track span{display:block;height:100%;border-radius:inherit}.purchase-bar{background:#678a9c}.due-bar{background:#b39a53}.bill-visual{margin-top:1.2rem}.bill-cycle{list-style:none;padding:0;margin:0 0 1.4rem;display:grid;grid-template-columns:1.4fr 1fr 1fr;gap:1rem}.bill-cycle li{position:relative;padding-top:1.4rem;min-width:0}.bill-cycle li::before{content:'';position:absolute;top:5px;left:0;right:-1rem;height:2px;background:#d8e2d9}.bill-cycle li:last-child::before{right:0}.cycle-dot{position:absolute;top:0;left:0;width:12px;height:12px;border-radius:50%;background:#fff;border:2px solid #78967d;box-sizing:border-box}.cycle-reached .cycle-dot{background:var(--green);border-color:var(--green)}.cycle-label{display:block;font-size:9px;font-weight:700;text-transform:uppercase;letter-spacing:.05em;color:var(--muted)}.bill-cycle strong{display:block;font-size:11px;margin:.4rem 0;line-height:1.5}.bill-cycle small{display:block;color:var(--muted);font-size:9px;line-height:1.5}.bill-amounts{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:.7rem;padding:1rem;background:#f3f6ed;border-radius:12px}.bill-amounts span{display:block;font-size:10px;color:var(--muted);margin-bottom:.35rem}.bill-amounts strong{font-size:13px;overflow-wrap:anywhere}.bill-amounts>div:last-child strong{color:var(--green)}.bill-progress-label{display:flex;justify-content:space-between;font-size:10px;margin:.9rem 0 .4rem;color:var(--muted)}.bill-progress{height:9px;border-radius:10px;overflow:hidden;background:#e0e6da}.bill-progress span{display:block;height:100%;background:var(--green);border-radius:inherit}.bill-projection-note{font-size:10px;color:var(--muted);line-height:1.5;margin:.9rem 0 0}@media(max-width:500px){.bill-comparison{grid-template-columns:1fr}.comparison-title{grid-column:auto}.bill-cycle{grid-template-columns:1fr;gap:0}.bill-cycle li{padding:0 0 1rem 1.5rem}.bill-cycle li::before{top:6px;bottom:0;left:5px;right:auto;width:2px;height:auto}.bill-cycle li:last-child::before{display:none}.cycle-dot{top:3px}.bill-amounts{gap:.4rem;padding:11px}.bill-amounts strong{font-size:11px}}
.bill-comparison strong,.bill-cycle strong,.bill-amounts strong,.bill-progress-label strong{font-family:Manrope,sans-serif;font-weight:800}.bill-comparison strong{font-size:13px}.bill-cycle strong{font-size:11px}.bill-amounts strong{font-size:13px}.bill-progress-label strong{font-size:10px}
</style>
