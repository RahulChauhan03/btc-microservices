package com.btc.userservice.service;

import com.btc.userservice.dto.UserRequestDto;
import com.btc.userservice.dto.UserResponseDto;
import java.util.List;

public interface UserService {

    UserResponseDto createUser(UserRequestDto requestDto);

    UserResponseDto getUserById(Long id);

    List<UserResponseDto> getAllUsers();

    UserResponseDto updateUser(Long id, UserRequestDto requestDto);

    void deleteUser(Long id);
}
