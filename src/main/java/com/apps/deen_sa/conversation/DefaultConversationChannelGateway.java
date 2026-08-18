package com.apps.deen_sa.conversation;

import com.apps.deen_sa.cooking.coach.CookingCoachService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultConversationChannelGateway implements ConversationChannelGateway {
    private final AppUserService users;
    private final CookingCoachService coach;

    @Override public SpeechResult process(String channel, String externalUserId, String messageId, String text) {
        AppUserEntity user = users.resolve(channel, externalUserId);
        return coach.process(user.getId(), text);
    }

    @Override public SpeechResult processTrustedAnswer(String channel, String externalUserId, String messageId, String answer) {
        AppUserEntity user = users.resolve(channel, externalUserId);
        return coach.process(user.getId(), answer);
    }
}
