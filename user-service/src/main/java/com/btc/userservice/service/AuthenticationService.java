package com.btc.userservice.service;

import com.btc.userservice.dto.AuthLoginRequestDto;
import com.btc.userservice.dto.AuthResponseDto;

public interface AuthenticationService {

    AuthResponseDto login(AuthLoginRequestDto requestDto);
}
