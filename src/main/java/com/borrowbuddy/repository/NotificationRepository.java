package com.borrowbuddy.repository;

import com.borrowbuddy.entity.Notification;
import com.borrowbuddy.entity.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByStudentIdOrderByCreatedAtDesc(Long studentId, Pageable pageable);

    List<Notification> findTop6ByStudentIdOrderByCreatedAtDesc(Long studentId);

    long countByStudentIdAndReadFalse(Long studentId);

    Optional<Notification> findByIdAndStudentId(Long id, Long studentId);

    boolean existsByStudentIdAndNotificationTypeAndReferenceIdAndCreatedAtAfter(
            Long studentId, NotificationType type, Long referenceId, Instant after);

    @Modifying
    @Query("update Notification n set n.read = true where n.student.id = :studentId and n.read = false")
    int markAllRead(@Param("studentId") Long studentId);
}
