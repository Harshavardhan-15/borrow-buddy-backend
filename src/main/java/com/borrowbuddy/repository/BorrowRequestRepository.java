package com.borrowbuddy.repository;

import com.borrowbuddy.entity.BorrowRequest;
import com.borrowbuddy.entity.enums.RequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BorrowRequestRepository extends JpaRepository<BorrowRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BorrowRequest r where r.id = :id")
    Optional<BorrowRequest> findByIdForUpdate(@Param("id") Long id);

    List<BorrowRequest> findByBorrowerIdOrderByCreatedAtDesc(Long borrowerId);

    List<BorrowRequest> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    boolean existsByItemIdAndBorrowerIdAndStatusIn(Long itemId, Long borrowerId, Collection<RequestStatus> statuses);

    long countByBorrowerIdAndStatusIn(Long borrowerId, Collection<RequestStatus> statuses);

    long countByItemIdAndStatusIn(Long itemId, Collection<RequestStatus> statuses);

    long countByOwnerIdAndStatus(Long ownerId, RequestStatus status);

    long countByOwnerIdAndStatusIn(Long ownerId, Collection<RequestStatus> statuses);

    /**
     * Counts approved reservations for an item that overlap the requested date range.
     * Used while holding a pessimistic lock on the item to prevent double booking.
     */
    @Query("""
            select count(r) from BorrowRequest r
            where r.item.id = :itemId
              and r.status = com.borrowbuddy.entity.enums.RequestStatus.APPROVED
              and r.id <> :excludeRequestId
              and r.startDate <= :returnDate
              and r.returnDate >= :startDate
            """)
    long countOverlappingApproved(@Param("itemId") Long itemId,
                                  @Param("startDate") LocalDate startDate,
                                  @Param("returnDate") LocalDate returnDate,
                                  @Param("excludeRequestId") Long excludeRequestId);

    /** End date of the reservation covering the given date, or null when the item is free. */
    @Query("""
            select max(r.returnDate) from BorrowRequest r
            where r.item.id = :itemId
              and r.status = com.borrowbuddy.entity.enums.RequestStatus.APPROVED
              and r.startDate <= :date
              and r.returnDate >= :date
            """)
    LocalDate findCurrentReservationEnd(@Param("itemId") Long itemId, @Param("date") LocalDate date);

    @Query("""
            select r from BorrowRequest r
            where r.item.id = :itemId
              and r.status = com.borrowbuddy.entity.enums.RequestStatus.PENDING
              and r.id <> :approvedRequestId
              and r.startDate <= :returnDate
              and r.returnDate >= :startDate
            """)
    List<BorrowRequest> findConflictingPending(@Param("itemId") Long itemId,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("returnDate") LocalDate returnDate,
                                               @Param("approvedRequestId") Long approvedRequestId);
}
