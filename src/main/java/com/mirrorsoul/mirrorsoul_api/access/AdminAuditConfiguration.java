package com.mirrorsoul.mirrorsoul_api.access;
import java.lang.reflect.Method;
import java.util.Set;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.aop.support.*;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Configuration @EnableTransactionManagement(order=Ordered.LOWEST_PRECEDENCE-100)
public class AdminAuditConfiguration {
 @Bean @Role(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE) public DefaultPointcutAdvisor adminBusinessAuditAdvisor(org.springframework.beans.factory.ObjectProvider<AdminAuditWriter> auditWriter) {
  var pointcut=new StaticMethodMatcherPointcut() {
   @Override public boolean matches(Method method,Class<?> type) {return Set.of("AdminMemberService","JobVerificationReviewService","SafetyReportService","SafetyDecisionService","SafetyEvidenceService","SupportTicketService","SupportAttachmentService").contains(type.getSimpleName())&&java.lang.reflect.Modifier.isPublic(method.getModifiers());}
  };
  MethodInterceptor advice=invocation -> {
   var c=AdminAuditContext.get();if(c==null) return invocation.proceed();
   boolean root=c.depth++==0;
   try {
    Object result=invocation.proceed();
    if(root) {
     var writer=auditWriter.getObject();
     String reason="OK";
     for(Object argument:invocation.getArguments()) {
      if(argument instanceof com.mirrorsoul.mirrorsoul_api.dto.member.MemberActionRequest request) reason=request.reasonCode().name();
      else if(argument instanceof com.mirrorsoul.mirrorsoul_api.safety.SafetyDtos.Decide request) reason=request.reasonCode().name();
     }
     if(TransactionSynchronizationManager.isActualTransactionActive()&&!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) writer.enlisted(c.staffId,c.action,c.target,"SUCCESS",reason,c.correlationId);
     else writer.isolated(c.staffId,c.action,c.target,"SUCCESS",reason,c.correlationId);
    }
    return result;
   } finally {c.depth--;}
  };
  var advisor=new DefaultPointcutAdvisor(pointcut,advice);advisor.setOrder(Ordered.LOWEST_PRECEDENCE-50);return advisor;
 }
}
