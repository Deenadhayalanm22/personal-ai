package com.apps.deen_sa.cooking.session;

import com.apps.deen_sa.cooking.recipe.Recipe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CookingSessionService {
    private final CookingSessionRepository repository;
    private static final List<CookingSessionStatus> ACTIVE = List.of(
            CookingSessionStatus.PREPARING, CookingSessionStatus.COOKING, CookingSessionStatus.PAUSED);

    public Optional<CookingSessionEntity> active(Long userId) {
        return repository.findFirstByUserIdAndStatusInOrderByUpdatedAtDesc(userId, ACTIVE);
    }

    @Transactional
    public CookingSessionEntity start(Long userId, Recipe recipe, BigDecimal rice, BigDecimal chicken) {
        active(userId).ifPresent(old -> { old.setStatus(CookingSessionStatus.CANCELLED); repository.save(old); });
        CookingSessionEntity value = new CookingSessionEntity();
        value.setUserId(userId); value.setRecipeId(recipe.id()); value.setRecipeVersion(recipe.version());
        value.setRiceGrams(rice); value.setChickenGrams(chicken); value.setCurrentStep(0);
        value.setStatus(CookingSessionStatus.PREPARING);
        return repository.save(value);
    }

    @Transactional
    public CookingSessionEntity start(Long userId, Recipe recipe, BigDecimal rice, BigDecimal chicken,
                                      String riceType, String equipment) {
        CookingSessionEntity value = start(userId, recipe, rice, chicken);
        value.setRiceType(riceType); value.setEquipment(equipment);
        return repository.save(value);
    }

    public CookingSessionEntity save(CookingSessionEntity session) { return repository.save(session); }
}
