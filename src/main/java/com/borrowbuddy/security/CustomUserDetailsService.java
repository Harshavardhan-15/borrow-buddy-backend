package com.borrowbuddy.security;

import com.borrowbuddy.entity.Student;
import com.borrowbuddy.repository.StudentRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final StudentRepository studentRepository;

    public CustomUserDetailsService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Student student = studentRepository.findByCollegeEmail(username.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));
        return User.withUsername(student.getCollegeEmail())
                .password(student.getPassword())
                .authorities("ROLE_" + student.getRole().name())
                .build();
    }
}
