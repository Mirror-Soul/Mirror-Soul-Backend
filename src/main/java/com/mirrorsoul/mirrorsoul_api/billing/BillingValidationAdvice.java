package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {PaymentController.class, StoreNotificationController.class, AdminBillingController.class})
public class BillingValidationAdvice {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Object>> invalid(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.onFailure(GeneralErrorCode.PAYMENT_INVALID, "결제 요청 형식을 확인해 주세요."));
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> conflict(Exception e) {
        return ResponseEntity.status(409).body(ApiResponse.onFailure(GeneralErrorCode.PAYMENT_CONFLICT, "결제 또는 멱등키 충돌입니다."));
    }
}
