package com.apps.deen_sa.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Phase 4: Asset Analytics Metrics DTO
 * 
 * Returns read-only analytical metrics computed from asset transaction history.
 * 
 * These values are NOT stored in the database and are always computed on-the-fly
 * from StateChangeEntity records.
 * 
 * Fields:
 * - totalQuantity: Current owned quantity (BUY - SELL)
 * - averageBuyPrice: Weighted average price of all BUY transactions
 * - realizedPnL: Profit/Loss from SELL transactions (if any)
 * - investedAmount: Total amount spent on BUY transactions
 * - soldAmount: Total amount received from SELL transactions
 */
@Getter
@Setter
public class AssetMetricsDto {
    
    /**
     * Current owned quantity after all BUY and SELL transactions.
     * Computed as: sum(BUY quantity) - sum(SELL quantity)
     */
    private BigDecimal totalQuantity;
    
    /**
     * Weighted average price per unit from all BUY transactions.
     * Computed as: totalBuyAmount / totalBuyQuantity
     * NULL if no BUY transactions exist.
     */
    private BigDecimal averageBuyPrice;
    
    /**
     * Realized profit or loss from SELL transactions.
     * Computed as: soldAmount - (soldQuantity × averageBuyPrice)
     * NULL if no SELL transactions exist.
     */
    private BigDecimal realizedPnL;
    
    /**
     * Total amount invested in BUY transactions.
     * Computed as: sum(BUY amount)
     */
    private BigDecimal investedAmount;
    
    /**
     * Total amount received from SELL transactions.
     * Computed as: sum(SELL amount)
     */
    private BigDecimal soldAmount;
    
    /**
     * Asset identifier (e.g., "ITC", "gold", "SBI Bluechip")
     */
    private String assetIdentifier;
    
    /**
     * Default constructor initializes BigDecimal fields to ZERO.
     */
    public AssetMetricsDto() {
        this.totalQuantity = BigDecimal.ZERO;
        this.investedAmount = BigDecimal.ZERO;
        this.soldAmount = BigDecimal.ZERO;
    }
}
