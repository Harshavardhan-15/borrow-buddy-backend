package com.borrowbuddy.repository;

/**
 * Aggregated review statistics for one student, used to build owner summaries
 * for a whole page of items in a single query instead of two queries per item.
 */
public interface RatingAggregate {

    Long getStudentId();

    Double getAverage();

    Long getCount();
}
