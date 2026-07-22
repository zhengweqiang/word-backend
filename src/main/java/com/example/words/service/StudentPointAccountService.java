package com.example.words.service;

import com.example.words.model.StudentPointAccount;
import com.example.words.repository.StudentPointAccountRepository;
import org.springframework.stereotype.Service;

@Service
public class StudentPointAccountService {

    private final StudentPointAccountRepository studentPointAccountRepository;

    public StudentPointAccountService(StudentPointAccountRepository studentPointAccountRepository) {
        this.studentPointAccountRepository = studentPointAccountRepository;
    }

    public void createForStudent(Long studentId) {
        studentPointAccountRepository.save(StudentPointAccount.create(studentId));
    }
}
