package com.apps.deen_sa.finance.query;

import com.apps.deen_sa.conversation.ResponseMedia;
import com.apps.deen_sa.dto.ExpenseSummary;
import com.apps.deen_sa.finance.presentation.PresentationMood;
import com.apps.deen_sa.finance.presentation.VisualizationPlan;
import com.apps.deen_sa.finance.presentation.VisualizationType;
import com.apps.deen_sa.finance.budget.BudgetProgress;
import com.apps.deen_sa.finance.presentation.PresentationDataset;
import com.apps.deen_sa.finance.presentation.FlowPoint;
import com.apps.deen_sa.finance.presentation.HierarchyPoint;
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
    public ResponseMedia render(VisualizationPlan plan, String title, ExpenseSummary summary,
                                PresentationDataset dataset, String locale) {
        Map<String, BigDecimal> categories = summary == null ? Map.of() : summary.getSpendByCategory();
        return switch (plan.type()) {
            case RANKED_HORIZONTAL_BARS -> rankedBars(title, categories, plan.mood(), locale);
            case SPENDING_REPORT_CARD -> reportCard(title, summary, plan.mood(), locale);
            case CALENDAR_HEATMAP -> calendarHeatmap("Daily spending", dataset.dailySpend(), plan.mood(), locale);
            case PAIRED_BARS, SLOPE_CHART -> comparisonBars("This month vs last month",
                    dataset.currentCategories(), dataset.previousCategories(), plan.mood(), locale);
            case SANKEY_MONEY_FLOW -> moneyFlow("Where your money went", dataset.flows(),
                    dataset.incomeTotal(), plan.mood(), locale);
            case CATEGORY_TREEMAP -> categoryTreemap("Category hierarchy", dataset.hierarchy(), plan.mood(), locale);
            case ACCOUNT_STACK, BUDGET_PROGRESS_BARS -> null;
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

    public ResponseMedia calendarHeatmap(String title, Map<String, BigDecimal> daily,
                                         PresentationMood mood, String locale) {
        if (daily == null || daily.isEmpty()) return emptyGraphic(title, "No daily spending in this period", "daily-heatmap.png", mood);
        List<java.time.LocalDate> dates = daily.keySet().stream().map(java.time.LocalDate::parse).sorted().toList();
        java.time.YearMonth month = java.time.YearMonth.from(dates.getLast());
        BigDecimal max = daily.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        int width = 1080, height = 760; BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image); g.setColor(SURFACE); g.fillRect(0, 0, width, height);
        g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 40)); g.drawString(title + " · " + month, 58, 72);
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        for (int i = 0; i < 7; i++) g.drawString(days[i], 62 + i * 142, 125);
        java.time.LocalDate first = month.atDay(1); int offset = first.getDayOfWeek().getValue() - 1;
        Color base = accent(mood);
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            int cell = offset + day - 1, col = cell % 7, row = cell / 7;
            BigDecimal amount = daily.getOrDefault(month.atDay(day).toString(), BigDecimal.ZERO);
            double intensity = max.signum() == 0 ? 0 : amount.doubleValue() / max.doubleValue();
            Color fill = intensity == 0 ? new Color(226, 232, 240)
                    : blend(Color.WHITE, base, .25 + .75 * intensity);
            int x = 54 + col * 142, y = 150 + row * 100;
            g.setColor(fill); g.fillRoundRect(x, y, 116, 76, 18, 18);
            g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 18)); g.drawString(String.valueOf(day), x + 12, y + 26);
            if (amount.signum() > 0) { g.setFont(new Font("SansSerif", Font.PLAIN, 15)); g.drawString(money(amount, locale), x + 12, y + 56); }
        }
        g.dispose(); return png(image, "daily-heatmap.png");
    }

    public ResponseMedia comparisonBars(String title, Map<String, BigDecimal> current,
                                        Map<String, BigDecimal> previous, PresentationMood mood, String locale) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>(); keys.addAll(current.keySet()); keys.addAll(previous.keySet());
        if (keys.isEmpty()) return emptyGraphic(title, "No spending in either month", "period-comparison.png", mood);
        List<String> categories = keys.stream().sorted(Comparator.comparing((String key) ->
                current.getOrDefault(key, BigDecimal.ZERO).max(previous.getOrDefault(key, BigDecimal.ZERO))).reversed()).limit(7).toList();
        BigDecimal max = categories.stream().flatMap(key -> java.util.stream.Stream.of(
                current.getOrDefault(key, BigDecimal.ZERO), previous.getOrDefault(key, BigDecimal.ZERO)))
                .max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        int width = 1200, height = 190 + categories.size() * 115; BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image); g.setColor(SURFACE); g.fillRect(0, 0, width, height);
        g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 40)); g.drawString(title, 60, 70);
        g.setFont(new Font("SansSerif", Font.PLAIN, 20)); g.setColor(new Color(100,116,139)); g.drawString("Previous", 810, 108);
        g.setColor(accent(mood)); g.drawString("Current", 970, 108); int y = 150;
        for (String category : categories) {
            g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 22)); g.drawString(category, 60, y + 28);
            BigDecimal old = previous.getOrDefault(category, BigDecimal.ZERO), now = current.getOrDefault(category, BigDecimal.ZERO);
            int oldBar = max.signum() == 0 ? 0 : (int)(700 * old.doubleValue()/max.doubleValue());
            int nowBar = max.signum() == 0 ? 0 : (int)(700 * now.doubleValue()/max.doubleValue());
            g.setColor(new Color(148,163,184)); g.fillRoundRect(390, y, Math.max(5, oldBar), 24, 14, 14);
            g.setColor(accent(mood)); g.fillRoundRect(390, y + 35, Math.max(5, nowBar), 24, 14, 14); y += 115;
        }
        g.dispose(); return png(image, "period-comparison.png");
    }

    public ResponseMedia moneyFlow(String title, List<FlowPoint> flows, BigDecimal income,
                                   PresentationMood mood, String locale) {
        if (flows == null || flows.isEmpty()) return emptyGraphic(title, "No expense flows in this period", "money-flow.png", mood);
        int width = 1200, height = Math.max(650, 220 + flows.size() * 72); BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image); g.setColor(SURFACE); g.fillRect(0, 0, width, height);
        g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 40)); g.drawString(title, 55, 68);
        g.setFont(new Font("SansSerif", Font.BOLD, 25)); g.setColor(accent(mood));
        g.drawString("Income " + money(income == null ? BigDecimal.ZERO : income, locale), 55, 125);
        BigDecimal max = flows.stream().map(FlowPoint::amount).max(BigDecimal::compareTo).orElse(BigDecimal.ONE); int y = 180;
        for (FlowPoint flow : flows) {
            int stroke = Math.max(3, (int)(24 * flow.amount().doubleValue()/max.doubleValue()));
            g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 21)); g.drawString(flow.account(), 55, y + 8);
            g.setColor(accent(mood)); g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(330, y, 820, y); g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 21));
            g.drawString(flow.category() + " · " + money(flow.amount(), locale), 850, y + 8); y += 72;
        }
        g.dispose(); return png(image, "money-flow.png");
    }

    public ResponseMedia categoryTreemap(String title, List<HierarchyPoint> points,
                                         PresentationMood mood, String locale) {
        if (points == null || points.isEmpty()) return emptyGraphic(title, "No category hierarchy in this period", "category-treemap.png", mood);
        List<HierarchyPoint> visible = points.stream().limit(12).toList();
        BigDecimal total = visible.stream().map(HierarchyPoint::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        int width = 1200, height = 800; BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image); g.setColor(SURFACE); g.fillRect(0, 0, width, height);
        g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 40)); g.drawString(title, 55, 68);
        int x = 50, y = 110, usable = 1100, consumed = 0, index = 0;
        for (HierarchyPoint point : visible) {
            int areaWidth = index == visible.size() - 1 ? usable - consumed
                    : Math.max(90, (int)(usable * point.amount().doubleValue()/total.doubleValue()));
            if (consumed + areaWidth > usable || x + areaWidth > 1150) { x = 50; y += 235; consumed = 0; areaWidth = Math.min(usable, areaWidth); }
            Color color = PALETTE[index % PALETTE.length]; g.setColor(blend(Color.WHITE, color, .82));
            g.fillRoundRect(x, y, areaWidth - 8, 210, 24, 24); g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 20)); g.drawString(clip(point.category(), 18), x + 16, y + 38);
            g.setFont(new Font("SansSerif", Font.PLAIN, 17)); g.drawString(clip(point.subcategory(), 20), x + 16, y + 70);
            g.drawString(clip(point.merchant(), 20), x + 16, y + 100); g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.drawString(money(point.amount(), locale), x + 16, y + 170); x += areaWidth; consumed += areaWidth; index++;
        }
        g.dispose(); return png(image, "category-treemap.png");
    }

    private ResponseMedia emptyGraphic(String title, String message, String filename, PresentationMood mood) {
        BufferedImage image = new BufferedImage(1080, 420, BufferedImage.TYPE_INT_ARGB); Graphics2D g = graphics(image);
        g.setColor(SURFACE); g.fillRect(0, 0, 1080, 420); g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 40));
        g.drawString(title, 58, 76); g.setColor(accent(mood)); g.fillRoundRect(54, 125, 972, 210, 30, 30);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 28)); g.drawString(message, 92, 235); g.dispose();
        return png(image, filename);
    }

    private Color blend(Color from, Color to, double ratio) {
        double r = Math.max(0, Math.min(1, ratio)); return new Color((int)(from.getRed()*(1-r)+to.getRed()*r),
                (int)(from.getGreen()*(1-r)+to.getGreen()*r), (int)(from.getBlue()*(1-r)+to.getBlue()*r));
    }
    private String clip(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 1) + "…"; }

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
