package com.mirrorsoul.mirrorsoul_api.common.apiPayload.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GeneralSuccessCode implements BaseSuccessCode {

    OK(HttpStatus.OK, "COMMON2000", "성공적으로 처리했습니다."),
    DELETED(HttpStatus.OK, "COMMON2001", "성공적으로 삭제했습니다."),
    VALUE_BALANCE_DAILY_LIMIT_REACHED(
            HttpStatus.OK,
            "VALUE_BALANCE_DAILY_LIMIT_REACHED",
            "Daily value balance limit reached."
    ),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
