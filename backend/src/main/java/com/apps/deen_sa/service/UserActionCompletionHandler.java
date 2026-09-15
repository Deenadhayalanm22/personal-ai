package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.UserActionItemType;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.UserActionItemEntity;

/** Implements the domain effect of completing one action type. */
public interface UserActionCompletionHandler {
    UserActionItemType actionType();
    void complete(AppUserEntity user, UserActionItemEntity action);
}
