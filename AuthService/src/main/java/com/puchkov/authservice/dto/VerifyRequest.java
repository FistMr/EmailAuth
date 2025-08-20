package com.puchkov.authservice.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class VerifyRequest {
    @Email
    private String email;
    private String code;
}
