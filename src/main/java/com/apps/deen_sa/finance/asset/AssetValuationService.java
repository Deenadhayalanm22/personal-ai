package com.apps.deen_sa.finance.asset;

import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.state.StateContainerRepository;
import com.apps.deen_sa.dto.AssetMetricsDto;
import com.apps.deen_sa.dto.AssetValuationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Phase 5: Asset Valuation Service
 * 
 * Computes current market value and unrealized PnL for assets using
 * existing ownership state and derived metrics.
 * 
 * STRICT RULES:
 * - Do NOT persist market prices
 * - Do NOT modify containers or transactions
 * - Do NOT assume price is always present
 * - If price is missing, return partial valuation
 * - Keep all computations deterministic and side-effect free
 * 
 * Responsibilities:
 * 1. Accept asset identifier and optional currentPrice
 * 2. Resolve ASSET container to get current quantity
 * 3. Use AssetAnalyticsService to obtain averageBuyPrice
 * 4. Compute currentMarketValue = quantity × currentPrice
 * 5. Compute unrealizedPnL = (currentPrice - averageBuyPrice) × quantity
 * 6. Return valuation DTO
 */
@Service
@RequiredArgsConstructor
public class AssetValuationService {

    private final StateContainerRepository containerRepository;
    private final AssetAnalyticsService assetAnalyticsService;

    /**
     * Compute valuation for a specific asset with optional current price.
     * 
     * @param assetIdentifier The asset identifier (e.g., "ITC", "gold")
     * @param userId The user ID
     * @param currentPrice Optional current market price per unit (can be null)
     * @return AssetValuationDto containing computed valuation
     * @throws IllegalArgumentException if asset container not found
     */
    @Transactional(readOnly = true)
    public AssetValuationDto computeValuation(String assetIdentifier, Long userId, BigDecimal currentPrice) {
        
        // Validate input
        if (assetIdentifier == null || assetIdentifier.isBlank()) {
            throw new IllegalArgumentException("Asset identifier cannot be null or empty");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        // Find the asset container
        Optional<StateContainerEntity> assetContainerOpt = findAssetContainer(assetIdentifier, userId);
        
        if (assetContainerOpt.isEmpty()) {
            throw new IllegalArgumentException(
                "Asset container not found for: " + assetIdentifier + " (userId: " + userId + ")"
            );
        }
        
        StateContainerEntity assetContainer = assetContainerOpt.get();
        
        // Get current quantity from container
        BigDecimal quantity = assetContainer.getCurrentValue() != null 
            ? assetContainer.getCurrentValue() 
            : BigDecimal.ZERO;
        
        // Get analytics from AssetAnalyticsService
        AssetMetricsDto metrics = assetAnalyticsService.computeMetrics(assetIdentifier, userId);
        BigDecimal averageBuyPrice = metrics.getAverageBuyPrice();
        
        // Create valuation DTO
        AssetValuationDto valuation = new AssetValuationDto();
        valuation.setAssetIdentifier(assetIdentifier);
        valuation.setQuantity(quantity);
        valuation.setCurrentPrice(currentPrice);
        valuation.setAverageBuyPrice(averageBuyPrice);
        
        // Compute current market value if price is provided
        if (currentPrice != null) {
            BigDecimal currentMarketValue = quantity.multiply(currentPrice);
            valuation.setCurrentMarketValue(currentMarketValue);
            
            // Compute unrealized PnL if average buy price is available
            if (averageBuyPrice != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal priceDifference = currentPrice.subtract(averageBuyPrice);
                BigDecimal unrealizedPnL = priceDifference.multiply(quantity);
                valuation.setUnrealizedPnL(unrealizedPnL);
            } else {
                valuation.setUnrealizedPnL(null);
            }
        } else {
            // Price not provided - partial valuation
            valuation.setCurrentMarketValue(null);
            valuation.setUnrealizedPnL(null);
        }
        
        return valuation;
    }
    
    /**
     * Find the asset container for a specific asset and user.
     * 
     * @param assetIdentifier The asset identifier
     * @param userId The user ID
     * @return Optional containing the asset container if found
     */
    private Optional<StateContainerEntity> findAssetContainer(String assetIdentifier, Long userId) {
        return containerRepository.findAll().stream()
            .filter(c -> "ASSET".equals(c.getContainerType()))
            .filter(c -> assetIdentifier.equalsIgnoreCase(c.getName()))
            .filter(c -> userId.equals(c.getOwnerId()))
            .findFirst();
    }
}
