package com.mirrorsoul.mirrorsoul_api.overview;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=AdminOverviewController.class)
public class OverviewValidationAdvice {
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> invalid(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.onFailure(GeneralErrorCode.INVALID_PARAMETER,null));
    }
    @ExceptionHandler({DataAccessException.class,TransactionException.class})
    public ResponseEntity<ApiResponse<Object>> unavailable(Exception e) {
        return ResponseEntity.status(503).body(ApiResponse.onFailure(GeneralErrorCode.OVERVIEW_UNAVAILABLE,null));
    }
}
