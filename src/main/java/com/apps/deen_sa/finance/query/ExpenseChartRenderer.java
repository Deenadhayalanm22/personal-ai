package com.apps.deen_sa.finance.query;

import com.apps.deen_sa.conversation.ResponseMedia;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.PieChart;
import org.knowm.xchart.PieChartBuilder;
import org.knowm.xchart.style.PieStyler;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.awt.Color;
import java.awt.Font;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ExpenseChartRenderer {
    private static final int MAX_CATEGORIES = 7;
    private static final Color INK = new Color(30, 41, 59);
    private static final Color SURFACE = new Color(248, 250, 252);
    private static final Color[] PALETTE = {
            new Color(37, 99, 235),   // blue
            new Color(13, 148, 136),  // teal
            new Color(245, 158, 11),  // amber
            new Color(139, 92, 246),  // violet
            new Color(236, 72, 153),  // pink
            new Color(34, 197, 94),   // green
            new Color(249, 115, 22),  // orange
            new Color(148, 163, 184)  // other
    };

    public ResponseMedia categoryDonut(String title, Map<String, BigDecimal> source, String locale) {
        Map<String, BigDecimal> values = displayValues(source);
        if (values.isEmpty()) return null;

        PieChart chart = new PieChartBuilder().width(1200).height(800).title(title).build();
        chart.getStyler().setAntiAlias(true);
        chart.getStyler().setChartBackgroundColor(SURFACE);
        chart.getStyler().setPlotBackgroundColor(SURFACE);
        chart.getStyler().setChartFontColor(INK);
        chart.getStyler().setChartPadding(36);
        chart.getStyler().setChartTitleFont(new Font("SansSerif", Font.BOLD, 34));
        chart.getStyler().setChartTitlePadding(24);
        chart.getStyler().setLegendFont(new Font("SansSerif", Font.PLAIN, 21));
        chart.getStyler().setLegendPosition(PieStyler.LegendPosition.OutsideE);
        chart.getStyler().setLegendBackgroundColor(Color.WHITE);
        chart.getStyler().setLegendBorderColor(new Color(226, 232, 240));
        chart.getStyler().setLegendPadding(18);
        chart.getStyler().setSeriesColors(PALETTE);
        chart.getStyler().setCircular(true);
        chart.getStyler().setDonutThickness(0.58);
        chart.getStyler().setPlotContentSize(0.76);
        chart.getStyler().setSliceBorderWidth(3);
        chart.getStyler().setLabelType(PieStyler.LabelType.Percentage);
        chart.getStyler().setLabelsFont(new Font("SansSerif", Font.BOLD, 19));
        chart.getStyler().setLabelsFontColorAutomaticEnabled(true);
        chart.getStyler().setForceAllLabelsVisible(false);
        chart.getStyler().setLabelsDistance(0.72);
        chart.getStyler().setDecimalPattern("0.#");
        chart.getStyler().setSumVisible(true);
        chart.getStyler().setSumFormat("₹%,.0f");
        chart.getStyler().setSumFont(new Font("SansSerif", Font.BOLD, 34));

        NumberFormat currency = NumberFormat.getCurrencyInstance(currencyLocale(locale));
        values.forEach((category, amount) ->
                chart.addSeries(category + "  " + currency.format(amount), amount.doubleValue()));
        try {
            return new ResponseMedia(BitmapEncoder.getBitmapBytes(chart, BitmapEncoder.BitmapFormat.PNG),
                    "image/png", "expense-breakdown.png");
        } catch (IOException e) {
            throw new IllegalStateException("Could not render expense chart", e);
        }
    }

    Map<String, BigDecimal> displayValues(Map<String, BigDecimal> source) {
        if (source == null || source.isEmpty()) return Map.of();
        List<Map.Entry<String, BigDecimal>> sorted = source.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> entry.getValue() != null && entry.getValue().signum() > 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        LinkedHashMap<String, BigDecimal> result = new LinkedHashMap<>();
        if (sorted.size() <= MAX_CATEGORIES) {
            sorted.forEach(entry -> result.put(entry.getKey(), entry.getValue()));
            return result;
        }
        sorted.subList(0, MAX_CATEGORIES).forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        BigDecimal other = sorted.subList(MAX_CATEGORIES, sorted.size()).stream()
                .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("Other", other);
        return result;
    }

    private Locale currencyLocale(String locale) {
        Locale requested = locale == null || locale.isBlank() ? Locale.forLanguageTag("en-IN")
                : Locale.forLanguageTag(locale.replace('_', '-'));
        return requested.getCountry().isBlank() ? new Locale.Builder().setLanguage(requested.getLanguage())
                .setRegion("IN").build() : requested;
    }
}
