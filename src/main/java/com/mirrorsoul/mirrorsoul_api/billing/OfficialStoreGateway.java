package com.mirrorsoul.mirrorsoul_api.billing;

import com.apple.itunes.storekit.client.*;
import com.apple.itunes.storekit.model.*;
import com.apple.itunes.storekit.verification.*;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;

@Component @RequiredArgsConstructor
public class OfficialStoreGateway implements StoreGateway {
    private final BillingProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile AppleClients apple;
    private volatile GoogleCredentials credentials;
    private volatile GoogleIdTokenVerifier pushVerifier;
    private record AppleClients(AppStoreServerAPIClient api, SignedDataVerifier verifier) {}

    private synchronized AppleClients apple() {
        if (apple != null) return apple;
        var p = properties.getApple();
        if (p.getBundleId().isBlank() || p.getIssuerId().isBlank() || p.getKeyId().isBlank()
                || p.getPrivateKeyPath().isBlank() || p.getRootCertificatePaths().isEmpty()) throw BillingSupport.unavailable();
        Set<InputStream> roots = new HashSet<>();
        try {
            Environment env = Environment.valueOf(p.getEnvironment());
            if (env != Environment.PRODUCTION && env != Environment.SANDBOX) throw BillingSupport.unavailable();
            if (env == Environment.PRODUCTION && p.getAppAppleId() == null) throw BillingSupport.unavailable();
            for (String path : p.getRootCertificatePaths()) roots.add(Files.newInputStream(Path.of(path)));
            apple = new AppleClients(new AppStoreServerAPIClient(Files.readString(Path.of(p.getPrivateKeyPath())),
                    p.getKeyId(), p.getIssuerId(), p.getBundleId(), env),
                    new SignedDataVerifier(roots, p.getBundleId(), p.getAppAppleId(), env, true));
            return apple;
        } catch (IOException | IllegalArgumentException e) { throw BillingSupport.unavailable(); }
        finally { for (var root : roots) try { root.close(); } catch (IOException ignored) {} }
    }

