package com.mirrorsoul.mirrorsoul_api.dto.login;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginReqDTO(
        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "올바른 이메일 형식이어야 합니다.")
        String email,

        @NotBlank(message = "password는 필수입니다.")
        String password
) {
}
