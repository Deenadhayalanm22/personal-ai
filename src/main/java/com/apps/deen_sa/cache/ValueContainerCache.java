package com.apps.deen_sa.cache;

import com.apps.deen_sa.entity.ValueContainerEntity;

import java.util.List;

public interface ValueContainerCache {

    List<ValueContainerEntity> getActiveContainers(Long ownerId);

    void putActiveContainers(Long ownerId, List<ValueContainerEntity> containers);

    void evict(Long ownerId);

    void evictAll();
}