    @Override public Purchase verify(Store store, String reference) {
        if (reference == null || reference.isBlank() || reference.length() > 4096) throw BillingSupport.invalid();
        return store == Store.APPLE ? verifyApple(reference) : verifyGoogle(reference);
    }
    private Purchase verifyApple(String reference) {
        if (!reference.matches("[0-9]{1,100}")) throw BillingSupport.invalid();
        var clients = apple();
        try {
            var t = clients.verifier().verifyAndDecodeTransaction(clients.api().getTransactionInfo(reference).getSignedTransactionInfo());
            if (!reference.equals(t.getTransactionId()) || t.getType() != Type.CONSUMABLE
                    || t.getAppAccountToken() == null || t.getQuantity() == null || t.getQuantity() < 1
                    || t.getQuantity() > 100) throw BillingSupport.invalid();
            int percentage = t.getRevocationDate() == null ? 0 :
                    t.getRevocationPercentage() == null ? 100 : t.getRevocationPercentage();
            if (percentage < 0 || percentage > 100) throw BillingSupport.invalid();
            boolean revoked = percentage == 100;
            return new Purchase(Store.APPLE, reference, t.getTransactionId(), t.getProductId(),
                    t.getAppAccountToken(), t.getQuantity(), revoked ? 0 : t.getQuantity(), true, revoked, true,
                    percentage, t.getRevocationDate() == null ? 0 : t.getRevocationDate());
        } catch (VerificationException e) { throw BillingSupport.invalid(); }
        catch (APIException e) {
            if (e.getHttpStatusCode() == 400 || e.getHttpStatusCode() == 404) throw BillingSupport.invalid();
            throw BillingSupport.unavailable();
        } catch (IOException e) { throw BillingSupport.unavailable(); }
    }
    private synchronized GoogleCredentials credentials() {
        if (credentials != null) return credentials;
        if (properties.getGoogle().getPackageName().isBlank()) throw BillingSupport.unavailable();
        try {
            if (properties.getGoogle().getCredentialsPath().isBlank()) credentials = GoogleCredentials.getApplicationDefault();
            else try (var in = Files.newInputStream(Path.of(properties.getGoogle().getCredentialsPath()))) {
                credentials = GoogleCredentials.fromStream(in);
            }
            credentials = credentials.createScoped("https://www.googleapis.com/auth/androidpublisher");
            return credentials;
        } catch (IOException e) { throw BillingSupport.unavailable(); }
    }
    private String googleUrl(String path) {
        String pkg = properties.getGoogle().getPackageName();
        if (!pkg.matches("[A-Za-z0-9_.]+")) throw BillingSupport.unavailable();
        return "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" + pkg + path;
    }
    private String segment(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
    private synchronized String accessToken() {
        try { var c = credentials(); c.refreshIfExpired(); return c.getAccessToken().getTokenValue(); }
        catch (IOException e) { throw BillingSupport.unavailable(); }
    }
    private HttpResponse<String> google(String method, String path) {
        try {
            var request = HttpRequest.newBuilder(URI.create(googleUrl(path))).timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + accessToken());
            return http.send(request.method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) { throw BillingSupport.unavailable(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw BillingSupport.unavailable(); }
    }
    private JsonNode googlePurchase(String reference) {
        var response = google("GET", "/purchases/productsv2/tokens/" + segment(reference));
        if (response.statusCode() == 400 || response.statusCode() == 404) throw BillingSupport.invalid();
        if (response.statusCode() != 200) throw BillingSupport.unavailable();
        try { return mapper.readTree(response.body()); }
        catch (RuntimeException e) { throw BillingSupport.unavailable(); }
    }
    private Purchase verifyGoogle(String reference) {
        return decodeGoogle(reference, googlePurchase(reference));
    }
    // Package-visible for contract tests; only authenticated API responses reach this method.
    Purchase decodeGoogle(String reference, JsonNode node) {
        if (node.has("testPurchaseContext") && !properties.getGoogle().isAllowTestPurchases()) throw BillingSupport.invalid();
        var items = node.path("productLineItem");
        if (!items.isArray() || items.size() != 1) throw BillingSupport.invalid();
        var line = items.get(0); var offer = line.path("productOfferDetails");
        if (offer.has("rentOfferDetails")) throw BillingSupport.invalid();
        int quantity = offer.path("quantity").asInt(1);
        String state = node.path("purchaseStateContext").path("purchaseState").asText();
        if (!Set.of("PURCHASED", "PENDING", "CANCELLED").contains(state)) throw BillingSupport.invalid();
        int remaining = offer.path("refundableQuantity").asInt(quantity);
        String owner = node.path("obfuscatedExternalAccountId").asText();
        // The mobile app sets obfuscatedAccountId to the member UUID.
        UUID uuid;
        try { uuid = UUID.fromString(owner); } catch (IllegalArgumentException e) { throw BillingSupport.invalid(); }
        String order = node.path("orderId").asText();
        if (quantity < 1 || quantity > 100 || remaining < 0 || remaining > quantity
                || "PURCHASED".equals(state) && order.isBlank()) throw BillingSupport.invalid();
        boolean revoked = "CANCELLED".equals(state) || remaining == 0;
        return new Purchase(Store.GOOGLE, reference, order.isBlank() ? null : order,
                line.path("productId").asText(), uuid, quantity, revoked ? 0 : remaining,
                "PURCHASED".equals(state), revoked,
                "CONSUMPTION_STATE_CONSUMED".equals(offer.path("consumptionState").asText()));
    }
    @Override public Notification appleNotification(String signedPayload) {
        var verifier = apple().verifier();
        try {
            var n = verifier.verifyAndDecodeNotification(signedPayload);
            if (n.getNotificationUUID() == null || n.getSignedDate() == null) throw BillingSupport.invalid();
            var type = n.getNotificationType();
            if (type != NotificationTypeV2.REFUND && type != NotificationTypeV2.REVOKE
                    && type != NotificationTypeV2.REFUND_REVERSED && type != NotificationTypeV2.ONE_TIME_CHARGE)
                return new Notification(Store.APPLE, n.getNotificationUUID(), null, null, Action.IGNORE, n.getSignedDate());
            if (n.getData() == null) throw BillingSupport.invalid();
            var t = verifier.verifyAndDecodeTransaction(n.getData().getSignedTransactionInfo());
            if (t.getType() != Type.CONSUMABLE || t.getTransactionId() == null) throw BillingSupport.invalid();
            if (t.getRevocationPercentage() != null && (t.getRevocationPercentage() < 0 || t.getRevocationPercentage() > 100))
                throw BillingSupport.invalid();
            Action action = type == NotificationTypeV2.REFUND_REVERSED ? Action.RESTORE :
                    type == NotificationTypeV2.ONE_TIME_CHARGE ? Action.SYNC : Action.REVOKE;
            if (action == Action.REVOKE && t.getRevocationPercentage() != null && t.getRevocationPercentage() < 100)
                action = Action.REVOKE_PARTIAL;
            return new Notification(Store.APPLE, n.getNotificationUUID(), t.getTransactionId(),
                    t.getTransactionId(), action, n.getSignedDate());
        } catch (VerificationException e) { throw BillingSupport.invalid(); }
    }
    private synchronized GoogleIdTokenVerifier pushVerifier() {
        var p = properties.getGoogle();
        if (p.getPushAudience().isBlank() || p.getPushServiceAccount().isBlank()) throw BillingSupport.unavailable();
        if (pushVerifier == null) pushVerifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(p.getPushAudience())).build();
        return pushVerifier;
    }
    @Override public Notification googleNotification(String authorization, String data, String messageId) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException(
                com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.FORBIDDEN);
        try {
            var token = pushVerifier().verify(authorization.substring(7));
            if (token == null || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())
                    || !properties.getGoogle().getPushServiceAccount().equals(token.getPayload().getEmail()))
                throw new com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException(
                        com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.FORBIDDEN);
        } catch (java.security.GeneralSecurityException | IllegalArgumentException e) { throw BillingSupport.invalid(); }
        catch (IOException e) { throw BillingSupport.unavailable(); }
        JsonNode decoded;
        try {
            decoded = mapper.readTree(Base64.getDecoder().decode(data));
        } catch (RuntimeException e) { throw BillingSupport.invalid(); }
        return decodeGoogleNotification(decoded, messageId);
    }
    Notification decodeGoogleNotification(JsonNode node, String messageId) {
        if (messageId == null || messageId.isBlank() || messageId.length() > 200
                || !properties.getGoogle().getPackageName().equals(node.path("packageName").asText())) throw BillingSupport.invalid();
        long at;
        try { at = Long.parseLong(node.path("eventTimeMillis").asText()); }
        catch (NumberFormatException e) { throw BillingSupport.invalid(); }
        if (at <= 0) throw BillingSupport.invalid();
        var voided = node.path("voidedPurchaseNotification");
        if (!voided.isMissingNode() && voided.path("productType").asInt() == 2) {
            int type = voided.path("refundType").asInt(1);
            if (type != 1 && type != 2) throw BillingSupport.invalid();
            return notification(messageId, voided.path("purchaseToken").asText(), voided.path("orderId").asText(),
                    type == 1 ? Action.REVOKE : Action.REVOKE_PARTIAL, at);
        }
        var one = node.path("oneTimeProductNotification");
        if (!one.isMissingNode()) {
            int type = one.path("notificationType").asInt();
            if (type != 1 && type != 2) throw BillingSupport.invalid();
            return notification(messageId, one.path("purchaseToken").asText(), null, type == 1 ? Action.SYNC : Action.REVOKE, at);
        }
        return new Notification(Store.GOOGLE, messageId, null, null, Action.IGNORE, at);
    }
    private Notification notification(String id, String reference, String order, Action action, long at) {
        if (reference.isBlank() || reference.length() > 4096) throw BillingSupport.invalid();
        return new Notification(Store.GOOGLE, id, reference, order, action, at);
    }
    @Override public void consume(String reference, String productId) {
        // Recheck before retrying an uncertain consume response.
        var p = verifyGoogle(reference);
        if (p.consumed() || p.revoked()) return;
        if (!p.purchased() || !p.productId().equals(productId)) throw BillingSupport.conflict();
        var response = google("POST", "/purchases/products/" + segment(productId) + "/tokens/" + segment(reference) + ":consume");
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw BillingSupport.unavailable();
    }
}
