package com.mirrorsoul.mirrorsoul_api.region.geocoding;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KakaoAddressResponse(List<Document> documents) {

    public record Document(String x, String y, Address address) {
    }

    public record Address(@JsonProperty("b_code") String legalDongCode) {
    }
}
