package com.apps.deen_sa.repo;

import com.apps.deen_sa.entity.ValueContainerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ValueContainerRepo extends JpaRepository<ValueContainerEntity, Long> {

    @Query("""
        SELECT v FROM ValueContainerEntity v
        WHERE v.ownerId = :userId
        AND v.containerType = 'PAYABLE'
        AND v.status = 'ACTIVE'
        """)
    List<ValueContainerEntity> findActiveLoansForUser(@Param("userId") Long userId);


    @Query("""
        SELECT vc
        FROM ValueContainerEntity vc
        WHERE vc.ownerId = :ownerId
          AND vc.status = 'ACTIVE'
    """)
    List<ValueContainerEntity> findActiveByOwnerId(@Param("ownerId") Long ownerId);

}
