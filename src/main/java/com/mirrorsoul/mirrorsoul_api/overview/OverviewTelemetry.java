package com.mirrorsoul.mirrorsoul_api.overview;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class OverviewTelemetry {
    private final MeterRegistry meters;
    @Value("${admin.overview.slow-query-ms:1000}") private long slowMs;
    public void card(OverviewDtos.Key key, boolean success, long nanos) {
        meters.timer("admin.overview.card.duration","card",key.name(),"outcome",success?"READY":"UNAVAILABLE")
                .record(nanos,TimeUnit.NANOSECONDS);
        long ms=TimeUnit.NANOSECONDS.toMillis(nanos);
        if(!success) {
            meters.counter("admin.overview.card.failures","card",key.name()).increment();
            log.error("Admin overview card failed card={} failureCode=QUERY_FAILED durationMs={}",key,ms);
        }
        if(ms>=slowMs)log.warn("Admin overview slow card={} durationMs={}",key,ms);
    }
    public void request(OverviewDtos.State state,int count,long nanos) {
        meters.timer("admin.overview.request.duration","outcome",state.name()).record(nanos,TimeUnit.NANOSECONDS);
        if(state!=OverviewDtos.State.COMPLETE)
            log.warn("Admin overview incomplete state={} cardCount={}",state,count);
    }
}
