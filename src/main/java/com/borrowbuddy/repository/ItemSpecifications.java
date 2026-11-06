package com.borrowbuddy.repository;

import com.borrowbuddy.entity.BorrowRequest;
import com.borrowbuddy.entity.Item;
import com.borrowbuddy.entity.enums.AvailabilityStatus;
import com.borrowbuddy.entity.enums.ItemCondition;
import com.borrowbuddy.entity.enums.RequestStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ItemSpecifications {

    private ItemSpecifications() {
    }

    public static Specification<Item> browse(String query,
                                             String category,
                                             ItemCondition condition,
                                             Boolean availableOnly) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));

            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("itemName")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("category")), like)
                ));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
            }
            if (condition != null) {
                predicates.add(cb.equal(root.get("itemCondition"), condition));
            }
            if (Boolean.TRUE.equals(availableOnly)) {
                predicates.add(cb.equal(root.get("availabilityStatus"), AvailabilityStatus.AVAILABLE));
                LocalDate today = LocalDate.now();
                Subquery<Long> reservation = criteriaQuery.subquery(Long.class);
                Root<BorrowRequest> request = reservation.from(BorrowRequest.class);
                reservation.select(request.get("id"))
                        .where(cb.and(
                                cb.equal(request.get("item"), root),
                                cb.equal(request.get("status"), RequestStatus.APPROVED),
                                cb.lessThanOrEqualTo(request.get("startDate"), today),
                                cb.greaterThanOrEqualTo(request.get("returnDate"), today)
                        ));
                predicates.add(cb.not(cb.exists(reservation)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
