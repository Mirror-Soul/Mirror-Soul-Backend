package com.mirrorsoul.mirrorsoul_api.safety;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes={MemberSafetyController.class,AdminSafetyController.class,InternalSafetyController.class})
public class SafetyValidationAdvice {
    // Do not log rejected report descriptions, evidence, or appeal text.
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Object>> invalidBody(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.onFailure(GeneralErrorCode.INVALID_PARAMETER,"신고 요청 형식을 확인해 주세요."));
    }
}
