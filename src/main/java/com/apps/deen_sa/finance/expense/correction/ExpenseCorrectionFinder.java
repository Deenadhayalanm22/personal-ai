package com.apps.deen_sa.finance.expense.correction;

import com.apps.deen_sa.finance.expense.ExpenseRecordStatus;
import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
class ExpenseCorrectionFinder {
    private final StateChangeRepository transactions;
    private final int pageSize;

    ExpenseCorrectionFinder(StateChangeRepository transactions,
                            @Value("${finance.expense-corrections.page-size:5}") int pageSize) {
        this.transactions = transactions;
        this.pageSize = Math.max(1, pageSize);
    }

    ExpenseBrowsePage find(String userId, ExpenseCorrectionState state) {
        Specification<StateChangeEntity> specification = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.equal(root.get("transactionType"), StateChangeTypeEnum.EXPENSE));
            predicates.add(cb.equal(root.get("recordStatus"), ExpenseRecordStatus.ACTIVE));
            if (state.getBeforeId() != null) predicates.add(cb.lessThan(root.get("id"), state.getBeforeId()));
            if (state.getPeriodStart() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), state.getPeriodStart()));
            if (state.getPeriodEnd() != null) predicates.add(cb.lessThan(root.get("timestamp"), state.getPeriodEnd()));
            if (state.getCategory() != null && !state.getCategory().isBlank())
                predicates.add(cb.equal(cb.lower(root.get("category")), state.getCategory().toLowerCase()));
            if (state.getSubcategory() != null && !state.getSubcategory().isBlank())
                predicates.add(cb.equal(cb.lower(root.get("subcategory")), state.getSubcategory().toLowerCase()));
            query.orderBy(cb.desc(root.get("id")));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        List<StateChangeEntity> rows = transactions.findAll(specification, PageRequest.of(0, pageSize + 1)).getContent();
        boolean more = rows.size() > pageSize;
        List<StateChangeEntity> visible = more ? rows.subList(0, pageSize) : rows;
        Long cursor = more && !visible.isEmpty() ? visible.getLast().getId() : null;
        return new ExpenseBrowsePage(List.copyOf(visible), cursor);
    }
}
