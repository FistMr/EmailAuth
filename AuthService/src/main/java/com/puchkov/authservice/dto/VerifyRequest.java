package com.puchkov.authservice.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerifyRequest {
    @Email
    private String email;
    private String code;
}
