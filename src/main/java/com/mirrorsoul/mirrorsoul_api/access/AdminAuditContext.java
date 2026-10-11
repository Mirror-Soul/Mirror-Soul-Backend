package com.mirrorsoul.mirrorsoul_api.access;
public final class AdminAuditContext {
 private static final ThreadLocal<Context> CURRENT=new ThreadLocal<>();
 private AdminAuditContext() {}
 public static final class Context {
  public final Long staffId;public final String action,target,correlationId;
  public int depth;public String recordedResult;
  public Context(Long staffId,String action,String target,String correlationId) {this.staffId=staffId;this.action=action;this.target=target;this.correlationId=correlationId;}
 }
 public static Context get() {return CURRENT.get();}
 public static void set(Context value) {CURRENT.set(value);}
 public static void clear() {CURRENT.remove();}
}
