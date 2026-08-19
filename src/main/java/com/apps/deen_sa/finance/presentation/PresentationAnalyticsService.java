package com.apps.deen_sa.finance.presentation;

import com.apps.deen_sa.dto.TimeRange;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PresentationAnalyticsService {
    private final StateChangeRepository changes;
    public PresentationAnalyticsService(StateChangeRepository changes) { this.changes = changes; }

    public PresentationDataset load(Long userId, TimeRange requested, VisualizationPlan plan, String timezone) {
        String uid = userId.toString(); ZoneId zone = zone(timezone);
        return switch (plan.type()) {
            case CALENDAR_HEATMAP -> new PresentationDataset(
                    map(changes.sumExpensesByLocalDay(uid, requested.start(), requested.end(), zone.getId())),
                    Map.of(), Map.of(), List.of(), List.of(), BigDecimal.ZERO);
            case PAIRED_BARS, SLOPE_CHART -> comparison(uid, zone);
            case SANKEY_MONEY_FLOW -> flow(uid, requested);
            case CATEGORY_TREEMAP -> hierarchy(uid, requested);
            default -> PresentationDataset.empty();
        };
    }

    private PresentationDataset comparison(String userId, ZoneId zone) {
        YearMonth current = YearMonth.now(zone); YearMonth previous = current.minusMonths(1);
        Instant currentStart = current.atDay(1).atStartOfDay(zone).toInstant();
        Instant currentEnd = current.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        Instant previousStart = previous.atDay(1).atStartOfDay(zone).toInstant();
        return new PresentationDataset(Map.of(), map(changes.sumByCategory(userId, currentStart, currentEnd, null)),
                map(changes.sumByCategory(userId, previousStart, currentStart, null)), List.of(), List.of(), BigDecimal.ZERO);
    }

    private PresentationDataset hierarchy(String userId, TimeRange range) {
        List<HierarchyPoint> points = changes.sumExpenseHierarchy(userId, range.start(), range.end()).stream()
                .map(row -> new HierarchyPoint(String.valueOf(row[0]), String.valueOf(row[1]), String.valueOf(row[2]), amount(row[3])))
                .toList();
        return new PresentationDataset(Map.of(), Map.of(), Map.of(), points, List.of(), BigDecimal.ZERO);
    }

    private PresentationDataset flow(String userId, TimeRange range) {
        List<FlowPoint> points = changes.sumExpenseFlow(userId, range.start(), range.end()).stream()
                .map(row -> new FlowPoint(String.valueOf(row[0]), String.valueOf(row[1]), amount(row[2]))).toList();
        BigDecimal income = changes.sumIncome(userId, range.start(), range.end());
        return new PresentationDataset(Map.of(), Map.of(), Map.of(), List.of(), points,
                income == null ? BigDecimal.ZERO : income);
    }

    private Map<String, BigDecimal> map(List<Object[]> rows) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(String.valueOf(row[0]), amount(row[1]))); return result;
    }
    private BigDecimal amount(Object value) { return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString()); }
    private ZoneId zone(String value) { try { return ZoneId.of(value); } catch (Exception ignored) { return ZoneId.of("Asia/Kolkata"); } }
}
