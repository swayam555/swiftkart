package com.swiftkart.gateway;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * Demo sign-in. Customers sign in with a name and an Indian mobile number (a real app would verify
 * an OTP here); the operations console uses a fixed demo account.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final Jwt jwt;
    private final String opsUser;
    private final String opsPassword;

    public AuthController(Jwt jwt, @Value("${swiftkart.ops.username:ops}") String opsUser,
                          @Value("${swiftkart.ops.password:swiftkart}") String opsPassword) {
        this.jwt = jwt;
        this.opsUser = opsUser;
        this.opsPassword = opsPassword;
    }

    @PostMapping("/customer")
    public Map<String, String> customer(@Valid @RequestBody CustomerLogin req) {
        String id = "cust-" + req.phone();
        String token = jwt.issue(id, req.name().trim(), "CUSTOMER", Duration.ofDays(7));
        return Map.of("accessToken", token, "userId", id, "name", req.name().trim(), "role", "CUSTOMER");
    }

    @PostMapping("/ops")
    public ResponseEntity<Map<String, String>> ops(@RequestBody OpsLogin req) {
        if (!opsUser.equals(req.username()) || !opsPassword.equals(req.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password"));
        }
        String token = jwt.issue("ops-" + req.username(), "Ops Control Room", "OPS", Duration.ofHours(12));
        return ResponseEntity.ok(Map.of("accessToken", token, "userId", "ops-" + req.username(),
                "name", "Ops Control Room", "role", "OPS"));
    }

    public record CustomerLogin(@NotBlank String name,
                                @Pattern(regexp = "^[6-9][0-9]{9}$", message = "must be a 10-digit Indian mobile number")
                                String phone) {
    }

    public record OpsLogin(String username, String password) {
    }
}
