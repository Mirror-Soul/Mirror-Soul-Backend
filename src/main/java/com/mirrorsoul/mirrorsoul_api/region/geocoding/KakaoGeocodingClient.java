package com.mirrorsoul.mirrorsoul_api.region.geocoding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "region.geocoding", name = "enabled", havingValue = "true")
public class KakaoGeocodingClient {

    private final RestClient restClient;

    public KakaoGeocodingClient(
            @Value("${region.geocoding.kakao-rest-api-key}") String apiKey
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "KAKAO_REST_API_KEY is required when region geocoding is enabled"
            );
        }

        this.restClient = RestClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey)
                .build();
    }

    public KakaoAddressResponse search(String query) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/search/address.json")
                        .queryParam("query", query)
                        .build())
                .retrieve()
                .body(KakaoAddressResponse.class);
    }
}
