package com.mirrorsoul.mirrorsoul_api.access;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import org.springframework.core.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@Order(Ordered.HIGHEST_PRECEDENCE) @RestControllerAdvice(assignableTypes={AdminStaffController.class,StaffInvitationController.class,com.mirrorsoul.mirrorsoul_api.controller.CloneTrainingController.class,com.mirrorsoul.mirrorsoul_api.controller.ValueBalanceAnalysisController.class,com.mirrorsoul.mirrorsoul_api.controller.AiTalkLogController.class,com.mirrorsoul.mirrorsoul_api.controller.AiCallContextController.class})
public class AccessValidationAdvice {
 @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class}) public ResponseEntity<ApiResponse<Object>> invalid(Exception e) {return ResponseEntity.badRequest().body(ApiResponse.onFailure(GeneralErrorCode.INVALID_PARAMETER,"요청 형식을 확인해 주세요."));}
}
