package com.borrowbuddy.service;

import com.borrowbuddy.entity.Student;
import com.borrowbuddy.exception.UnauthorizedException;
import com.borrowbuddy.repository.StudentRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final StudentRepository studentRepository;

    public CurrentUserService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Student requireCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Please log in to continue");
        }
        return studentRepository.findByCollegeEmail(authentication.getName().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException("Session is no longer valid"));
    }

    public boolean isAdmin(Student student) {
        return student != null && student.getRole() == com.borrowbuddy.entity.enums.Role.ADMIN;
    }

    public Student currentOrNull() {
        try {
            return requireCurrent();
        } catch (UnauthorizedException ex) {
            return null;
        }
    }
}
