<script>
  export let loan;
  export let money;
</script>

<article class="closed-loan-row" data-testid={`closed-loan-${loan.id}`}>
  <span>✓</span>
  <div>
    <strong>{loan.loanName}</strong>
    <small>{loan.lenderName} · {loan.loanType.replaceAll('_', ' ')}</small>
  </div>
  <div>
    <b>CLOSED</b>
    <small>{money(loan.monthlyEmiAmount)}/mo</small>
  </div>
</article>

<style>
  .closed-loan-row{display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:9px;align-items:center;margin-top:8px;padding:9px;border-radius:9px;background:#f2f4f0;color:#83907e;opacity:.82}
  .closed-loan-row>span{display:grid;place-items:center;width:24px;height:24px;border-radius:8px;background:#dfe5da;color:#6b7c65;font:800 11px Manrope}
  strong,small,b{display:block}.closed-loan-row strong{font-size:10px}.closed-loan-row small{margin-top:2px;font-size:8px;line-height:1.35}.closed-loan-row>div:last-child{text-align:right}.closed-loan-row b{font-size:8px;letter-spacing:.06em}
</style>
