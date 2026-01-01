package com.apps.deen_sa.core.state;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StateContainerRepository extends JpaRepository<StateContainerEntity, Long> {

    @Query("""
        SELECT v FROM StateContainerEntity v
        WHERE v.ownerId = :userId
        AND v.containerType = 'PAYABLE'
        AND v.status = 'ACTIVE'
        """)
    List<StateContainerEntity> findActiveLoansForUser(@Param("userId") Long userId);


    @Query("""
        SELECT vc
        FROM StateContainerEntity vc
        WHERE vc.ownerId = :ownerId
          AND vc.status = 'ACTIVE'
    """)
    List<StateContainerEntity> findActiveByOwnerId(@Param("ownerId") Long ownerId);

}
