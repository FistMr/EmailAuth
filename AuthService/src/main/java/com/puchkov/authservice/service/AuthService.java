package com.puchkov.authservice.service;

import jakarta.validation.constraints.Email;

public interface AuthService {
    void registerUser(@Email String email);

    String verifyCode(@Email String email, String code);
}
