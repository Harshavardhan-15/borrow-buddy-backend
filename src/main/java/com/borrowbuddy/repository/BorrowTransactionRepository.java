package com.borrowbuddy.repository;

import com.borrowbuddy.entity.BorrowTransaction;
import com.borrowbuddy.entity.enums.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BorrowTransactionRepository extends JpaRepository<BorrowTransaction, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from BorrowTransaction t where t.id = :id")
    Optional<BorrowTransaction> findByIdForUpdate(@Param("id") Long id);

    Optional<BorrowTransaction> findByRequestId(Long requestId);

    Optional<BorrowTransaction> findByQrTokenHash(String qrTokenHash);

    List<BorrowTransaction> findByRequestBorrowerIdOrderByCreatedAtDesc(Long borrowerId);

    List<BorrowTransaction> findByRequestOwnerIdOrderByCreatedAtDesc(Long ownerId);

    List<BorrowTransaction> findByStatusInAndRequestReturnDate(Collection<TransactionStatus> statuses, LocalDate date);

    List<BorrowTransaction> findByStatusAndRequestReturnDateBefore(TransactionStatus status, LocalDate date);

    @Query("""
            select t from BorrowTransaction t
            where t.status in :statuses
              and (t.request.borrower.id = :studentId or t.request.owner.id = :studentId)
            order by t.request.returnDate asc
            """)
    List<BorrowTransaction> findActiveForStudent(@Param("studentId") Long studentId,
                                                 @Param("statuses") Collection<TransactionStatus> statuses);

    long countByRequestBorrowerIdAndStatusIn(Long borrowerId, Collection<TransactionStatus> statuses);

    long countByRequestOwnerIdAndStatusIn(Long ownerId, Collection<TransactionStatus> statuses);

    long countByRequestItemIdAndStatusIn(Long itemId, Collection<TransactionStatus> statuses);

    long countByStatus(TransactionStatus status);
}
