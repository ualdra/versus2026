package com.versus.api.users;

import com.versus.api.users.dto.UpdateMeRequest;
import com.versus.api.users.dto.UserMeResponse;
import com.versus.api.users.dto.UserPublicResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserMeResponse me(@AuthenticationPrincipal UUID userId) {
        return userService.getMe(userId);
    }

    @PutMapping("/me")
    public UserMeResponse updateMe(@AuthenticationPrincipal UUID userId,
                                   @Valid @RequestBody UpdateMeRequest req) {
        return userService.updateMe(userId, req);
    }

    @GetMapping("/{id}")
    public UserPublicResponse byId(@PathVariable("id") UUID id) {
        return userService.getPublic(id);
    }
}
