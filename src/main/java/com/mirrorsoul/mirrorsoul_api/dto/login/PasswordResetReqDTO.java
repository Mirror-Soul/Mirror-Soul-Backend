package com.mirrorsoul.mirrorsoul_api.dto.login;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class PasswordResetReqDTO {
    private PasswordResetReqDTO() {
    }

    public record SendCode(
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "올바른 이메일 형식이어야 합니다.")
            String email
    ) {
    }

    public record VerifyCode(
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "올바른 이메일 형식이어야 합니다.")
            String email,
            @NotBlank(message = "인증 코드는 필수입니다.")
            String code
    ) {
    }

    public record ResetPassword(
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "올바른 이메일 형식이어야 합니다.")
            String email,
            @NotBlank(message = "새 비밀번호는 필수입니다.")
            @Pattern(
                    regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+=-]{8,20}$",
                    message = "비밀번호는 8자 이상 20자 이하이며 영문과 숫자를 포함해야 합니다."
            )
            String newPassword,
            @NotBlank(message = "새 비밀번호 확인은 필수입니다.")
            String newPasswordConfirm
    ) {
    }
}
