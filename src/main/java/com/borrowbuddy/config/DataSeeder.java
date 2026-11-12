package com.borrowbuddy.config;

import com.borrowbuddy.entity.*;
import com.borrowbuddy.entity.enums.*;
import com.borrowbuddy.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Seeds demo students, items, requests and reviews for local development.
 * Disabled by default in production (app.seed.enabled=false).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final StudentRepository studentRepository;
    private final ItemRepository itemRepository;
    private final BorrowRequestRepository requestRepository;
    private final BorrowTransactionRepository transactionRepository;
    private final ReviewRepository reviewRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.enabled:false}")
    private boolean enabled;

    public DataSeeder(StudentRepository studentRepository,
                      ItemRepository itemRepository,
                      BorrowRequestRepository requestRepository,
                      BorrowTransactionRepository transactionRepository,
                      ReviewRepository reviewRepository,
                      NotificationRepository notificationRepository,
                      PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.itemRepository = itemRepository;
        this.requestRepository = requestRepository;
        this.transactionRepository = transactionRepository;
        this.reviewRepository = reviewRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled || studentRepository.count() > 0) {
            return;
        }

        Student aarav = student("Aarav Sharma", "aarav@college.edu", "STU2024001");
        Student priya = student("Priya Nair", "priya@college.edu", "STU2024002");
        Student rohan = student("Rohan Mehta", "rohan@college.edu", "STU2024003");
        Student sara = student("Sara Khan", "sara@college.edu", "STU2024004");

        Item calculator = item(aarav, "Casio FX-991EX Scientific Calculator", "Electronics",
                "Exam-approved scientific calculator with 552 functions. Includes a protective slide cover.",
                ItemCondition.GOOD, new BigDecimal("20"), new BigDecimal("200"), "+91 98765 43210");
        Item textbook = item(aarav, "Data Structures & Algorithms Textbook", "Books",
                "Second edition, clean pages with light highlighting in chapters 3-4. Perfect for CS201.",
                ItemCondition.LIKE_NEW, new BigDecimal("15"), new BigDecimal("150"), "+91 98765 43210");
        item(aarav, "Arduino Uno Starter Kit", "Electronics",
                "Uno R3 board, breadboard, jumper wires, LEDs and a sensor pack. Great for mini projects.",
                ItemCondition.GOOD, new BigDecimal("40"), new BigDecimal("500"), "+91 98765 43210");
        item(aarav, "Yonex Badminton Racket", "Sports",
                "Lightweight graphite racket with a fresh grip. Two shuttlecocks included.",
                ItemCondition.GOOD, new BigDecimal("25"), new BigDecimal("250"), "+91 98765 43210");
        Item labCoat = item(priya, "Women's Lab Coat (Size M)", "Lab Equipment",
                "Freshly laundered white lab coat, size M. Required for chemistry and biology labs.",
                ItemCondition.LIKE_NEW, new BigDecimal("15"), new BigDecimal("150"), "+91 98765 43211");
        Item headphones = item(priya, "Sony WH-CH510 Headphones", "Electronics",
                "Wireless over-ear headphones with 30-hour battery life and USB-C charging.",
                ItemCondition.GOOD, new BigDecimal("30"), new BigDecimal("500"), "+91 98765 43211");
        item(rohan, "MacBook Pro 67W USB-C Charger", "Electronics",
                "Original Apple 67W charger with a 2m USB-C cable. Ideal for exam-week emergencies.",
                ItemCondition.GOOD, new BigDecimal("20"), new BigDecimal("300"), "+91 98765 43212");
        item(rohan, "Canon EOS 200D DSLR Camera", "Electronics",
                "24MP DSLR with an 18-55mm lens, bag and 64GB SD card. For club events and projects.",
                ItemCondition.LIKE_NEW, new BigDecimal("150"), new BigDecimal("2000"), "+91 98765 43212");
        item(sara, "Engineering Drawing Kit", "Stationery",
                "Drawing board, T-square, set squares, compass and isometric sheets. Complete kit.",
                ItemCondition.NEW, new BigDecimal("25"), new BigDecimal("300"), "+91 98765 43213");
        item(sara, "Texas Instruments TI-84 Graphing Calculator", "Electronics",
                "Graphing calculator for calculus and statistics. Batteries included.",
                ItemCondition.GOOD, new BigDecimal("40"), new BigDecimal("500"), "+91 98765 43213");

        LocalDate today = LocalDate.now();

        BorrowRequest pendingCalculator = request(priya, calculator, today.plusDays(2), today.plusDays(7),
                "Hi Aarav! I have my maths exam next week and my calculator just stopped working. Would really appreciate the loan.");
        BorrowRequest borrowedTextbook = request(rohan, textbook, today.minusDays(1), today.plusDays(5),
                "Need this for the data structures assignment. I'll return it right after the submission.");
        borrowedTextbook.setStatus(RequestStatus.APPROVED);
        requestRepository.save(borrowedTextbook);

        BorrowRequest pendingHeadphones = request(sara, headphones, today.plusDays(1), today.plusDays(4),
                "Could I borrow these for an online lecture week? I'll take good care of them.");
        BorrowRequest pendingCoat = request(sara, labCoat, today.plusDays(1), today.plusDays(4),
                "Could I borrow this for the Friday lab session? I'll wash it before returning.");
        BorrowRequest completedCoat = request(sara, labCoat, today.minusDays(20), today.minusDays(12),
                "Borrowing for the two-week chemistry practical block.");
        completedCoat.setStatus(RequestStatus.COMPLETED);
        requestRepository.save(completedCoat);

        BorrowTransaction active = transaction(borrowedTextbook, TransactionStatus.BORROWED,
                Instant.now().minus(1, ChronoUnit.DAYS), null);
        BorrowTransaction completed = transaction(completedCoat, TransactionStatus.COMPLETED,
                Instant.now().minus(20, ChronoUnit.DAYS), Instant.now().minus(12, ChronoUnit.DAYS));

        notification(aarav, NotificationType.BORROW_REQUEST,
                priya.getName() + " requested to borrow \"" + calculator.getItemName() + "\" from "
                        + pendingCalculator.getStartDate() + " to " + pendingCalculator.getReturnDate() + ".",
                pendingCalculator.getId());
        notification(priya, NotificationType.BORROW_REQUEST,
                sara.getName() + " requested to borrow \"" + labCoat.getItemName() + "\" from "
                        + pendingCoat.getStartDate() + " to " + pendingCoat.getReturnDate() + ".",
                pendingCoat.getId());
        notification(rohan, NotificationType.REQUEST_APPROVED,
                "Your request for \"" + textbook.getItemName() + "\" was approved. Coordinate the handover with "
                        + aarav.getName() + ".", borrowedTextbook.getId());
        notification(rohan, NotificationType.HANDOVER_COMPLETED,
                "Handover confirmed for \"" + textbook.getItemName() + "\". Please return it by "
                        + borrowedTextbook.getReturnDate() + ".", active.getId());
        notification(aarav, NotificationType.HANDOVER_COMPLETED,
                "\"" + textbook.getItemName() + "\" was handed over to " + rohan.getName() + ".",
                active.getId());

        review(priya, sara, completed, 5, "Sara returned the lab coat clean and on time. A reliable borrower!");
        review(sara, priya, completed, 5, "Priya was quick to respond and the coat was exactly as described.");

        log.info("Seeded BorrowBuddy demo data: 4 students, 10 items, requests and reviews.");
    }

    private Student student(String name, String email, String studentId) {
        Student student = new Student();
        student.setName(name);
        student.setCollegeEmail(email);
        student.setStudentId(studentId);
        student.setPassword(passwordEncoder.encode("Password123"));
        student.setRole(Role.STUDENT);
        return studentRepository.save(student);
    }

    private Item item(Student owner, String name, String category, String description,
                      ItemCondition condition, BigDecimal pricePerDay, BigDecimal securityDeposit,
                      String contactPhone) {
        Item item = new Item();
        item.setOwner(owner);
        item.setItemName(name);
        item.setCategory(category);
        item.setDescription(description);
        item.setItemCondition(condition);
        item.setPricePerDay(pricePerDay);
        item.setSecurityDeposit(securityDeposit);
        item.setContactPhone(contactPhone);
        item.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        return itemRepository.save(item);
    }

    private BorrowRequest request(Student borrower, Item item, LocalDate start, LocalDate end, String message) {
        BorrowRequest request = new BorrowRequest();
        request.setItem(item);
        request.setBorrower(borrower);
        request.setOwner(item.getOwner());
        request.setRequestDate(LocalDate.now());
        request.setStartDate(start);
        request.setReturnDate(end);
        request.setMessage(message == null || message.isBlank() ? null : message);
        request.setStatus(RequestStatus.PENDING);
        return requestRepository.save(request);
    }

    private BorrowTransaction transaction(BorrowRequest request, TransactionStatus status,
                                          Instant handover, Instant returned) {
        BorrowTransaction transaction = new BorrowTransaction();
        transaction.setRequest(request);
        transaction.setStatus(status);
        transaction.setHandoverDate(handover);
        transaction.setActualReturnDate(returned);
        return transactionRepository.save(transaction);
    }

    private void review(Student reviewer, Student reviewed, BorrowTransaction transaction,
                        int rating, String comment) {
        Review review = new Review();
        review.setReviewer(reviewer);
        review.setReviewedStudent(reviewed);
        review.setTransaction(transaction);
        review.setRating(rating);
        review.setComment(comment);
        reviewRepository.save(review);
    }

    private void notification(Student student, NotificationType type, String message, Long referenceId) {
        Notification notification = new Notification();
        notification.setStudent(student);
        notification.setNotificationType(type);
        notification.setMessage(message);
        notification.setReferenceId(referenceId);
        notification.setLink("notifications.html");
        notificationRepository.save(notification);
    }
}
