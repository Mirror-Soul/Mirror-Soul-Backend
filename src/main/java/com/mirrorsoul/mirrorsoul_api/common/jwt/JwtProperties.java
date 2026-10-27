package com.mirrorsoul.mirrorsoul_api.common.jwt;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Instant;
@ConfigurationProperties(prefix="jwt")
public record JwtProperties(String secret,long accessTokenExpirationSeconds,long refreshTokenExpirationSeconds,String previousSecret,Instant previousSecretExpiresAt) {
 @org.springframework.boot.context.properties.bind.ConstructorBinding
 public JwtProperties {}
 public JwtProperties(String secret,long access,long refresh) {this(secret,access,refresh,null,null);}
 @Override public String toString() {return "JwtProperties[REDACTED]";}
}
