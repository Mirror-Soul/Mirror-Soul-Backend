package com.mirrorsoul.mirrorsoul_api.region.geocoding;

import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "region.geocoding", name = "enabled", havingValue = "true")
public class RegionGeocodingService {

    private static final String COORDINATE_SOURCE = "KAKAO_ADDRESS";

    private final RegionRepository regionRepository;
    private final KakaoGeocodingClient kakaoGeocodingClient;

    @Transactional(readOnly = true)
    public List<Region> findTargets() {
        return regionRepository.findAllByLatitudeIsNullOrLongitudeIsNullOrderByIdAsc();
    }

    @Transactional
    public boolean geocode(Region region) {
        KakaoAddressResponse response = kakaoGeocodingClient.search(createQuery(region));
        Optional<KakaoAddressResponse.Document> matched = findUsableDocument(response);

        if (matched.isEmpty()) {
            return false;
        }

        KakaoAddressResponse.Document document = matched.get();
        region.updateGeocodingData(
                document.address().legalDongCode(),
                new BigDecimal(document.y()),
                new BigDecimal(document.x()),
                COORDINATE_SOURCE
        );
        regionRepository.save(region);
        return true;
    }

    private String createQuery(Region region) {
        return String.join(
                " ",
                region.getSidoName(),
                region.getSigunguName(),
                region.getEupmyeondongName()
        );
    }

    private Optional<KakaoAddressResponse.Document> findUsableDocument(KakaoAddressResponse response) {
        if (response == null || response.documents() == null) {
            return Optional.empty();
        }

        return response.documents().stream()
                .filter(document -> document.address() != null)
                .filter(document -> document.address().legalDongCode() != null)
                .filter(document -> !document.address().legalDongCode().isBlank())
                .filter(document -> document.x() != null && document.y() != null)
                .findFirst();
    }
}
