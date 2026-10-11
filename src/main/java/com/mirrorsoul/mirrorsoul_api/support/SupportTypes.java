package com.mirrorsoul.mirrorsoul_api.support;
public final class SupportTypes {
 private SupportTypes() {}
 public enum Channel { APP, EMAIL }
 public enum Category { ACCOUNT, BILLING, TWIN, CALL, SAFETY, OTHER }
 public enum Status { RECEIVED, IN_PROGRESS, WAITING_CUSTOMER, RESOLVED, CLOSED }
 public enum EntryType { CUSTOMER_MESSAGE, REPLY, NOTE, STATUS_CHANGE, ASSIGNMENT }
 public enum Delivery { NONE, PENDING, SENDING, SENT, FAILED, UNKNOWN, CANCELLED }
 public enum JobType { VOICE, FACE, VALUE_BALANCE, CLONE_PROFILE }
}
