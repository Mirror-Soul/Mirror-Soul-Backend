package com.mirrorsoul.mirrorsoul_api.common.apiPayload.code;

import org.springframework.http.HttpStatus;

public interface BaseSuccessCode {
    HttpStatus getHttpStatus();
    String getCode();
    String getMessage();
}
