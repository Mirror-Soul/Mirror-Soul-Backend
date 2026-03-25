package com.mirrorsoul.mirrorsoul_api.common.apiPayload;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.BaseErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({"isSuccess", "code", "message", "result", "error"})
public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    private Boolean isSuccess;

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("result")
    private final T result;

    @JsonProperty("error")
    private Object error;

    /**
     * Create a successful ApiResponse containing the provided result payload.
     *
     * @param message human-readable message describing the success
     * @param result  the response payload to include
     * @param <T>     the type of the result payload
     * @return an ApiResponse containing `isSuccess = true`, the success code for OK, the given message and result, and `error = null`
     */
    public static <T> ApiResponse<T> onSuccess(String message,T result) {
        return new ApiResponse<>(true, GeneralSuccessCode.OK.getCode(), message, result, null);
    }

    /**
     * Create a successful ApiResponse without a result.
     *
     * @param message human-readable message to include in the response
     * @param <T> the expected type of the response result
     * @return an ApiResponse containing the OK code and the provided message, with `null` result and `null` error
     */
    public static <T> ApiResponse<T> onSuccess(String message) {
        return new ApiResponse<>(true, GeneralSuccessCode.OK.getCode(), message, null, null);
    }

    /**
     * Create a failed API response containing the provided error code and error details.
     *
     * The response's `result` field will be `null`.
     *
     * @param errorCode the error code object providing the response code and message
     * @param error additional error details to include in the response (may be null)
     * @return an `ApiResponse<T>` with `isSuccess` set to `false`, the code and message from `errorCode`, a `null` result, and the provided error details
     */
    public static <T> ApiResponse<T> onFailure(BaseErrorCode errorCode, Object error) {
        return new ApiResponse<>(false,errorCode.getCode(),errorCode.getMessage(),null,error);
    }

}
