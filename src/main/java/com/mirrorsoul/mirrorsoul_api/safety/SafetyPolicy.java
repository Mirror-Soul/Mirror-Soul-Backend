package com.mirrorsoul.mirrorsoul_api.safety;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public record SafetyPolicy(int hourlyLimit,int dailyLimit,int retentionDays,int defaultRestrictionDays,int appealDays) {
    public SafetyPolicy(@Value("${safety.reports.hourly-limit:10}") int hourlyLimit,
            @Value("${safety.reports.daily-limit:50}") int dailyLimit,
            @Value("${safety.evidence.retention-days:90}") int retentionDays,
            @Value("${safety.restriction.default-days:7}") int defaultRestrictionDays,
            @Value("${safety.appeal.days:14}") int appealDays) {
        if(hourlyLimit<1 || dailyLimit<hourlyLimit || retentionDays<appealDays || appealDays<1
                || defaultRestrictionDays<1 || defaultRestrictionDays>30) throw new IllegalArgumentException("Invalid safety policy.");
        this.hourlyLimit=hourlyLimit;this.dailyLimit=dailyLimit;this.retentionDays=retentionDays;
        this.defaultRestrictionDays=defaultRestrictionDays;this.appealDays=appealDays;
    }
}
