package com.borrowbuddy.service;

import com.borrowbuddy.dto.item.ItemDto;
import com.borrowbuddy.dto.student.StudentProfileDto;
import com.borrowbuddy.dto.student.UpdateProfileRequest;
import com.borrowbuddy.entity.Student;
import com.borrowbuddy.entity.enums.RequestStatus;
import com.borrowbuddy.entity.enums.TransactionStatus;
import com.borrowbuddy.exception.BusinessException;
import com.borrowbuddy.exception.ResourceNotFoundException;
import com.borrowbuddy.repository.BorrowRequestRepository;
import com.borrowbuddy.repository.BorrowTransactionRepository;
import com.borrowbuddy.repository.ItemRepository;
import com.borrowbuddy.repository.ReviewRepository;
import com.borrowbuddy.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final ItemRepository itemRepository;
    private final BorrowRequestRepository requestRepository;
    private final BorrowTransactionRepository transactionRepository;
    private final ReviewRepository reviewRepository;
    private final MapperService mapperService;

    public StudentService(StudentRepository studentRepository,
                          ItemRepository itemRepository,
                          BorrowRequestRepository requestRepository,
                          BorrowTransactionRepository transactionRepository,
                          ReviewRepository reviewRepository,
                          MapperService mapperService) {
        this.studentRepository = studentRepository;
        this.itemRepository = itemRepository;
        this.requestRepository = requestRepository;
        this.transactionRepository = transactionRepository;
        this.reviewRepository = reviewRepository;
        this.mapperService = mapperService;
    }

    @Transactional(readOnly = true)
    public StudentProfileDto profile(Student current) {
        return buildProfile(current, current);
    }

    @Transactional(readOnly = true)
    public StudentProfileDto publicProfile(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        return buildProfile(student, null);
    }

    @Transactional
    public StudentProfileDto updateProfile(Student current, UpdateProfileRequest request) {
        String studentId = request.studentId().trim().toUpperCase();
        if (!studentId.equals(current.getStudentId()) && studentRepository.existsByStudentId(studentId)) {
            throw new BusinessException("This student ID is already registered", HttpStatus.CONFLICT);
        }
        String image = request.profileImage();
        if (image != null && !image.isBlank()
                && !image.startsWith("/uploads/") && !image.startsWith("http://") && !image.startsWith("https://")) {
            throw new BusinessException("Invalid profile image URL");
        }

        current.setName(request.name().trim());
        current.setStudentId(studentId);
        current.setProfileImage(image == null || image.isBlank() ? null : image);
        Student saved = studentRepository.save(current);
        return buildProfile(saved, saved);
    }

    private StudentProfileDto buildProfile(Student student, Student viewer) {
        List<ItemDto> items = itemRepository
                .findByOwnerIdAndDeletedFalseOrderByCreatedAtDesc(student.getId())
                .stream().map(item -> mapperService.toItemDto(item, viewer)).toList();

        long listedItems = itemRepository.countByOwnerIdAndDeletedFalse(student.getId());
        long totalBorrowed = requestRepository.countByBorrowerIdAndStatusIn(
                student.getId(), List.of(RequestStatus.APPROVED, RequestStatus.COMPLETED));
        long totalLent = requestRepository.countByOwnerIdAndStatusIn(
                student.getId(), List.of(RequestStatus.APPROVED, RequestStatus.COMPLETED));
        long completedBorrows = transactionRepository.countByRequestBorrowerIdAndStatusIn(
                student.getId(), List.of(TransactionStatus.COMPLETED));
        long completedLends = transactionRepository.countByRequestOwnerIdAndStatusIn(
                student.getId(), List.of(TransactionStatus.COMPLETED));

        return new StudentProfileDto(
                mapperService.toUserDto(student),
                listedItems,
                totalBorrowed,
                totalLent,
                completedBorrows,
                completedLends,
                reviewRepository.averageRatingFor(student.getId()),
                reviewRepository.countByReviewedStudentId(student.getId()),
                items
        );
    }
}
