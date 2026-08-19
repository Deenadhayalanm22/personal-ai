package com.apps.deen_sa.finance.query;

import com.apps.deen_sa.conversation.ResponseMedia;
import com.apps.deen_sa.dto.ExpenseSummary;
import com.apps.deen_sa.finance.presentation.PresentationMood;
import com.apps.deen_sa.finance.presentation.VisualizationPlan;
import com.apps.deen_sa.finance.presentation.VisualizationType;
import com.apps.deen_sa.finance.budget.BudgetProgress;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.PieChart;
import org.knowm.xchart.PieChartBuilder;
import org.knowm.xchart.style.PieStyler;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
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

    /** Transitional registry entry point. Renderers can be split into independent strategies without changing callers. */
    public ResponseMedia render(VisualizationPlan plan, String title, ExpenseSummary summary, String locale) {
        Map<String, BigDecimal> categories = summary == null ? Map.of() : summary.getSpendByCategory();
        return switch (plan.type()) {
            case RANKED_HORIZONTAL_BARS -> rankedBars(title, categories, plan.mood(), locale);
            case SPENDING_REPORT_CARD -> reportCard(title, summary, plan.mood(), locale);
            // These data contracts are catalogued but the current ledger does not yet expose their required projections.
            // A truthful category ranking is preferable to fabricating daily, comparison or flow data.
            default -> rankedBars(title, categories, plan.mood(), locale);
        };
    }

    public ResponseMedia reportCard(String title, ExpenseSummary summary, PresentationMood mood, String locale) {
        Map<String, BigDecimal> values = displayValues(summary == null ? null : summary.getSpendByCategory());
        if (values.isEmpty()) return null;
        int width = 1080, height = 1180;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image);
        Color accent = accent(mood);
        g.setColor(new Color(245, 247, 250)); g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE); g.fillRoundRect(54, 46, 972, 1080, 38, 38);
        g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 42)); g.drawString(title, 96, 125);
        BigDecimal total = summary.getTotalSpend() == null
                ? values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add) : summary.getTotalSpend();
        g.setColor(accent); g.setFont(new Font("SansSerif", Font.BOLD, 76));
        g.drawString(money(total, locale), 96, 230);
        g.setColor(new Color(100, 116, 139)); g.setFont(new Font("SansSerif", Font.PLAIN, 28));
        g.drawString("Total spent", 98, 272);
        int y = 350, index = 0;
        for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            if (index++ == 5) break;
            double ratio = total.signum() == 0 ? 0 : entry.getValue().doubleValue() / total.doubleValue();
            g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 28)); g.drawString(entry.getKey(), 96, y);
            String amount = money(entry.getValue(), locale);
            int amountWidth = g.getFontMetrics().stringWidth(amount); g.drawString(amount, 930 - amountWidth, y);
            g.setColor(new Color(226, 232, 240)); g.fillRoundRect(96, y + 24, 834, 22, 22, 22);
            g.setColor(accent); g.fillRoundRect(96, y + 24, Math.max(8, (int) (834 * ratio)), 22, 22, 22);
            g.setColor(new Color(100, 116, 139)); g.setFont(new Font("SansSerif", Font.PLAIN, 23));
            g.drawString(Math.round(ratio * 100) + "%", 96, y + 80);
            y += 140;
        }
        Map.Entry<String, BigDecimal> top = values.entrySet().iterator().next();
        g.setColor(new Color(241, 245, 249)); g.fillRoundRect(86, 1000, 908, 86, 22, 22);
        g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 25));
        g.drawString("Largest category: " + top.getKey() + " · " + money(top.getValue(), locale), 116, 1054);
        g.dispose();
        return png(image, "spending-report-card.png");
    }

    public ResponseMedia rankedBars(String title, Map<String, BigDecimal> source, PresentationMood mood, String locale) {
        Map<String, BigDecimal> values = displayValues(source);
        if (values.isEmpty()) return null;
        int width = 1200, height = 250 + values.size() * 105;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image); Color accent = accent(mood);
        g.setColor(SURFACE); g.fillRect(0, 0, width, height);
        g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 40)); g.drawString(title, 64, 76);
        BigDecimal max = values.values().iterator().next(); int y = 145;
        for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 23)); g.drawString(entry.getKey(), 64, y);
            String amount = money(entry.getValue(), locale); int amountWidth = g.getFontMetrics().stringWidth(amount);
            g.drawString(amount, 1136 - amountWidth, y);
            g.setColor(new Color(226, 232, 240)); g.fillRoundRect(64, y + 18, 1072, 28, 20, 20);
            int bar = (int) (1072 * entry.getValue().doubleValue() / max.doubleValue());
            g.setColor(accent); g.fillRoundRect(64, y + 18, Math.max(8, bar), 28, 20, 20); y += 105;
        }
        g.dispose(); return png(image, "ranked-spending.png");
    }

    public ResponseMedia accountStack(String title, Map<String, BigDecimal> source,
                                      PresentationMood mood, String locale) {
        if (source == null || source.isEmpty()) return null;
        List<Map.Entry<String, BigDecimal>> values = source.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .toList();
        if (values.isEmpty()) return null;
        int width = 1080, height = 210 + values.size() * 150;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image); Color accent = accent(mood);
        g.setColor(new Color(245, 247, 250)); g.fillRect(0, 0, width, height);
        g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 40)); g.drawString(title, 64, 75);
        BigDecimal max = values.stream().map(entry -> entry.getValue().abs()).max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);
        if (max.signum() == 0) max = BigDecimal.ONE;
        int y = 125;
        for (Map.Entry<String, BigDecimal> entry : values) {
            g.setColor(Color.WHITE); g.fillRoundRect(52, y, 976, 120, 28, 28);
            g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 25)); g.drawString(entry.getKey(), 84, y + 40);
            String amount = money(entry.getValue(), locale); g.setFont(new Font("SansSerif", Font.BOLD, 28));
            g.drawString(amount, 985 - g.getFontMetrics().stringWidth(amount), y + 42);
            g.setColor(new Color(226, 232, 240)); g.fillRoundRect(84, y + 72, 900, 18, 18, 18);
            int bar = (int) (900 * entry.getValue().abs().doubleValue() / max.doubleValue());
            g.setColor(entry.getValue().signum() < 0 ? new Color(220, 38, 38) : accent);
            g.fillRoundRect(84, y + 72, Math.max(8, bar), 18, 18, 18);
            y += 150;
        }
        g.dispose(); return png(image, "account-stack.png");
    }

    public ResponseMedia budgetProgress(String title, List<BudgetProgress> budgets,
                                        PresentationMood mood, String locale) {
        int rows = budgets == null ? 0 : budgets.size();
        int width = 1080, height = Math.max(420, 190 + rows * 150);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image); Color accent = accent(mood);
        g.setColor(new Color(245, 247, 250)); g.fillRect(0, 0, width, height);
        g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 40)); g.drawString(title, 64, 75);
        if (rows == 0) {
            g.setColor(Color.WHITE); g.fillRoundRect(54, 120, 972, 230, 32, 32);
            g.setColor(accent); g.setFont(new Font("SansSerif", Font.BOLD, 34));
            g.drawString("No active budgets yet", 96, 205);
            g.setColor(new Color(100, 116, 139)); g.setFont(new Font("SansSerif", Font.PLAIN, 25));
            g.drawString("Try: Set my monthly groceries budget to ₹10,000", 96, 270);
            g.dispose(); return png(image, "budget-progress.png");
        }
        int y = 125;
        for (BudgetProgress budget : budgets) {
            BigDecimal limit = budget.limit() == null ? BigDecimal.ZERO : budget.limit();
            BigDecimal spent = budget.spent() == null ? BigDecimal.ZERO : budget.spent();
            double ratio = limit.signum() <= 0 ? 0 : spent.doubleValue() / limit.doubleValue();
            g.setColor(Color.WHITE); g.fillRoundRect(52, y, 976, 120, 28, 28);
            g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 25)); g.drawString(budget.category(), 84, y + 38);
            String amounts = money(spent, locale) + " / " + money(limit, locale);
            g.setFont(new Font("SansSerif", Font.BOLD, 23)); g.drawString(amounts,
                    985 - g.getFontMetrics().stringWidth(amounts), y + 40);
            g.setColor(new Color(226, 232, 240)); g.fillRoundRect(84, y + 72, 900, 18, 18, 18);
            g.setColor(ratio > 1 ? new Color(220, 38, 38) : ratio >= .8 ? new Color(217, 119, 6) : accent);
            g.fillRoundRect(84, y + 72, Math.max(8, (int) (900 * Math.min(1, ratio))), 18, 18, 18);
            y += 150;
        }
        g.dispose(); return png(image, "budget-progress.png");
    }

    private Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(2)); return g;
    }

    private Color accent(PresentationMood mood) {
        return switch (mood) {
            case CELEBRATORY -> new Color(22, 163, 74);
            case CONCERNED -> new Color(217, 119, 6);
            case FRUSTRATED -> new Color(220, 38, 38);
            case CURIOUS -> new Color(124, 58, 237);
            default -> new Color(37, 99, 235);
        };
    }

    private String money(BigDecimal value, String locale) {
        NumberFormat format = NumberFormat.getCurrencyInstance(currencyLocale(locale));
        format.setMaximumFractionDigits(0); return format.format(value);
    }

    private ResponseMedia png(BufferedImage image, String filename) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output); return new ResponseMedia(output.toByteArray(), "image/png", filename);
        } catch (IOException e) { throw new IllegalStateException("Could not render expense graphic", e); }
    }

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
