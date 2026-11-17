package com.borrowbuddy.repository;

import com.borrowbuddy.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByReviewedStudentIdOrderByCreatedAtDesc(Long studentId);

    List<Review> findByReviewerIdOrderByCreatedAtDesc(Long reviewerId);

    boolean existsByReviewerIdAndTransactionId(Long reviewerId, Long transactionId);

    Optional<Review> findByReviewerIdAndTransactionId(Long reviewerId, Long transactionId);

    List<Review> findByTransactionId(Long transactionId);

    @Query("select coalesce(avg(r.rating), 0) from Review r where r.reviewedStudent.id = :studentId")
    double averageRatingFor(@Param("studentId") Long studentId);

    long countByReviewedStudentId(Long studentId);

    /** Average rating and review count for many students in one query. */
    @Query("""
            select r.reviewedStudent.id as studentId,
                   avg(r.rating) as average,
                   count(r) as count
            from Review r
            where r.reviewedStudent.id in :studentIds
            group by r.reviewedStudent.id
            """)
    List<RatingAggregate> ratingAggregates(@Param("studentIds") Collection<Long> studentIds);
}
