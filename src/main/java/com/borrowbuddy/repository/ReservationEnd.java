package com.borrowbuddy.repository;

import java.time.LocalDate;

/**
 * The end date of the approved reservation covering a given day for one item.
 * Loaded for a whole page of items in a single query.
 */
public interface ReservationEnd {

    Long getItemId();

    LocalDate getReturnDate();
}
