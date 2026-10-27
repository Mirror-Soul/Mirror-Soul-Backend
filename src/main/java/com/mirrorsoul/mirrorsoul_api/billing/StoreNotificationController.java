package com.mirrorsoul.mirrorsoul_api.billing;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingDtos.*;

@RestController @RequestMapping("/payments/notifications") @RequiredArgsConstructor
public class StoreNotificationController {
    private final StoreGateway store;
    private final BillingService billing;
    @PostMapping("/apple")
    public ResponseEntity<Void> apple(@Valid @RequestBody AppleNotification request) {
        billing.notification(store.appleNotification(request.signedPayload()));
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/google")
    public ResponseEntity<Void> google(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @Valid @RequestBody GooglePush request) {
        billing.notification(store.googleNotification(authorization, request.message().data(), request.message().messageId()));
        return ResponseEntity.noContent().build();
    }
}
