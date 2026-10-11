package com.mirrorsoul.mirrorsoul_api.access;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
public final class RotatingSecret {
 private RotatingSecret() {}
 public static boolean matches(String current,String previous,Instant previousExpiresAt,String actual) {
  if(actual==null||actual.isBlank()||current==null||current.isBlank()) return false;
  if(equal(current,actual)) return true;
  return previous!=null&&!previous.isBlank()&&previousExpiresAt!=null&&Instant.now().isBefore(previousExpiresAt)&&equal(previous,actual);
 }
 private static boolean equal(String expected,String actual) {return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8));}
 public static Instant expiry(String value) {if(value==null||value.isBlank()) return null;return Instant.parse(value);}
}
