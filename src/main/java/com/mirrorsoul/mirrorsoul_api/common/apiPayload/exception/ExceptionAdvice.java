package com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.BaseErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class ExceptionAdvice {

    /**
     * Handles a GeneralException and converts it into a standardized failure HTTP response.
     *
     * Uses the exception's BaseErrorCode to determine the HTTP status and constructs an
     * ApiResponse failure containing the code and the exception message.
     *
     * @param e the GeneralException whose BaseErrorCode and message will be used for the response
     * @return a ResponseEntity containing an ApiResponse failure populated with the exception's BaseErrorCode and message; the HTTP status is taken from the code
     */
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<Object>> handleCustomException(GeneralException e) {
        BaseErrorCode code = e.getCode();
        log.error("GeneralException: code={}, message={}", code.getCode(), code.getMessage(), e);

        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ApiResponse.onFailure(code, e.getMessage()));
    }

    /**
     * Handle validation failures for `@Valid` request bodies.
     *
     * Extracts the first validation error message, logs the validation failure, and responds with a
     * 400 BAD_REQUEST ApiResponse indicating an invalid parameter.
     *
     * @param e the MethodArgumentNotValidException containing binding errors; the first error's default message is used as the response detail
     * @return a ResponseEntity containing an ApiResponse failure with GeneralErrorCode.INVALID_PARAMETER and the first validation error message
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException e) {
        String errorDetail = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.error("Validation Exception: {}", errorDetail, e);

        BaseErrorCode code = GeneralErrorCode.INVALID_PARAMETER;
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure(code, errorDetail));
    }

    /**
     * Handles requests that are missing a required servlet request parameter.
     *
     * @param e the exception containing the name of the missing parameter
     * @return a ResponseEntity with a failure ApiResponse using GeneralErrorCode.MISSING_PARAMETER
     *         and a message prefixed with "누락된 파라미터: " followed by the missing parameter name
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParams(MissingServletRequestParameterException e) {
        log.error("Missing Parameter: {}", e.getParameterName(), e);

        BaseErrorCode code = GeneralErrorCode.MISSING_PARAMETER;
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure(code, "누락된 파라미터: " + e.getParameterName()));
    }

    /**
     * 메서드 인자 타입 불일치 예외를 처리하여 클라이언트에 실패 응답을 반환합니다.
     *
     * Logs the mismatched parameter name and returns a failure ApiResponse containing the parameter name in the message.
     *
     * @return ResponseEntity containing an ApiResponse failure with GeneralErrorCode.INVALID_PARAMETER and HTTP 400 Bad Request.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.error("Type Mismatch: {}", e.getName(), e);

        BaseErrorCode code = GeneralErrorCode.INVALID_PARAMETER;
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure(
                        code,
                        String.format("파라미터 '%s'의 타입이 올바르지 않습니다", e.getName())
                ));
    }

    /**
     * Handle unreadable HTTP request bodies (for example, malformed JSON).
     *
     * @param e the exception thrown when the request body cannot be parsed
     * @return a ResponseEntity containing an ApiResponse failure with GeneralErrorCode.INVALID_PARAMETER, HTTP 400 (Bad Request), and the message "요청 본문을 읽을 수 없습니다"
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.error("Message Not Readable Exception", e);

        BaseErrorCode code = GeneralErrorCode.INVALID_PARAMETER;
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure(code, "요청 본문을 읽을 수 없습니다"));
    }

    /**
     * Handle requests that specify an unsupported Content-Type by returning a standardized failure response.
     *
     * @param e the thrown HttpMediaTypeNotSupportedException containing the unsupported and supported media types
     * @return a ResponseEntity with HTTP 415 (Unsupported Media Type) and an ApiResponse failure containing
     *         GeneralErrorCode.UNSUPPORTED_FILE_TYPE and a message listing the supported Content-Types
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.error("Media Type Not Supported: {}", e.getContentType(), e);

        BaseErrorCode code = GeneralErrorCode.UNSUPPORTED_FILE_TYPE;
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.onFailure(
                        code,
                        "지원되는 Content-Type: " + e.getSupportedMediaTypes()
                ));
    }

    /**
     * Handles requests routed to non-existent API endpoints.
     *
     * @param e the exception containing the HTTP method and requested URL for the missing handler
     * @return a ResponseEntity with HTTP 404 and an ApiResponse failure containing GeneralErrorCode.API_NOT_FOUND and the missing request URL
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoHandlerFound(NoHandlerFoundException e) {
        log.error("No Handler Found: {} {}", e.getHttpMethod(), e.getRequestURL(), e);

        BaseErrorCode code = GeneralErrorCode.API_NOT_FOUND;
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.onFailure(code, e.getRequestURL()));
    }

    /**
     * Handles requests using an unsupported HTTP method.
     *
     * @return a ResponseEntity containing an ApiResponse failure with the `METHOD_NOT_ALLOWED` error code and a message listing the supported HTTP methods (HTTP 405)
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.error("Method Not Supported: {}", e.getMethod(), e);

        BaseErrorCode code = GeneralErrorCode.METHOD_NOT_ALLOWED;
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.onFailure(
                        code,
                        "지원되는 메서드: " + e.getSupportedHttpMethods()
                ));
    }

    /**
     * Handle any uncaught exception and convert it into a standardized API failure response.
     *
     * @param e the exception that was thrown
     * @return a ResponseEntity containing an ApiResponse failure with GeneralErrorCode.INTERNAL_SERVER_ERROR,
     *         using the code's configured HTTP status and the exception's message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        log.error("Unhandled Exception", e);

        BaseErrorCode code = GeneralErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ApiResponse.onFailure(code, e.getMessage()));
    }
}
