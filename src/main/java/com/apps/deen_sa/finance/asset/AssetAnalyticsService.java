package com.apps.deen_sa.finance.asset;

import com.apps.deen_sa.core.state.StateChangeEntity;
import com.apps.deen_sa.core.state.StateChangeRepository;
import com.apps.deen_sa.core.state.StateChangeTypeEnum;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.state.StateContainerRepository;
import com.apps.deen_sa.dto.AssetMetricsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Phase 4: Asset Analytics Service
 * 
 * Computes analytical metrics from existing asset BUY and SELL transactions
 * without modifying ownership state or database schema.
 * 
 * STRICT RULES:
 * - READ-ONLY operations only
 * - No new transactions created
 * - No container updates
 * - No data persistence
 * - All metrics computed on-the-fly from transaction history
 * 
 * Responsibilities:
 * 1. Fetch all StateChangeEntity records for a given asset
 * 2. Separate transactions by type (BUY vs SELL)
 * 3. Compute weighted average buy price
 * 4. Compute realized PnL if SELL transactions exist
 * 5. Return metrics via AssetMetricsDto
 */
@Service
@RequiredArgsConstructor
public class AssetAnalyticsService {

    private final StateChangeRepository transactionRepository;
    private final StateContainerRepository containerRepository;

    /**
     * Compute analytics for a specific asset owned by a user.
     * 
     * @param assetIdentifier The asset identifier (e.g., "ITC", "gold")
     * @param userId The user ID
     * @return AssetMetricsDto containing computed metrics
     * @throws IllegalArgumentException if asset container not found
     */
    @Transactional(readOnly = true)
    public AssetMetricsDto computeMetrics(String assetIdentifier, Long userId) {
        
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
        
        // Fetch all transactions for this asset container
        List<StateChangeEntity> allTransactions = transactionRepository.findAll().stream()
            .filter(tx -> assetContainer.getId().equals(tx.getSourceContainerId()) 
                       || assetContainer.getId().equals(tx.getTargetContainerId()))
            .filter(tx -> tx.getTransactionType() == StateChangeTypeEnum.ASSET_BUY
                       || tx.getTransactionType() == StateChangeTypeEnum.ASSET_SELL)
            .toList();
        
        // Separate BUY and SELL transactions
        List<StateChangeEntity> buyTransactions = allTransactions.stream()
            .filter(tx -> tx.getTransactionType() == StateChangeTypeEnum.ASSET_BUY)
            .toList();
        
        List<StateChangeEntity> sellTransactions = allTransactions.stream()
            .filter(tx -> tx.getTransactionType() == StateChangeTypeEnum.ASSET_SELL)
            .toList();
        
        // Create result DTO
        AssetMetricsDto metrics = new AssetMetricsDto();
        metrics.setAssetIdentifier(assetIdentifier);
        
        // Compute BUY metrics
        BigDecimal totalBuyQuantity = BigDecimal.ZERO;
        BigDecimal totalBuyAmount = BigDecimal.ZERO;
        
        for (StateChangeEntity buyTx : buyTransactions) {
            totalBuyQuantity = totalBuyQuantity.add(buyTx.getQuantity());
            totalBuyAmount = totalBuyAmount.add(buyTx.getAmount());
        }
        
        metrics.setInvestedAmount(totalBuyAmount);
        
        // Compute average buy price
        if (totalBuyQuantity.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal averageBuyPrice = totalBuyAmount.divide(totalBuyQuantity, 4, RoundingMode.HALF_UP);
            metrics.setAverageBuyPrice(averageBuyPrice);
        } else {
            metrics.setAverageBuyPrice(null);
        }
        
        // Compute SELL metrics
        BigDecimal totalSellQuantity = BigDecimal.ZERO;
        BigDecimal totalSellAmount = BigDecimal.ZERO;
        
        for (StateChangeEntity sellTx : sellTransactions) {
            totalSellQuantity = totalSellQuantity.add(sellTx.getQuantity());
            totalSellAmount = totalSellAmount.add(sellTx.getAmount());
        }
        
        metrics.setSoldAmount(totalSellAmount);
        
        // Compute realized PnL if there are SELL transactions
        if (totalSellQuantity.compareTo(BigDecimal.ZERO) > 0 && metrics.getAverageBuyPrice() != null) {
            BigDecimal costBasis = totalSellQuantity.multiply(metrics.getAverageBuyPrice());
            BigDecimal realizedPnL = totalSellAmount.subtract(costBasis);
            metrics.setRealizedPnL(realizedPnL);
        } else {
            metrics.setRealizedPnL(null);
        }
        
        // Compute total quantity (current ownership)
        BigDecimal totalQuantity = totalBuyQuantity.subtract(totalSellQuantity);
        metrics.setTotalQuantity(totalQuantity);
        
        return metrics;
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
