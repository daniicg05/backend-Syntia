package com.syntia.ai.repository;

import com.syntia.ai.model.BdnsRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BdnsRegionesRepository extends JpaRepository<BdnsRegion, Integer> {

    List<BdnsRegion> findByNombreContainingIgnoreCase(String nombre);
}

