package com.apps.deen_sa.cooking.coach;

import com.apps.deen_sa.cooking.session.CookingSetupEntity;
import com.apps.deen_sa.cooking.session.CookingSetupRepository;
import com.apps.deen_sa.cooking.session.CookingSetupStage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CookingSetupServiceTest {
    @Test
    void collectsChickenRiceRecommendationAndValidatedEquipmentInOrder() {
        CookingSetupRepository repository = mock(CookingSetupRepository.class);
        when(repository.findByUserId(7L)).thenReturn(Optional.empty());
        CookingSetupService service = new CookingSetupService(repository);

        assertThat(service.begin(7L)).contains("Are you ready").contains("Yes or No");
        ArgumentCaptor<CookingSetupEntity> saved = ArgumentCaptor.forClass(CookingSetupEntity.class);
        verify(repository).save(saved.capture());
        CookingSetupEntity setup = saved.getValue();
        assertThat(setup.getStage()).isEqualTo(CookingSetupStage.CONFIRM_READY);

        assertThat(service.answer(setup, "Yes").message()).contains("How much chicken");
        assertThat(service.answer(setup, "600g").message()).contains("Which rice").contains("Seeraga Samba");
        assertThat(service.answer(setup, "Basmati").message()).contains("For 600 g chicken")
                .contains("recommends 500 g rice");
        assertThat(service.answer(setup, "No").message()).contains("What are you cooking in?");
        CookingSetupService.SetupReply completed = service.answer(setup, "Biryani pot");

        assertThat(completed.complete()).isTrue();
        assertThat(completed.selection().chickenGrams()).isEqualByComparingTo("600");
        assertThat(completed.selection().riceGrams()).isEqualByComparingTo("500");
        assertThat(completed.selection().riceType()).isEqualTo("BASMATI");
        assertThat(completed.selection().equipment()).isEqualTo("BIRYANI_POT");
        verify(repository).delete(setup);
    }
}
