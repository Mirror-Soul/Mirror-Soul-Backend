package com.mirrorsoul.mirrorsoul_api.region.geocoding;

import com.mirrorsoul.mirrorsoul_api.domain.Region;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "region.geocoding", name = "enabled", havingValue = "true")
public class RegionGeocodingBatch implements ApplicationRunner {

    private final RegionGeocodingService regionGeocodingService;

    @Value("${region.geocoding.delay-ms:100}")
    private long delayMs;

    @Override
    public void run(ApplicationArguments args) {
        List<Region> targets = regionGeocodingService.findTargets();
        int successCount = 0;
        int failureCount = 0;

        log.info("Region geocoding started. targets={}", targets.size());

        for (int index = 0; index < targets.size(); index++) {
            Region region = targets.get(index);

            try {
                if (regionGeocodingService.geocode(region)) {
                    successCount++;
                } else {
                    failureCount++;
                    log.warn("No matching coordinate. regionId={}, lawdCd={}",
                            region.getId(), region.getLawdCd());
                }
            } catch (Exception exception) {
                if (isFatalClientError(exception)) {
                    throw new IllegalStateException(
                            "Region geocoding stopped because the Kakao API rejected the request",
                            exception
                    );
                }

                failureCount++;
                log.error("Region geocoding failed. regionId={}, lawdCd={}",
                        region.getId(), region.getLawdCd(), exception);
            }

            if (index < targets.size() - 1) {
                pauseBetweenRequests();
            }
        }

        log.info("Region geocoding finished. targets={}, success={}, failure={}",
                targets.size(), successCount, failureCount);
    }

    private boolean isFatalClientError(Exception exception) {
        if (!(exception instanceof HttpClientErrorException clientError)) {
            return false;
        }

        return clientError.getStatusCode().equals(HttpStatus.UNAUTHORIZED)
                || clientError.getStatusCode().equals(HttpStatus.FORBIDDEN)
                || clientError.getStatusCode().equals(HttpStatus.TOO_MANY_REQUESTS);
    }

    private void pauseBetweenRequests() {
        if (delayMs <= 0) {
            return;
        }

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Region geocoding was interrupted", exception);
        }
    }
}
