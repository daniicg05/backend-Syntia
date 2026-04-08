package com.syntia.ai.repository;

import com.syntia.ai.model.SyncState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SyncStateRepository extends JpaRepository<SyncState, Long> {

    Optional<SyncState> findByEje(String eje);
}