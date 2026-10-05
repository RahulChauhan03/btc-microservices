package com.btc.userservice.service;

import com.btc.userservice.dto.UserRequestDto;
import com.btc.userservice.dto.UserResponseDto;
import com.btc.userservice.dto.UserStatsDto;
import com.btc.userservice.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponseDto createUser(UserRequestDto requestDto, CurrentUser actor);

    UserResponseDto getUserById(Long id, CurrentUser actor);

    Page<UserResponseDto> getAllUsers(CurrentUser actor, String query, String role, Pageable pageable);

    UserStatsDto getStats(CurrentUser actor);

    UserResponseDto updateUser(Long id, UserRequestDto requestDto, CurrentUser actor);

    void deleteUser(Long id, CurrentUser actor);
}
