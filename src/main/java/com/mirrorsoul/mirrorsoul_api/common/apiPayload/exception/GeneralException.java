package com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.BaseErrorCode;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    // 예외에서 발생한 에러의 상세 내용
    private final BaseErrorCode code;

    // 생성자
    public GeneralException(BaseErrorCode code) {
        this.code = code;
    }

    public GeneralException(BaseErrorCode code,String message) {
        super(message);
        this.code = code;
    }
}
