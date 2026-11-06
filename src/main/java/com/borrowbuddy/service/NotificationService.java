package com.borrowbuddy.service;

import com.borrowbuddy.dto.PageResponse;
import com.borrowbuddy.dto.notification.NotificationDto;
import com.borrowbuddy.entity.Notification;
import com.borrowbuddy.entity.Student;
import com.borrowbuddy.entity.enums.NotificationType;
import com.borrowbuddy.exception.ResourceNotFoundException;
import com.borrowbuddy.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final MapperService mapperService;

    public NotificationService(NotificationRepository notificationRepository,
                               EmailService emailService,
                               MapperService mapperService) {
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
        this.mapperService = mapperService;
    }

    @Transactional
    public void notify(Student student, NotificationType type, String message, Long referenceId, String link) {
        Notification notification = new Notification();
        notification.setStudent(student);
        notification.setNotificationType(type);
        notification.setMessage(message);
        notification.setReferenceId(referenceId);
        notification.setLink(link);
        notificationRepository.save(notification);
        emailService.sendNotification(student, message);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(Student current, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Page<Notification> result = notificationRepository
                .findByStudentIdOrderByCreatedAtDesc(current.getId(), pageable);
        return PageResponse.from(result.map(mapperService::toNotificationDto));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Student current) {
        return notificationRepository.countByStudentIdAndReadFalse(current.getId());
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> recent(Student current) {
        return notificationRepository.findTop6ByStudentIdOrderByCreatedAtDesc(current.getId())
                .stream().map(mapperService::toNotificationDto).toList();
    }

    @Transactional
    public NotificationDto markRead(Student current, Long id) {
        Notification notification = notificationRepository.findByIdAndStudentId(id, current.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setRead(true);
        return mapperService.toNotificationDto(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllRead(Student current) {
        return notificationRepository.markAllRead(current.getId());
    }
}
