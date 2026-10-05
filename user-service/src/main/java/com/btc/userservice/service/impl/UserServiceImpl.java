package com.btc.userservice.service.impl;

import com.btc.userservice.audit.AuditService;
import com.btc.userservice.dto.UserRequestDto;
import com.btc.userservice.dto.UserResponseDto;
import com.btc.userservice.dto.UserStatsDto;
import com.btc.userservice.entity.Role;
import com.btc.userservice.entity.User;
import com.btc.userservice.exception.DuplicateResourceException;
import com.btc.userservice.exception.InvalidRequestException;
import com.btc.userservice.exception.UserNotFoundException;
import com.btc.userservice.repository.UserRepository;
import com.btc.userservice.security.CurrentUser;
import com.btc.userservice.service.UserService;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User management rules: administrators manage all users; any user may read and update their own
 * profile but can never change a role (their own or anyone else's).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Override
    public UserResponseDto createUser(UserRequestDto requestDto, CurrentUser actor) {
        requireAdmin(actor);
        if (userRepository.existsByEmail(requestDto.getEmail())) {
            throw new DuplicateResourceException("User already exists with email: " + requestDto.getEmail());
        }
        if (requestDto.getPassword() == null || requestDto.getPassword().isBlank()) {
            throw new InvalidRequestException("Password is required");
        }

        User user = User.builder()
                .name(requestDto.getName())
                .email(requestDto.getEmail())
                .phone(requestDto.getPhone())
                .passwordHash(passwordEncoder.encode(requestDto.getPassword()))
                .role(requestDto.getRole() == null ? Role.EMPLOYEE.name() : Role.valueOf(requestDto.getRole()).name())
                .build();

        User saved = userRepository.save(user);
        auditService.record(actor.id(), "USER_CREATED", "USER", saved.getId(),
                "User #%d created with role %s".formatted(saved.getId(), saved.getRole()));
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id, CurrentUser actor) {
        requireAdminOrSelf(id, actor);
        return mapToResponse(findUserById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponseDto> getAllUsers(CurrentUser actor, String query, String role, Pageable pageable) {
        requireAdmin(actor);
        Specification<User> spec = (root, criteria, cb) -> cb.conjunction();
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, criteria, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like, '\\'),
                    cb.like(cb.lower(root.get("email")), like, '\\')));
        }
        if (role != null && !role.isBlank()) {
            String normalized = role.trim().toUpperCase(Locale.ROOT);
            if (!normalized.equals("ADMIN") && !normalized.equals("EMPLOYEE")) {
                throw new InvalidRequestException("Role must be ADMIN or EMPLOYEE");
            }
            spec = spec.and((root, criteria, cb) -> cb.equal(root.get("role"), normalized));
        }
        return userRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserStatsDto getStats(CurrentUser actor) {
        requireAdmin(actor);
        return new UserStatsDto(userRepository.count(), userRepository.countByRole("ADMIN"),
                userRepository.countByRole("EMPLOYEE"), userRepository.countByCreatedAtAfter(LocalDateTime.now().minusDays(30)));
    }

    @Override
    public UserResponseDto updateUser(Long id, UserRequestDto requestDto, CurrentUser actor) {
        requireAdminOrSelf(id, actor);
        User existingUser = findUserById(id);

        if (userRepository.existsByEmailAndIdNot(requestDto.getEmail(), id)) {
            throw new DuplicateResourceException("User already exists with email: " + requestDto.getEmail());
        }

        String previousRole = existingUser.getRole();
        existingUser.setName(requestDto.getName());
        existingUser.setEmail(requestDto.getEmail());
        existingUser.setPhone(requestDto.getPhone());
        if (requestDto.getPassword() != null && !requestDto.getPassword().isBlank()) {
            if (actor.owns(id)) {
                // Own password changes must prove the current password (POST /users/me/password).
                throw new InvalidRequestException("Change your own password in Profile & settings (your current password is required)");
            }
            existingUser.setPasswordHash(passwordEncoder.encode(requestDto.getPassword()));
            auditService.record(actor.id(), "USER_PASSWORD_CHANGED", "USER", id,
                    "Password of user #%d changed by user #%d".formatted(id, actor.id()));
        }
        // Roles change only through an administrator acting on another account; a role sent by anyone else is ignored.
        if (actor.admin() && requestDto.getRole() != null) {
            Role requested = Role.valueOf(requestDto.getRole());
            if (actor.owns(id) && requested != Role.ADMIN) {
                throw new InvalidRequestException("Administrators cannot remove their own ADMIN role");
            }
            existingUser.setRole(requested.name());
            if (!requested.name().equals(previousRole)) {
                auditService.record(actor.id(), "USER_ROLE_CHANGED", "USER", id,
                        "Role of user #%d changed from %s to %s".formatted(id, previousRole, requested.name()));
            }
        }

        return mapToResponse(userRepository.save(existingUser));
    }

    @Override
    public void deleteUser(Long id, CurrentUser actor) {
        requireAdmin(actor);
        if (actor.owns(id)) {
            throw new InvalidRequestException("Administrators cannot delete their own account");
        }
        User user = findUserById(id);
        userRepository.delete(user);
        auditService.record(actor.id(), "USER_DELETED", "USER", id, "User #%d (role %s) deleted".formatted(id, user.getRole()));
    }

    private void requireAdmin(CurrentUser actor) {
        if (!actor.admin()) {
            throw new AccessDeniedException("Administrator role required");
        }
    }

    private void requireAdminOrSelf(Long id, CurrentUser actor) {
        if (!actor.admin() && !actor.owns(id)) {
            throw new AccessDeniedException("You can only access your own profile");
        }
    }

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    private UserResponseDto mapToResponse(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(Role.fromStored(user.getRole()).name())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
