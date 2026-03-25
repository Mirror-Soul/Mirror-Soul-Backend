package com.mirrorsoul.mirrorsoul_api.common.apiPayload.code;

import org.springframework.http.HttpStatus;

public interface BaseSuccessCode {
    /**
 * Get the HTTP status associated with this success code.
 *
 * @return the HTTP status corresponding to this success code
 */
HttpStatus getHttpStatus();
    /**
 * Machine-readable identifier for the success condition.
 *
 * @return the code identifier string for the success condition
 */
String getCode();
    /**
 * Human-readable message describing the success condition.
 *
 * @return the message text associated with this success code
 */
String getMessage();
}
