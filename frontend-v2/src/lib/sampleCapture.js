// FIN-EPIC-007: deliberately limited local demo, not AI extraction or a financial API.
export function previewExpenses(text) {
  const value = text.trim();
  if (!value) return { error: 'Tell us an expense and its amount, for example Lunch ₹180.' };
  if (/[?]/.test(value) || /^(why|how|what|when|where|can|could|should|is|are|do|did)\b/i.test(value))
    return { error: 'That sounds like a question. Questions will go to the assistant when it is connected; nothing was added.' };
  if (/\b(transfer|invest\w*|sip|loan|emi|refund|income|salary|yesterday|today|tomorrow|paid|owe|not|maybe|would|will|plan)\b/i.test(value))
    return { error: 'This demo only previews simple expense labels and amounts for the selected date. Other payment types, dates and conversational wording need the future assistant. Nothing was added.' };
  const parts = value.split(/\s+and\s+|[;\n]|,\s*(?=[A-Za-z])/i).map(s => s.trim());
  if (parts.length > 8) return { error: 'Try up to eight expenses at a time in this demo.' };
  const rows = [];
  for (const part of parts) {
    const match = part.match(/^([A-Za-z][A-Za-z '&-]{0,49}?)\s+(?:₹\s*|rs\.?\s*|inr\s*)?(\d+(?:,\d{3})*(?:\.\d{1,2})?)$/i);
    const amount = match ? Number(match[2].replaceAll(',', '')) : NaN;
    if (!match || !Number.isFinite(amount) || amount <= 0 || amount > 10000000)
      return { error: 'Please use a label and positive amount for each expense, such as Lunch ₹180 and Auto ₹90. Nothing was added; review the whole message.' };
    rows.push({ label: match[1].trim(), amount });
  }
  return { rows };
}
