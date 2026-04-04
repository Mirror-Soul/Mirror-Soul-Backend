package com.mirrorsoul.mirrorsoul_api.dto.join;

import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

public class JoinReqDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class basicProfileReqDTO {

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        private String email;

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+=-]{8,20}$",
                message = "비밀번호는 영문 + 숫자를 포함해야 합니다."
        )
        private String password;
        private Gender gender;
        private LocalDate birthDate;

    }

    @Getter
    @Setter
    public static class verifyCodeReqDTO {
        private String code;
    }

}
