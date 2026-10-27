package com.mirrorsoul.mirrorsoul_api.access;
import static org.assertj.core.api.Assertions.*;
import com.mirrorsoul.mirrorsoul_api.config.AiInternalApiProperties;
import com.mirrorsoul.mirrorsoul_api.common.jwt.*;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
class KeyRotationTest {
 @Test void previousInternalKeyExpiresAndScopesCannotCrossOperations() {
  var p=new AiInternalApiProperties("current","previous",Instant.now().plusSeconds(60),Set.of("call_context"));
  assertThat(p.matches("previous")).isTrue();assertThat(p.allows("GET","/internal/ai/calls/1/context")).isTrue();assertThat(p.allows("POST","/internal/ai/calls/1/talk-logs")).isFalse();assertThat(p.allowsSignaling()).isFalse();
  assertThat(new AiInternalApiProperties("current","previous",Instant.now().minusSeconds(1),Set.of()).matches("previous")).isFalse();assertThat(p.toString()).doesNotContain("previous","current");
  assertThat(p.allows("POST","/internal/ai/calls/1/billing-events")).isFalse();
  var billing=new AiInternalApiProperties("billing-key",null,null,Set.of("call_billing"));
  assertThat(billing.allows("POST","/internal/ai/calls/1/billing-events")).isTrue();
  assertThat(billing.allows("GET","/internal/ai/calls/1/context")).isFalse();
 }
 @Test void jwtRotationAcceptsOldSignatureOnlyDuringGraceAndSignsNewTokensWithCurrentKey() {
  String old="old-signing-secret-key-64-bytes-for-hs512-validation-1234567890123456",next="new-signing-secret-key-more-than-32-bytes";
  var user=User.builder().email("test@example.com").passwordHash("hash").build();ReflectionTestUtils.setField(user,"id",1L);
  var original=new JwtTokenProvider(new JwtProperties(old,3600,3600));String token=original.createAccessToken(user);
  var rotated=new JwtTokenProvider(new JwtProperties(next,3600,3600,old,Instant.now().plusSeconds(60)));rotated.validateAccessToken(token);
  assertThatThrownBy(()->new JwtTokenProvider(new JwtProperties(next,3600,3600,old,Instant.now().minusSeconds(1))).validateAccessToken(token)).isInstanceOf(io.jsonwebtoken.JwtException.class);
  assertThatThrownBy(()->original.validateAccessToken(rotated.createAccessToken(user))).isInstanceOf(io.jsonwebtoken.JwtException.class);
 }
 @Test void qualityScopesAreBoundToOperationMethodAndTypedJobId() {
  var call=new AiInternalApiProperties("key",null,null,Set.of("call_diagnostics"));
  assertThat(call.allows("POST","/internal/ai/calls/123/diagnostics")).isTrue();
  assertThat(call.allows("GET","/internal/ai/calls/123/diagnostics")).isFalse();
  assertThat(call.allows("POST","/internal/ai/calls/123/billing-events")).isFalse();
  assertThat(call.allows("POST","/internal/ai/clone-training-jobs/VOICE-1/events")).isFalse();
  var training=new AiInternalApiProperties("key",null,null,Set.of("training_status"));
  assertThat(training.allows("POST","/internal/ai/clone-training-jobs/FACE-1/events")).isTrue();
  assertThat(training.allows("POST","/internal/ai/clone-training-jobs/RAG-12-3/events")).isTrue();
  assertThat(training.allows("GET","/internal/ai/clone-training-jobs/VOICE-1/events")).isFalse();
  assertThat(training.allows("POST","/internal/ai/clone-training-jobs/1/events")).isFalse();
 }
}
