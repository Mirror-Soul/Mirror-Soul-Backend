package com.mirrorsoul.mirrorsoul_api.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Instant;
import java.util.Set;
import com.mirrorsoul.mirrorsoul_api.access.RotatingSecret;
@ConfigurationProperties(prefix="internal.ai")
public record AiInternalApiProperties(String apiKey,String previousApiKey,Instant previousKeyExpiresAt,Set<String> scopes) {
 @org.springframework.boot.context.properties.bind.ConstructorBinding
 public AiInternalApiProperties {}
 public AiInternalApiProperties(String apiKey) {this(apiKey,null,null,null);}
 public boolean isConfigured() {return apiKey!=null&&!apiKey.isBlank();}
 public boolean matches(String actual) {return RotatingSecret.matches(apiKey,previousApiKey,previousKeyExpiresAt,actual);}
 public boolean allows(String method,String path) {
  String scope=path.startsWith("/internal/ai/safety/")?"safety":path.startsWith("/internal/ai/member-deletions")?"member_deletion":path.matches("/internal/ai/calls/[0-9]+/context")&&method.equals("GET")?"call_context":path.matches("/internal/ai/calls/[0-9]+/talk-logs")&&method.equals("POST")?"talk_log":null;
  if(path.matches("/internal/ai/calls/[0-9]+/billing-events")&&method.equals("POST")) scope="call_billing";
  if(path.matches("/internal/ai/calls/[0-9]+/diagnostics")&&method.equals("POST")) scope="call_diagnostics";
  if(path.matches("/internal/ai/clone-training-jobs/(VOICE|FACE|PROFILE)-[0-9]+/events|/internal/ai/clone-training-jobs/RAG-[0-9]+-[0-9]+/events")&&method.equals("POST")) scope="training_status";
  return scope!=null&&(scopes==null||scopes.contains(scope));
 }
 public boolean allowsSignaling() {return scopes==null||scopes.contains("signaling");}
 @Override public String toString() {return "AiInternalApiProperties[REDACTED]";}
}
