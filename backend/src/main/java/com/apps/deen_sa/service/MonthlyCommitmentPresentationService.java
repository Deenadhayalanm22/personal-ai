package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Presents the live monthly commitment without stored expense stories. */
@Service
@RequiredArgsConstructor
public class MonthlyCommitmentPresentationService {
    private final MonthlyCommitmentCardService monthlyCommitment;
    private final MonthlyPaymentOverviewService overview;

    public MonthlyCommitmentApiResponse monthlyForWeb(AppUserEntity user, YearMonth month) {
        CommitmentPresentation current = monthlyCommitment.currentFor(user);
        CommitmentPresentation selected = current != null && current.period() != null
                && YearMonth.from(current.period().startDate()).equals(month) ? current : null;
        return new MonthlyCommitmentApiResponse(month.toString(), user.getCurrency(), user.getTimezone(), selected, selected == null ? null : overview.forMonth(user, month));
    }
    public record MonthlyCommitmentApiResponse(String month, String currency, String timezone, CommitmentPresentation commitment, MonthlyPaymentOverviewService.Overview overview) {
        public MonthlyCommitmentApiResponse(String month, String currency, String timezone, CommitmentPresentation commitment) { this(month, currency, timezone, commitment, null); }
        static MonthlyCommitmentApiResponse empty(YearMonth month, AppUserEntity user) {
            return new MonthlyCommitmentApiResponse(month.toString(), user.getCurrency(), user.getTimezone(), null, null);
        }
    }
    public record CommitmentPresentation(String storyId, String storyType, int templateVersion, Instant generatedAt,
            PeriodDto period, CardFace cardFace, List<CardDto> cards, EvidenceDto evidence) { }
    public record PeriodDto(String type, LocalDate startDate, LocalDate endDate, String displayLabel) { }
    public record CardFace(String heading, String displayValue, String theme) { }
    public record CardDto(String cardId, int sequence, String layout, String theme, String eyebrow, String title, String body, List<Component> components, List<Action> actions) { }
    public record Component(String type, String label, BigDecimal value, String currency, String displayValue) { }
    public record Action(String type, String label) { }
    public record EvidenceDto(String title, int totalCount, Component totalAmount, List<EvidenceTransaction> transactions,
                              List<Action> actions, java.util.Map<String, EvidenceDto> byCard) {
        EvidenceDto(String title, int totalCount, Component totalAmount, List<EvidenceTransaction> transactions) {
            this(title, totalCount, totalAmount, transactions, List.of(), java.util.Map.of());
        }
        EvidenceDto(String title, int totalCount, Component totalAmount, List<EvidenceTransaction> transactions, List<Action> actions) {
            this(title, totalCount, totalAmount, transactions, actions, java.util.Map.of());
        }
    }
    public record EvidenceTransaction(String transactionId, String dateLabel, String merchantLabel, String categoryLabel, Component amount, String subcategoryLabel) { }
}
