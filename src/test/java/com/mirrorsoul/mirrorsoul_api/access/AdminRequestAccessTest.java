package com.mirrorsoul.mirrorsoul_api.access;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
class AdminRequestAccessTest {
 static class Handler {public void read() {}}
 @AfterEach void clean() {AdminAuditContext.clear();SecurityContextHolder.clearContext();}
 @Test void requestChecksFreshPermissionAndRejectsForeignOrigin() throws Exception {
  var permissions=mock(AdminPermissionService.class);var audit=mock(AdminAuditWriter.class);var interceptor=new AdminAccessInterceptor(permissions,audit);ReflectionTestUtils.setField(interceptor,"origins","https://admin.example");
  var user=User.builder().email("staff@example.com").passwordHash("hash").status(UserStatus.ACTIVE).role(UserRole.ADMIN).build();ReflectionTestUtils.setField(user,"id",1L);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CustomUserDetails(user),null,java.util.List.of()));
  when(permissions.route("GET","/admin/members")).thenReturn(AdminPermission.member_read);
  var request=new MockHttpServletRequest("GET","/admin/members");request.addHeader("Origin","https://admin.example");var response=new MockHttpServletResponse();var handler=new HandlerMethod(new Handler(),Handler.class.getMethod("read"));
  assertThat(interceptor.preHandle(request,response,handler)).isTrue();verify(permissions).require(1L,AdminPermission.member_read);interceptor.afterCompletion(request,response,handler,null);
  assertThat(UUID.fromString(response.getHeader("X-Correlation-Id"))).isNotNull();
  doThrow(new GeneralException(GeneralErrorCode.FORBIDDEN)).when(permissions).require(1L,AdminPermission.member_read);
  assertThatThrownBy(()->interceptor.preHandle(request,new MockHttpServletResponse(),handler)).isInstanceOf(GeneralException.class);assertThat(AdminAuditContext.get()).isNull();
  var foreign=new MockHttpServletRequest("GET","/admin/members");foreign.addHeader("Origin","https://evil.example");assertThatThrownBy(()->interceptor.preHandle(foreign,new MockHttpServletResponse(),handler)).isInstanceOf(GeneralException.class);
 }
 @Test void securityRejectionsBeforeControllerAreAudited() throws Exception {
  var audit=mock(AdminAuditWriter.class);var filter=new AdminSecurityAuditFilter(audit);var request=new MockHttpServletRequest("GET","/admin/me");var response=new MockHttpServletResponse();
  filter.doFilter(request,response,(req,res)->((jakarta.servlet.http.HttpServletResponse)res).setStatus(403));
  verify(audit).isolated(eq(null),eq("ADMIN_HTTP_REQUEST"),eq("admin"),eq("DENIED"),eq("HTTP_403"),anyString());
 }
}
