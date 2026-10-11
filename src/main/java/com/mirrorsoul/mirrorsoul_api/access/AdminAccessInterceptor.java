package com.mirrorsoul.mirrorsoul_api.access;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import jakarta.servlet.http.*;
import java.util.*;
@Component @RequiredArgsConstructor
public class AdminAccessInterceptor implements HandlerInterceptor {
 private final AdminPermissionService permissions;
 private final AdminAuditWriter audit;
 @Value("${security.admin.allowed-origins:http://localhost:3000,http://localhost:5173}") private String origins;
 @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
  if(!(handler instanceof HandlerMethod method)) return true;
  var authentication=SecurityContextHolder.getContext().getAuthentication();Long id=authentication!=null&&authentication.getPrincipal() instanceof CustomUserDetails u?u.getId():null;
  String correlation=request.getHeader("X-Correlation-Id");try {correlation=UUID.fromString(correlation).toString();}catch(Exception ignored) {correlation=UUID.randomUUID().toString();}
  response.setHeader("X-Correlation-Id",correlation);
  String target=method.getBeanType().getSimpleName();
  Object variables=request.getAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
  if(variables instanceof Map<?,?> map) for(var entry:map.entrySet()) {String value=String.valueOf(entry.getValue());if(value.matches("[0-9]{1,20}")) target+=":"+value;}
  var c=new AdminAuditContext.Context(id,method.getBeanType().getSimpleName()+"."+method.getMethod().getName(),target.substring(0,Math.min(target.length(),120)),correlation);AdminAuditContext.set(c);
  try {
   String origin=request.getHeader("Origin");if(origin!=null&&Arrays.stream(origins.split(",")).map(String::trim).noneMatch(origin::equals)) AccessSupport.denied();
   if(id==null) AccessSupport.denied();permissions.active(id);var required=permissions.route(request.getMethod(),request.getRequestURI());if(required!=null) permissions.require(id,required);return true;
  } catch(RuntimeException e) {try {audit.isolated(id,c.action,c.target,"DENIED","PERMISSION_OR_ORIGIN_DENIED",correlation);}finally {request.setAttribute(AdminSecurityAuditFilter.RECORDED,true);AdminAuditContext.clear();}throw e;}
 }
 @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception error) {
  var c=AdminAuditContext.get();if(c==null) return;
  try {
   if(response.getStatus()>=400||error!=null) audit.isolated(c.staffId,c.action,c.target,"FAILED","HTTP_"+response.getStatus(),c.correlationId);
   else if(c.recordedResult==null) audit.isolated(c.staffId,c.action,c.target,"SUCCESS","OK",c.correlationId);
  } finally {request.setAttribute(AdminSecurityAuditFilter.RECORDED,true);AdminAuditContext.clear();}
 }
}
