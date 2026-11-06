package com.borrowbuddy.service;

import com.borrowbuddy.entity.BorrowRequest;
import com.borrowbuddy.entity.BorrowTransaction;
import com.borrowbuddy.entity.Student;
import com.borrowbuddy.entity.enums.NotificationType;
import com.borrowbuddy.entity.enums.TransactionStatus;
import com.borrowbuddy.repository.BorrowTransactionRepository;
import com.borrowbuddy.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final BorrowTransactionRepository transactionRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public ReminderScheduler(BorrowTransactionRepository transactionRepository,
                             NotificationRepository notificationRepository,
                             NotificationService notificationService) {
        this.transactionRepository = transactionRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${app.reminders.cron:0 0 8 * * *}")
    @Transactional
    public void runDailyReminders() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        Instant startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant();

        List<BorrowTransaction> dueTomorrow = transactionRepository
                .findByStatusInAndRequestReturnDate(
                        List.of(TransactionStatus.BORROWED, TransactionStatus.OVERDUE), tomorrow);
        for (BorrowTransaction transaction : dueTomorrow) {
            BorrowRequest request = transaction.getRequest();
            String message = "\"" + request.getItem().getItemName() + "\" is due back tomorrow ("
                    + request.getReturnDate() + ").";
            notifyOnce(request.getBorrower(), NotificationType.RETURN_REMINDER, message,
                    transaction.getId(), startOfDay);
            notifyOnce(request.getOwner(), NotificationType.RETURN_REMINDER,
                    "The item you lent (\"" + request.getItem().getItemName() + "\") is due back tomorrow from "
                            + request.getBorrower().getName() + ".",
                    transaction.getId(), startOfDay);
        }

        List<BorrowTransaction> overdue = transactionRepository
                .findByStatusAndRequestReturnDateBefore(TransactionStatus.BORROWED, today);
        for (BorrowTransaction transaction : overdue) {
            transaction.setStatus(TransactionStatus.OVERDUE);
            transactionRepository.save(transaction);
            BorrowRequest request = transaction.getRequest();
            String message = "\"" + request.getItem().getItemName() + "\" is overdue. It was due on "
                    + request.getReturnDate() + ".";
            notifyOnce(request.getBorrower(), NotificationType.OVERDUE, message,
                    transaction.getId(), startOfDay);
            notifyOnce(request.getOwner(), NotificationType.OVERDUE,
                    "The item you lent (\"" + request.getItem().getItemName() + "\") is overdue from "
                            + request.getBorrower().getName() + ".",
                    transaction.getId(), startOfDay);
        }

        log.info("Return reminder job finished: {} due tomorrow, {} marked overdue",
                dueTomorrow.size(), overdue.size());
    }

    private void notifyOnce(Student student, NotificationType type, String message,
                            Long referenceId, Instant startOfDay) {
        boolean alreadySent = notificationRepository
                .existsByStudentIdAndNotificationTypeAndReferenceIdAndCreatedAtAfter(
                        student.getId(), type, referenceId, startOfDay);
        if (!alreadySent) {
            notificationService.notify(student, type, message, referenceId, "notifications.html");
        }
    }
}
