package com.mirrorsoul.mirrorsoul_api.safety;

public final class SafetyTypes {
    private SafetyTypes() {}
    public enum ReportType { HARASSMENT, THREATS, SEXUAL_CONTENT, FRAUD, SPAM, PRIVACY, OTHER }
    public enum Priority { NORMAL, HIGH, URGENT }
    public enum ReportState { RECEIVED, REVIEWING, DECIDED, APPEALED }
    public enum Action { REJECT, WARN, RESTRICT, RELEASE, APPROVE_APPEAL, REJECT_APPEAL }
    public enum Reason { VIOLATION_CONFIRMED, INSUFFICIENT_EVIDENCE, NO_VIOLATION, RESTRICTION_COMPLETED, APPEAL_ACCEPTED, APPEAL_REJECTED }
    public enum AppealState { PENDING, ACCEPTED, REJECTED }
    public enum DiagnosticCode { CONNECTED, CONNECTION_FAILED, DISCONNECTED, AI_ERROR, SAFETY_STOPPED }
}
