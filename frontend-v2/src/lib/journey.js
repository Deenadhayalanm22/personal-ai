// FIN-EPIC-007: isolated sample facts; never a replacement for backend financial calculations.
export const days = [
  { date:'2026-09-13', kind:'quiet', activity:[], detail:'The sample has no recorded activity for this day. That does not prove zero spending.' },
  { date:'2026-09-14', kind:'quiet', activity:[], detail:'The sample has no recorded activity for this day. That does not prove zero spending.' },
  { date:'2026-09-15', kind:'bridge', amount:8000, activity:[['Loan payment',8000,'Confirmed sample repayment']], detail:'This stone represents one recorded repayment, not a percentage of the loan repaid. The bridge stays unfinished until loan closure is verified.' },
  { date:'2026-09-16', kind:'tree', amount:2000, activity:[['Investment contribution',2000,'First contribution recorded']], detail:'This tree marks recorded contributions. It does not represent investment returns, market value, or a prediction of future growth.' },
  { date:'2026-09-17', kind:'life', amount:1380, activity:[['Groceries',1200,'Food for home'],['Travel',180,'Getting across town']], detail:'Two recorded expenses total ₹1,380. These are recorded expenses, not a bank balance or a judgment about your spending.' },
  { date:'2026-09-18', kind:'shelter', amount:5000, activity:[['Car insurance savings',5000,'Money set aside · not an expense']], detail:'The sample plan has ₹10,000 recorded saved toward ₹20,000. Today’s ₹5,000 is part of that total. Insurance remains unpaid; this is savings progress only.' },
  { date:'2026-09-19', kind:'quiet', amount:null, activity:[], detail:'No activity recorded does not mean nothing was spent. No new insight is generated from an absence of records.' },
  { date:'2026-09-20', kind:'bridge', amount:8000, activity:[['Loan payment',8000,'Confirmed sample repayment']], detail:'Two sample repayments are now recorded. These are milestones, not an estimate of the remaining balance. The loan is not marked closed.' },
  { date:'2026-09-21', kind:'tree', amount:2000, activity:[['Investment contribution',2000,'Second contribution recorded']], detail:'Two contributions of ₹2,000 total ₹4,000 contributed. Tree growth reflects these recorded contributions, not investment performance. Visit 16 September to see the first sapling.' }
];
export const money = value => new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',minimumFractionDigits:0,maximumFractionDigits:2}).format(value);
export const dayLabel = date => new Intl.DateTimeFormat('en-IN',{day:'numeric',month:'long',timeZone:'UTC'}).format(new Date(date+'T12:00:00Z'));
export const weekday = date => new Intl.DateTimeFormat('en-IN',{weekday:'long',timeZone:'UTC'}).format(new Date(date+'T12:00:00Z'));
