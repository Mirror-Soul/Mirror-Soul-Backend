package com.mirrorsoul.mirrorsoul_api.common.apiPayload.code;

import org.springframework.http.HttpStatus;

public interface BaseErrorCode {
    /**
 * Retrieve the HTTP status associated with this error code.
 *
 * @return the HttpStatus corresponding to the error
 */
HttpStatus getHttpStatus();
    /**
 * Provides the application-specific error code that identifies this error.
 *
 * @return the error code string identifying the error condition
 */
String getCode();
    /**
 * Human-readable message describing the error.
 *
 * @return the human-readable error message
 */
String getMessage();
}
