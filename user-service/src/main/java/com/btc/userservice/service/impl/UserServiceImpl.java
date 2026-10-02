package com.btc.userservice.service.impl;

import com.btc.userservice.dto.UserRequestDto;
import com.btc.userservice.dto.UserResponseDto;
import com.btc.userservice.entity.Role;
import com.btc.userservice.entity.User;
import com.btc.userservice.exception.DuplicateResourceException;
import com.btc.userservice.exception.InvalidRequestException;
import com.btc.userservice.exception.UserNotFoundException;
import com.btc.userservice.repository.UserRepository;
import com.btc.userservice.security.CurrentUser;
import com.btc.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

        return mapToResponse(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id, CurrentUser actor) {
        requireAdminOrSelf(id, actor);
        return mapToResponse(findUserById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponseDto> getAllUsers(CurrentUser actor, Pageable pageable) {
        requireAdmin(actor);
        return userRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    public UserResponseDto updateUser(Long id, UserRequestDto requestDto, CurrentUser actor) {
        requireAdminOrSelf(id, actor);
        User existingUser = findUserById(id);

        if (userRepository.existsByEmailAndIdNot(requestDto.getEmail(), id)) {
            throw new DuplicateResourceException("User already exists with email: " + requestDto.getEmail());
        }

        existingUser.setName(requestDto.getName());
        existingUser.setEmail(requestDto.getEmail());
        existingUser.setPhone(requestDto.getPhone());
        if (requestDto.getPassword() != null && !requestDto.getPassword().isBlank()) {
            existingUser.setPasswordHash(passwordEncoder.encode(requestDto.getPassword()));
        }
        // Roles change only through an administrator acting on another account; a role sent by anyone else is ignored.
        if (actor.admin() && requestDto.getRole() != null) {
            Role requested = Role.valueOf(requestDto.getRole());
            if (actor.owns(id) && requested != Role.ADMIN) {
                throw new InvalidRequestException("Administrators cannot remove their own ADMIN role");
            }
            existingUser.setRole(requested.name());
        }

        return mapToResponse(userRepository.save(existingUser));
    }

    @Override
    public void deleteUser(Long id, CurrentUser actor) {
        requireAdmin(actor);
        if (actor.owns(id)) {
            throw new InvalidRequestException("Administrators cannot delete their own account");
        }
        userRepository.delete(findUserById(id));
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
