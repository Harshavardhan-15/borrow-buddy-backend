package com.borrowbuddy.repository;

import com.borrowbuddy.entity.Item;
import com.borrowbuddy.entity.enums.AvailabilityStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long>, JpaSpecificationExecutor<Item> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id")
    Optional<Item> findByIdForUpdate(@Param("id") Long id);

    Page<Item> findByOwnerIdAndDeletedFalseOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    List<Item> findByOwnerIdAndDeletedFalseOrderByCreatedAtDesc(Long ownerId);

    long countByOwnerIdAndDeletedFalse(Long ownerId);

    long countByDeletedFalse();

    List<Item> findTop6ByDeletedFalseAndAvailabilityStatusOrderByCreatedAtDesc(AvailabilityStatus status);
}
