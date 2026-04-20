package com.syntia.ai.repository;

import com.syntia.ai.model.BdnsInstrumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BdnsInstrumentosRepository extends JpaRepository<BdnsInstrumento, Integer> {

    List<BdnsInstrumento> findByNombreContainingIgnoreCase(String nombre);
}

