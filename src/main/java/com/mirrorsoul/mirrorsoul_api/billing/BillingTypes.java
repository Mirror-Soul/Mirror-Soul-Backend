package com.mirrorsoul.mirrorsoul_api.billing;

public final class BillingTypes {
    private BillingTypes() {}
    public enum Store { APPLE, GOOGLE }
    public enum Verification { UNVERIFIED, VERIFIED }
    public enum Grant { NOT_GRANTED, GRANTED, REVOKED }
    public enum Refund { NONE, PARTIAL, REFUNDED, REVERSED, CANCELLED }
    public enum Consumption { NOT_REQUIRED, PENDING, COMPLETED }
    public enum Action { SYNC, REVOKE, REVOKE_PARTIAL, RESTORE, IGNORE, CONSUME }
    public enum JobState { PENDING, PROCESSING, COMPLETED, DEAD }
}
