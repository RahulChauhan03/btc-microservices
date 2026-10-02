package com.btc.userservice.controller;

import com.btc.userservice.dto.UserRequestDto;
import com.btc.userservice.dto.UserResponseDto;
import com.btc.userservice.security.CurrentUser;
import com.btc.userservice.service.UserService;
import com.btc.userservice.web.PageRequests;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private static final Set<String> SORTABLE = Set.of("id", "name", "email", "createdAt");

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody UserRequestDto requestDto,
                                                      @AuthenticationPrincipal Jwt jwt) {
        UserResponseDto createdUser = userService.createUser(requestDto, CurrentUser.from(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getUserById(id, CurrentUser.from(jwt)));
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDto>> getAllUsers(
            @RequestParam(defaultValue = PageRequests.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequests.DEFAULT_SIZE) int size,
            @RequestParam(defaultValue = PageRequests.DEFAULT_SORT) String sort,
            @AuthenticationPrincipal Jwt jwt) {
        return PageRequests.toResponse(
                userService.getAllUsers(CurrentUser.from(jwt), PageRequests.of(page, size, sort, SORTABLE)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDto> updateUser(@PathVariable Long id,
                                                      @Valid @RequestBody UserRequestDto requestDto,
                                                      @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.updateUser(id, requestDto, CurrentUser.from(jwt)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        userService.deleteUser(id, CurrentUser.from(jwt));
        return ResponseEntity.noContent().build();
    }
}
