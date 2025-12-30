package com.apps.deen_sa.simulation.fuzz;

import com.apps.deen_sa.core.state.StateContainerEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generate repeatable random financial scenarios using a provided seed.
 * Produces a list of ScenarioAction objects referencing container types.
 */
public class RandomFinancialScenarioGenerator {

    public static final String[] SOURCES = new String[] {"CREDIT_CARD", "BANK_ACCOUNT", "CASH"};

    private final Random rnd;
    private final int daysInMonth;
    private final Map<String, StateContainerEntity> containersByType;

    public RandomFinancialScenarioGenerator(long seed, int daysInMonth, Map<String, StateContainerEntity> containersByType) {
        this.rnd = new Random(seed);
        this.daysInMonth = daysInMonth;
        this.containersByType = containersByType;
    }

    public List<ScenarioAction> generate(int actionCount) {
        List<ScenarioAction> actions = new ArrayList<>();

        for (int i = 0; i < actionCount; i++) {
            int day = 1 + rnd.nextInt(daysInMonth);

            // Choose an action type: 0=expense, 1=payCreditCard, 2=transfer (map to expense+payment)
            int kind = rnd.nextInt(100);
            if (kind < 75) {
                // expense
                String source = pickSourceForExpense();
                long amount = pickAmountForSource(source);
                String desc = pickDescription();
                actions.add(new ScenarioAction(day, ActionType.EXPENSE, source, desc, amount, null));
            } else {
                // pay credit card
                StateContainerEntity cc = containersByType.get("CREDIT_CARD");
                if (cc != null) {
                    long amount = pickAmountForPayment(cc);
                    actions.add(new ScenarioAction(day, ActionType.PAY_CREDIT_CARD, "BANK_ACCOUNT", "pay", amount, cc.getName()));
                } else {
                    // fallback to expense
                    String source = pickSourceForExpense();
                    long amount = pickAmountForSource(source);
                    String desc = pickDescription();
                    actions.add(new ScenarioAction(day, ActionType.EXPENSE, source, desc, amount, null));
                }
            }
        }

        // sort by day for deterministic order of execution
        actions.sort((a, b) -> Integer.compare(a.day(), b.day()));
        return actions;
    }

    private String pickSourceForExpense() {
        // prefer credit card and bank for expenses
        int p = rnd.nextInt(100);
        if (p < 50 && containersByType.containsKey("CREDIT_CARD")) return "CREDIT_CARD";
        if (p < 85 && containersByType.containsKey("BANK_ACCOUNT")) return "BANK_ACCOUNT";
        return containersByType.containsKey("CASH") ? "CASH" : "BANK_ACCOUNT";
    }

    private long pickAmountForSource(String source) {
        StateContainerEntity c = containersByType.get(source);
        BigDecimal cap = c != null && c.getCapacityLimit() != null ? c.getCapacityLimit() : BigDecimal.valueOf(100000L);
        BigDecimal curr = c != null && c.getCurrentValue() != null ? c.getCurrentValue() : BigDecimal.ZERO;

        long max = Math.max(1, Math.min( (cap.longValue() + 10000), Math.max(100L, curr.longValue()+5000)) );
        // pick up to max/10 to avoid huge single expenses
        long amt = 50 + rnd.nextInt((int)Math.max(100, Math.min(max, 10000)));
        return amt;
    }

    private long pickAmountForPayment(StateContainerEntity cc) {
        BigDecimal outstanding = cc.getCurrentValue() == null ? BigDecimal.ZERO : cc.getCurrentValue();
        long base = Math.max(100L, Math.min( (outstanding.longValue()), 5000));
        return  base > 0 ? (50 + rnd.nextInt((int)base)) : (50 + rnd.nextInt(5000));
    }

    private String pickDescription() {
        String[] words = new String[]{"Groceries","Fuel","Dinner","Shopping","Mobile","Subscription","Rent"};
        return words[rnd.nextInt(words.length)];
    }

    public enum ActionType { EXPENSE, PAY_CREDIT_CARD }

    public static final class ScenarioAction {
        private final int day;
        private final ActionType type;
        private final String sourceAccount;
        private final String description;
        private final long amount;
        private final String targetName;

        public ScenarioAction(int day, ActionType type, String sourceAccount, String description, long amount, String targetName) {
            this.day = day;
            this.type = type;
            this.sourceAccount = sourceAccount;
            this.description = description;
            this.amount = amount;
            this.targetName = targetName;
        }

        public int day() { return day; }
        public ActionType type() { return type; }
        public String sourceAccount() { return sourceAccount; }
        public String description() { return description; }
        public long amount() { return amount; }
        public String targetName() { return targetName; }

        @Override
        public String toString() {
            return "Day " + day + ": " + type + " " + amount + " " + sourceAccount + (description != null ? " ("+description+")" : "") + (targetName!=null?" target="+targetName:"");
        }
    }
}
