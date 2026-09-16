package com.mirrorsoul.mirrorsoul_api.event;

import com.mirrorsoul.mirrorsoul_api.recommendation.EmbeddingType;
import java.util.UUID;

public record UserEmbeddingRequestedEvent(UUID userUuid, EmbeddingType type) {
}
