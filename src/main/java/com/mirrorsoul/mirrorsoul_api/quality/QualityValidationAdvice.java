package com.mirrorsoul.mirrorsoul_api.quality;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import org.springframework.core.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes={AdminQualityController.class,InternalQualityController.class})
public class QualityValidationAdvice {
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Object>> invalid(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.onFailure(GeneralErrorCode.QUALITY_INVALID,"품질 요청 형식을 확인해 주세요."));
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> conflict(Exception e) {
        return ResponseEntity.status(409).body(ApiResponse.onFailure(GeneralErrorCode.QUALITY_CONFLICT,"이벤트 또는 재시도 키 충돌입니다."));
    }
}
