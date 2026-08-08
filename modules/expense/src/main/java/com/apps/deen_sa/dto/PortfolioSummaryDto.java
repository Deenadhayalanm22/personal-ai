package com.apps.deen_sa.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 6: Portfolio Summary DTO
 * 
 * Returns aggregated portfolio and net worth summary.
 * 
 * This DTO provides a comprehensive view of user's financial position
 * including assets, liquid accounts, and liabilities.
 * 
 * Fields:
 * - assetSummaries: List of individual asset summaries
 * - totalAssetValue: Total market value of all assets
 * - totalLiquidValue: Total value in liquid accounts (bank, cash, wallet)
 * - totalLiabilities: Total liabilities (credit cards, loans)
 * - netWorth: Total portfolio value (liquid + assets - liabilities)
 * - hasPartialValuations: Flag indicating if some assets lack current prices
 */
@Getter
@Setter
public class PortfolioSummaryDto {
    
    /**
     * List of individual asset summaries with valuations.
     */
    private List<AssetSummaryItem> assetSummaries;
    
    /**
     * Total market value of all assets.
     * May be partial if some assets lack current prices.
     */
    private BigDecimal totalAssetValue;
    
    /**
     * Total value in liquid accounts (BANK_ACCOUNT, CASH, WALLET).
     */
    private BigDecimal totalLiquidValue;
    
    /**
     * Total liabilities (CREDIT_CARD, LOAN).
     */
    private BigDecimal totalLiabilities;
    
    /**
     * Net worth = totalLiquidValue + totalAssetValue - totalLiabilities
     */
    private BigDecimal netWorth;
    
    /**
     * Flag indicating if some assets lack current prices.
     * If true, portfolio value is partial/incomplete.
     */
    private boolean hasPartialValuations;
    
    /**
     * Default constructor initializes collections and BigDecimal fields.
     */
    public PortfolioSummaryDto() {
        this.assetSummaries = new ArrayList<>();
        this.totalAssetValue = BigDecimal.ZERO;
        this.totalLiquidValue = BigDecimal.ZERO;
        this.totalLiabilities = BigDecimal.ZERO;
        this.netWorth = BigDecimal.ZERO;
        this.hasPartialValuations = false;
    }
    
    /**
     * Individual asset summary item.
     */
    @Getter
    @Setter
    public static class AssetSummaryItem {
        private String assetIdentifier;
        private BigDecimal quantity;
        private BigDecimal averageBuyPrice;
        private BigDecimal currentPrice;
        private BigDecimal currentMarketValue;
        private BigDecimal unrealizedPnL;
        private boolean hasPrice;
    }
}
