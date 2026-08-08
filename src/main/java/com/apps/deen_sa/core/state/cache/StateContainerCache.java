package com.apps.deen_sa.finance.legacy.state.cache;

import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;

import java.util.List;

public interface StateContainerCache {

    List<StateContainerEntity> getActiveContainers(Long ownerId);

    void putActiveContainers(Long ownerId, List<StateContainerEntity> containers);

    void evict(Long ownerId);

    void evictAll();
}
