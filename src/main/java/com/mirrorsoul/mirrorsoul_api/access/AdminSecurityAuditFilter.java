package com.mirrorsoul.mirrorsoul_api.access;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
public class AdminSecurityAuditFilter extends OncePerRequestFilter {
 public static final String RECORDED="admin.audit.recorded";
 private final AdminAuditWriter audit;
 public AdminSecurityAuditFilter(AdminAuditWriter audit) {this.audit=audit;}
 @Override protected boolean shouldNotFilter(HttpServletRequest request) {return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/admin/");}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
  String correlation=request.getHeader("X-Correlation-Id");try {correlation=UUID.fromString(correlation).toString();}catch(Exception ignored) {correlation=UUID.randomUUID().toString();}response.setHeader("X-Correlation-Id",correlation);
  try {chain.doFilter(request,response);}
  finally {
   if(response.getStatus()>=400&&!Boolean.TRUE.equals(request.getAttribute(RECORDED))) {
    var authentication=SecurityContextHolder.getContext().getAuthentication();Long id=authentication!=null&&authentication.getPrincipal() instanceof CustomUserDetails u?u.getId():null;
    if(id==null&&request.getAttribute(com.mirrorsoul.mirrorsoul_api.common.jwt.JwtAuthenticationFilter.VERIFIED_USER_ID) instanceof Long verified) id=verified;
    audit.isolated(id,"ADMIN_HTTP_REQUEST","admin","DENIED","HTTP_"+response.getStatus(),response.getHeader("X-Correlation-Id"));
   }
  }
 }
}
