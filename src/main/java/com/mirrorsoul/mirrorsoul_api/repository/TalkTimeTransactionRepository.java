package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.TalkTimeTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TalkTimeTransactionRepository extends JpaRepository<TalkTimeTransaction, Long> {
}
