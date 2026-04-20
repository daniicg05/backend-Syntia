package com.syntia.ai.repository;

import com.syntia.ai.model.BdnsOrgano;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BdnsOrganosRepository extends JpaRepository<BdnsOrgano, Integer> {

    List<BdnsOrgano> findByTipoAdmon(String tipoAdmon);

    List<BdnsOrgano> findByNombreContainingIgnoreCase(String nombre);
}

