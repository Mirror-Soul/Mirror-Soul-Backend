package com.mirrorsoul.mirrorsoul_api.region;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.repository.RegionRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NearbyRegionFinder {

    private final RegionRepository regionRepository;

    /**
     * 앵커를 포함해 최대 {@code count}개의 지역 ID를 가까운 순서로 반환한다.
     */
    public List<Long> findNearestRegionIds(Region anchor, int count) {
        if (!hasCoordinates(anchor)) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "좌표가 등록되지 않은 지역은 기준 지역으로 설정할 수 없습니다."
            );
        }

        List<Long> nearestIds = regionRepository
                .findAllByLatitudeIsNotNullAndLongitudeIsNotNullOrderByIdAsc().stream()
                .filter(region -> !region.getId().equals(anchor.getId()))
                .sorted(Comparator
                        .comparingDouble((Region region) -> GeoDistanceUtils.distanceKm(
                                anchor.getLatitude(),
                                anchor.getLongitude(),
                                region.getLatitude(),
                                region.getLongitude()
                        ))
                        .thenComparing(Region::getId))
                .limit(Math.max(0, count - 1L))
                .map(Region::getId)
                .toList();

        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(anchor.getId()),
                nearestIds.stream()
        ).toList();
    }

    private boolean hasCoordinates(Region region) {
        return region != null && region.getLatitude() != null && region.getLongitude() != null;
    }

}
