package com.mirrorsoul.mirrorsoul_api.support;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes={MemberSupportController.class,AdminSupportController.class})
public class SupportValidationAdvice {
 @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class})
 public ResponseEntity<ApiResponse<Object>> invalidBody(Exception e) {return ResponseEntity.badRequest().body(ApiResponse.onFailure(GeneralErrorCode.INVALID_PARAMETER,"고객문의 요청 형식을 확인해 주세요."));}
 @ExceptionHandler({software.amazon.awssdk.awscore.exception.AwsServiceException.class,software.amazon.awssdk.core.exception.SdkClientException.class})
 public ResponseEntity<ApiResponse<Object>> storage(Exception e) {return ResponseEntity.status(503).body(ApiResponse.onFailure(GeneralErrorCode.SERVICE_UNAVAILABLE,"첨부 저장소에 연결할 수 없습니다."));}
}
