package com.mirrorsoul.mirrorsoul_api.jobreview;

/** At least one recipient was explicitly rejected; successful recipients may already be notified. */
public class JobNotificationDeliveryException extends RuntimeException {
    public JobNotificationDeliveryException() { super("Job notification recipient delivery failed"); }
}
