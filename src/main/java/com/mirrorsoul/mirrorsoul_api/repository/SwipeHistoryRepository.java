package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.SwipeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SwipeHistoryRepository extends JpaRepository<SwipeHistory, Long> {
}
