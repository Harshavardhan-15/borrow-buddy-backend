package com.borrowbuddy.service;

import com.borrowbuddy.dto.auth.AuthResponse;
import com.borrowbuddy.dto.auth.LoginRequest;
import com.borrowbuddy.dto.auth.RegisterRequest;
import com.borrowbuddy.dto.auth.UserDto;
import com.borrowbuddy.entity.Student;
import com.borrowbuddy.entity.enums.Role;
import com.borrowbuddy.exception.BusinessException;
import com.borrowbuddy.repository.StudentRepository;
import com.borrowbuddy.security.JwtBlacklistService;
import com.borrowbuddy.security.JwtService;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
public class AuthService {

    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtBlacklistService blacklistService;
    private final MapperService mapperService;
    private final CurrentUserService currentUserService;

    @Value("${app.auth.allowed-email-domains:}")
    private String allowedEmailDomains;

    public AuthService(StudentRepository studentRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       JwtBlacklistService blacklistService,
                       MapperService mapperService,
                       CurrentUserService currentUserService) {
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
        this.mapperService = mapperService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.collegeEmail().trim().toLowerCase();
        validateCollegeEmail(email);

        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }
        if (studentRepository.existsByCollegeEmail(email)) {
            throw new BusinessException("An account with this college email already exists", HttpStatus.CONFLICT);
        }
        String studentId = request.studentId().trim().toUpperCase();
        if (studentRepository.existsByStudentId(studentId)) {
            throw new BusinessException("This student ID is already registered", HttpStatus.CONFLICT);
        }

        Student student = new Student();
        student.setName(request.name().trim());
        student.setCollegeEmail(email);
        student.setPassword(passwordEncoder.encode(request.password()));
        student.setStudentId(studentId);
        student.setRole(Role.STUDENT);
        Student saved = studentRepository.save(student);

        return buildAuthResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.collegeEmail().trim().toLowerCase();
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        Student student = studentRepository.findByCollegeEmail(email)
                .orElseThrow(() -> new BusinessException("Invalid email or password", HttpStatus.UNAUTHORIZED));
        return buildAuthResponse(student);
    }

    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authorizationHeader.substring(7).trim();
        try {
            Instant expiry = jwtService.extractExpiration(token);
            blacklistService.revoke(token, expiry);
        } catch (JwtException | IllegalArgumentException ignored) {
            // Expired or malformed tokens are already unusable.
        }
    }

    @Transactional(readOnly = true)
    public UserDto me() {
        return mapperService.toUserDto(currentUserService.requireCurrent());
    }

    private AuthResponse buildAuthResponse(Student student) {
        String token = jwtService.generateToken(student);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs(), mapperService.toUserDto(student));
    }

    private void validateCollegeEmail(String email) {
        List<String> domains = Arrays.stream(allowedEmailDomains.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();
        if (domains.isEmpty()) {
            return;
        }
        boolean allowed = domains.stream().anyMatch(domain -> email.endsWith("@" + domain));
        if (!allowed) {
            throw new BusinessException("Please register with a college email ending in @"
                    + String.join(" or @", domains));
        }
    }
}
