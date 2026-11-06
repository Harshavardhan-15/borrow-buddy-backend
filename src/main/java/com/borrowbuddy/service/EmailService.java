package com.borrowbuddy.service;

import com.borrowbuddy.entity.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Optional email notifications. Disabled by default; enable with MAIL_ENABLED=true
 * and valid SMTP credentials. In-app notifications always work.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${spring.mail.username:no-reply@borrowbuddy.local}")
    private String from;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendNotification(Student student, String message) {
        if (!enabled) {
            return;
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(student.getCollegeEmail());
            mail.setSubject("BorrowBuddy notification");
            mail.setText("Hi " + student.getName() + ",\n\n" + message
                    + "\n\nOpen BorrowBuddy to see details.\n\n- BorrowBuddy");
            mailSender.send(mail);
        } catch (Exception ex) {
            log.warn("Could not send notification email to {}: {}", student.getCollegeEmail(), ex.getMessage());
        }
    }
}
