package com.apps.deen_sa.finance.portfolio;

import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.state.StateContainerRepository;
import com.apps.deen_sa.dto.AssetValuationDto;
import com.apps.deen_sa.dto.PortfolioSummaryDto;
import com.apps.deen_sa.finance.asset.AssetAnalyticsService;
import com.apps.deen_sa.finance.asset.AssetValuationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Phase 6: Portfolio Summary Service
 * 
 * Aggregates assets, liquid accounts, and liabilities into a single
 * portfolio and net worth view using existing services.
 * 
 * STRICT RULES:
 * - Do NOT persist any summary data
 * - Do NOT modify containers or transactions
 * - Do NOT assume prices exist for all assets
 * - If data is missing, mark results as partial
 * - Keep logic orchestration-only; no heavy calculations here
 * 
 * Responsibilities:
 * 1. Fetch all StateContainerEntity records
 * 2. Separate containers by type (ASSET, LIQUID, LIABILITY)
 * 3. For each ASSET: use AssetAnalyticsService and AssetValuationService
 * 4. Aggregate totals
 * 5. Compute net worth
 * 6. Return PortfolioSummaryDTO with flags for partial valuations
 */
@Service
@RequiredArgsConstructor
public class PortfolioSummaryService {

    private final StateContainerRepository containerRepository;
    private final AssetAnalyticsService assetAnalyticsService;
    private final AssetValuationService assetValuationService;

    /**
     * Compute portfolio summary for a user with optional asset prices.
     * 
     * @param userId The user ID
     * @param assetPrices Optional map of asset identifier to current price (can be null or partial)
     * @return PortfolioSummaryDto containing aggregated portfolio view
     */
    @Transactional(readOnly = true)
    public PortfolioSummaryDto computePortfolioSummary(Long userId, Map<String, BigDecimal> assetPrices) {
        
        // Validate input
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        // Create result DTO
        PortfolioSummaryDto summary = new PortfolioSummaryDto();
        
        // Fetch all containers for this user
        List<StateContainerEntity> allContainers = containerRepository.findAll().stream()
            .filter(c -> userId.equals(c.getOwnerId()))
            .filter(c -> "ACTIVE".equals(c.getStatus()))
            .toList();
        
        // Separate containers by type
        List<StateContainerEntity> assetContainers = allContainers.stream()
            .filter(c -> "ASSET".equals(c.getContainerType()))
            .toList();
        
        List<StateContainerEntity> liquidContainers = allContainers.stream()
            .filter(c -> isLiquidContainer(c.getContainerType()))
            .toList();
        
        List<StateContainerEntity> liabilityContainers = allContainers.stream()
            .filter(c -> isLiabilityContainer(c.getContainerType()))
            .toList();
        
        // Process assets
        BigDecimal totalAssetValue = BigDecimal.ZERO;
        boolean hasPartialValuations = false;
        
        for (StateContainerEntity assetContainer : assetContainers) {
            String assetIdentifier = assetContainer.getName();
            BigDecimal currentPrice = (assetPrices != null) ? assetPrices.get(assetIdentifier) : null;
            
            try {
                // Get valuation for this asset
                AssetValuationDto valuation = assetValuationService.computeValuation(
                    assetIdentifier, 
                    userId, 
                    currentPrice
                );
                
                // Create summary item
                PortfolioSummaryDto.AssetSummaryItem item = new PortfolioSummaryDto.AssetSummaryItem();
                item.setAssetIdentifier(assetIdentifier);
                item.setQuantity(valuation.getQuantity());
                item.setAverageBuyPrice(valuation.getAverageBuyPrice());
                item.setCurrentPrice(valuation.getCurrentPrice());
                item.setCurrentMarketValue(valuation.getCurrentMarketValue());
                item.setUnrealizedPnL(valuation.getUnrealizedPnL());
                item.setHasPrice(currentPrice != null);
                
                summary.getAssetSummaries().add(item);
                
                // Add to total if we have a valuation
                if (valuation.getCurrentMarketValue() != null) {
                    totalAssetValue = totalAssetValue.add(valuation.getCurrentMarketValue());
                } else {
                    hasPartialValuations = true;
                }
                
            } catch (Exception e) {
                // Asset might not have transactions yet, skip it
                hasPartialValuations = true;
            }
        }
        
        summary.setTotalAssetValue(totalAssetValue);
        summary.setHasPartialValuations(hasPartialValuations);
        
        // Process liquid accounts
        BigDecimal totalLiquidValue = BigDecimal.ZERO;
        for (StateContainerEntity liquidContainer : liquidContainers) {
            BigDecimal value = liquidContainer.getCurrentValue();
            if (value != null) {
                totalLiquidValue = totalLiquidValue.add(value);
            }
        }
        summary.setTotalLiquidValue(totalLiquidValue);
        
        // Process liabilities
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        for (StateContainerEntity liabilityContainer : liabilityContainers) {
            BigDecimal value = liabilityContainer.getCurrentValue();
            if (value != null) {
                // Liabilities are typically stored as positive values
                totalLiabilities = totalLiabilities.add(value.abs());
            }
        }
        summary.setTotalLiabilities(totalLiabilities);
        
        // Compute net worth
        BigDecimal netWorth = totalLiquidValue
            .add(totalAssetValue)
            .subtract(totalLiabilities);
        summary.setNetWorth(netWorth);
        
        return summary;
    }
    
    /**
     * Check if container type is a liquid account.
     * 
     * @param containerType The container type
     * @return true if liquid (BANK_ACCOUNT, CASH, WALLET)
     */
    private boolean isLiquidContainer(String containerType) {
        return "BANK_ACCOUNT".equals(containerType)
            || "CASH".equals(containerType)
            || "WALLET".equals(containerType);
    }
    
    /**
     * Check if container type is a liability.
     * 
     * @param containerType The container type
     * @return true if liability (CREDIT_CARD, LOAN)
     */
    private boolean isLiabilityContainer(String containerType) {
        return "CREDIT_CARD".equals(containerType)
            || "LOAN".equals(containerType);
    }
}
