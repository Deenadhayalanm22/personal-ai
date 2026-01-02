package com.apps.deen_sa.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Phase 5: Asset Valuation DTO
 * 
 * Returns current market value and unrealized PnL for an asset.
 * 
 * These values are NOT stored in the database and are computed on-demand
 * using current price input and existing ownership state.
 * 
 * Fields:
 * - quantity: Current owned quantity
 * - currentPrice: Current market price per unit (provided as input)
 * - currentMarketValue: Total market value (quantity × currentPrice)
 * - averageBuyPrice: Weighted average buy price (from AssetAnalyticsService)
 * - unrealizedPnL: Unrealized profit/loss ((currentPrice - averageBuyPrice) × quantity)
 * - assetIdentifier: Asset name/identifier
 */
@Getter
@Setter
public class AssetValuationDto {
    
    /**
     * Current owned quantity.
     */
    private BigDecimal quantity;
    
    /**
     * Current market price per unit (provided as input).
     * NULL if price was not provided.
     */
    private BigDecimal currentPrice;
    
    /**
     * Total current market value.
     * Computed as: quantity × currentPrice
     * NULL if currentPrice is not provided.
     */
    private BigDecimal currentMarketValue;
    
    /**
     * Weighted average buy price (from transaction history).
     * NULL if no BUY transactions exist.
     */
    private BigDecimal averageBuyPrice;
    
    /**
     * Unrealized profit or loss.
     * Computed as: (currentPrice - averageBuyPrice) × quantity
     * NULL if currentPrice or averageBuyPrice is not available.
     */
    private BigDecimal unrealizedPnL;
    
    /**
     * Asset identifier (e.g., "ITC", "gold", "SBI Bluechip")
     */
    private String assetIdentifier;
    
    /**
     * Default constructor initializes BigDecimal fields to ZERO where appropriate.
     */
    public AssetValuationDto() {
        this.quantity = BigDecimal.ZERO;
    }
}
