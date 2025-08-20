package com.puchkov.authservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class ProtectedController {

    @GetMapping("/public")
    public ResponseEntity<String> publicEndpoint() {
        return ResponseEntity.ok("Это публичный эндпоинт, доступен всем");
    }

    @GetMapping("/protected")
    public ResponseEntity<String> protectedEndpoint(Principal principal) {
        String email = principal.getName();
        return ResponseEntity.ok("Это защищённый эндпоинт. Вы аутентифицированы по почте: " + email);
    }
}
