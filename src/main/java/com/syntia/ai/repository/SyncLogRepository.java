package com.syntia.ai.repository;

import com.syntia.ai.model.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    List<SyncLog> findByEjecucionIdOrderByTsAsc(String ejecucionId);

    List<SyncLog> findByEjeOrderByTsDesc(String eje);

    List<SyncLog> findAllByOrderByTsDesc();
}