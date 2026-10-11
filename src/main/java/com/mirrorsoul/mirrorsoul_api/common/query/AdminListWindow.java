package com.mirrorsoul.mirrorsoul_api.common.query;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;

/** Current rows selected by their creation time, with an inclusive lower and exclusive upper bound. */
public record AdminListWindow(OffsetDateTime from, OffsetDateTime to) {
    public AdminListWindow {
        if (from != null && to != null && !from.isBefore(to))
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
    }
    public static AdminListWindow all() { return new AdminListWindow(null, null); }
    public static LocalDateTime local(OffsetDateTime at) {
        return at == null ? null : LocalDateTime.ofInstant(at.toInstant(), ZoneId.systemDefault());
    }
    public static OffsetDateTime now() { return OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS); }
    public <T> Specification<T> specification() {
        return (root, query, cb) -> {
            var conditions = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (from != null) conditions.add(cb.greaterThanOrEqualTo(root.get("createdAt"), local(from)));
            if (to != null) conditions.add(cb.lessThan(root.get("createdAt"), local(to)));
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
