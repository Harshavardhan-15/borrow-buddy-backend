package com.borrowbuddy.service;

import com.borrowbuddy.dto.report.CreateReportRequest;
import com.borrowbuddy.dto.report.ReportDto;
import com.borrowbuddy.entity.Report;
import com.borrowbuddy.entity.Student;
import com.borrowbuddy.entity.enums.NotificationType;
import com.borrowbuddy.entity.enums.ReportStatus;
import com.borrowbuddy.entity.enums.Role;
import com.borrowbuddy.exception.BusinessException;
import com.borrowbuddy.exception.ResourceNotFoundException;
import com.borrowbuddy.repository.ItemRepository;
import com.borrowbuddy.repository.ReportRepository;
import com.borrowbuddy.repository.ReviewRepository;
import com.borrowbuddy.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final StudentRepository studentRepository;
    private final ItemRepository itemRepository;
    private final ReviewRepository reviewRepository;
    private final CurrentUserService currentUserService;
    private final MapperService mapperService;
    private final NotificationService notificationService;

    public ReportService(ReportRepository reportRepository,
                         StudentRepository studentRepository,
                         ItemRepository itemRepository,
                         ReviewRepository reviewRepository,
                         CurrentUserService currentUserService,
                         MapperService mapperService,
                         NotificationService notificationService) {
        this.reportRepository = reportRepository;
        this.studentRepository = studentRepository;
        this.itemRepository = itemRepository;
        this.reviewRepository = reviewRepository;
        this.currentUserService = currentUserService;
        this.mapperService = mapperService;
        this.notificationService = notificationService;
    }

    @Transactional
    public ReportDto create(Student current, CreateReportRequest request) {
        boolean targetExists = switch (request.targetType()) {
            case USER -> studentRepository.existsById(request.targetId());
            case ITEM -> itemRepository.existsById(request.targetId());
            case REVIEW -> reviewRepository.existsById(request.targetId());
        };
        if (!targetExists) {
            throw new ResourceNotFoundException("Reported item was not found");
        }

        Report report = new Report();
        report.setReporter(current);
        report.setTargetType(request.targetType());
        report.setTargetId(request.targetId());
        report.setReason(request.reason().trim());
        report.setStatus(ReportStatus.OPEN);
        Report saved = reportRepository.save(report);

        for (Student admin : studentRepository.findByRole(Role.ADMIN)) {
            notificationService.notify(admin, NotificationType.REPORT_RECEIVED,
                    "New " + request.targetType().name().toLowerCase() + " report from " + current.getName() + ".",
                    saved.getId(), "dashboard.html");
        }
        return mapperService.toReportDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ReportDto> list(Student current, ReportStatus status) {
        requireAdmin(current);
        List<Report> reports = status == null
                ? reportRepository.findAllByOrderByCreatedAtDesc()
                : reportRepository.findByStatusOrderByCreatedAtDesc(status);
        return reports.stream().map(mapperService::toReportDto).toList();
    }

    @Transactional
    public ReportDto updateStatus(Student current, Long id, ReportStatus status) {
        requireAdmin(current);
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        report.setStatus(status);
        return mapperService.toReportDto(reportRepository.save(report));
    }

    private void requireAdmin(Student current) {
        if (!currentUserService.isAdmin(current)) {
            throw new BusinessException("Administrator access required", HttpStatus.FORBIDDEN);
        }
    }
}
