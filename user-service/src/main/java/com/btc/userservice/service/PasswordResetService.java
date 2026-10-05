package com.btc.userservice.service;

import com.btc.userservice.dto.ResetPasswordRequestDto;

public interface PasswordResetService {

    /** Same outcome whether or not the address belongs to an account. */
    void requestReset(String email, String clientKey);

    void resetPassword(ResetPasswordRequestDto request, String clientKey);
}
