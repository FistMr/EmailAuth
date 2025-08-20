package com.puchkov.authservice.controller;

import com.puchkov.authservice.dto.AuthResponse;
import com.puchkov.authservice.dto.RegisterRequest;
import com.puchkov.authservice.dto.VerifyRequest;
import com.puchkov.authservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody @Valid RegisterRequest request){
        authService.registerUser(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verify(@RequestBody  @Valid VerifyRequest request){
        String token = authService.verifyCode(request.getEmail(),request.getCode());
        return ResponseEntity.ok(new AuthResponse(token));
    }
}
