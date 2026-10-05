package com.btc.userservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.btc.userservice.audit.AuditService;
import com.btc.userservice.dto.UserRequestDto;
import com.btc.userservice.dto.UserResponseDto;
import com.btc.userservice.entity.User;
import com.btc.userservice.exception.InvalidRequestException;
import com.btc.userservice.repository.UserRepository;
import com.btc.userservice.security.CurrentUser;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserServiceImplTests {

    private static final CurrentUser ADMIN = new CurrentUser(1L, true);
    private static final CurrentUser EMPLOYEE = new CurrentUser(2L, false);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final UserServiceImpl userService = new UserServiceImpl(userRepository, new BCryptPasswordEncoder(4), auditService);

    @BeforeEach
    void setUp() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void employeeCannotListCreateOrDeleteUsers() {
        assertThatThrownBy(() -> userService.getAllUsers(EMPLOYEE, null, null, Pageable.unpaged())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.createUser(request("ADMIN"), EMPLOYEE))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.deleteUser(3L, EMPLOYEE)).isInstanceOf(AccessDeniedException.class);

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void employeeCannotReadOrUpdateAnotherUser() {
        assertThatThrownBy(() -> userService.getUserById(3L, EMPLOYEE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.updateUser(3L, request(null), EMPLOYEE))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void employeeCannotGrantThemselvesAdminThroughProfileUpdate() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "EMPLOYEE")));

        UserRequestDto selfUpdate = request("ADMIN");
        selfUpdate.setPassword(null);
        UserResponseDto updated = userService.updateUser(2L, selfUpdate, EMPLOYEE);

        assertThat(updated.getRole()).isEqualTo("EMPLOYEE");
        assertThat(updated.getName()).isEqualTo("New Name");
    }

    @Test
    void ownPasswordChangesMustGoThroughTheVerifiedProfileFlow() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "EMPLOYEE")));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "ADMIN")));

        assertThatThrownBy(() -> userService.updateUser(2L, request(null), EMPLOYEE)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> userService.updateUser(1L, request("ADMIN"), ADMIN)).isInstanceOf(InvalidRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void adminCreatesUsersWithEmployeeAsDefaultRole() {
        assertThat(userService.createUser(request(null), ADMIN).getRole()).isEqualTo("EMPLOYEE");
        assertThat(userService.createUser(request("ADMIN"), ADMIN).getRole()).isEqualTo("ADMIN");
    }

    @Test
    void adminCanChangeAnotherUsersRoleButNotDemoteOrDeleteThemselves() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(user(3L, "EMPLOYEE")));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "ADMIN")));

        assertThat(userService.updateUser(3L, request("ADMIN"), ADMIN).getRole()).isEqualTo("ADMIN");
        assertThatThrownBy(() -> userService.updateUser(1L, request("EMPLOYEE"), ADMIN))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> userService.deleteUser(1L, ADMIN)).isInstanceOf(InvalidRequestException.class);
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void storedRolesAreNormalisedToSupportedValues() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, null)));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user(3L, "SUPER_ADMIN")));

        assertThat(userService.getUserById(2L, ADMIN).getRole()).isEqualTo("EMPLOYEE");
        assertThat(userService.getUserById(3L, ADMIN).getRole()).isEqualTo("EMPLOYEE");
    }

    private static UserRequestDto request(String role) {
        return UserRequestDto.builder()
                .name("New Name")
                .email("someone@example.com")
                .phone("123")
                .password("long-enough-password")
                .role(role)
                .build();
    }

    private static User user(Long id, String role) {
        return User.builder().id(id).name("Old").email("u" + id + "@example.com").phone("1").role(role).build();
    }
}
