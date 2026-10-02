package com.btc.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRequestDto {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Phone is required")
    private String phone;

    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    /** Optional. Applied only when an administrator creates or updates another user; ignored otherwise. */
    @Pattern(regexp = "ADMIN|EMPLOYEE", message = "Role must be ADMIN or EMPLOYEE")
    private String role;
}
