package com.apps.deen_sa.formatter;

import com.apps.deen_sa.dto.QueryResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class QueryContextFormatter {

    public String describe(QueryResult qr) {

        List<String> parts = new ArrayList<>();

        if (qr.getSourceAccount() != null) {
            parts.add("paid using " + qr.getSourceAccount());
        }

        if (qr.getCategory() != null) {
            parts.add("in category " + qr.getCategory());
        }

        if (qr.getTimePeriod() != null) {
            parts.add("during " + qr.getTimePeriod().replace("_", " ").toLowerCase());
        }

        if (parts.isEmpty()) {
            return "for all expenses";
        }

        return "for expenses " + String.join(", ", parts);
    }
}
