package com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.BaseErrorCode;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    // 예외에서 발생한 에러의 상세 내용
    private final BaseErrorCode code;

    /**
     * Create a GeneralException with the specified application error code.
     *
     * @param code the application-specific {@code BaseErrorCode} that identifies the error condition
     */
    public GeneralException(BaseErrorCode code) {
        this.code = code;
    }

    /**
     * Creates a GeneralException with the specified error code and detail message.
     *
     * @param code    the application-specific error code describing the failure
     * @param message the detail message for this exception
     */
    public GeneralException(BaseErrorCode code,String message) {
        super(message);
        this.code = code;
    }
}
