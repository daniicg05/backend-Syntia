package com.syntia.ai.repository;

import com.syntia.ai.model.IdxConvocatoriaOrgano;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IdxConvocatoriaOrganoRepository extends JpaRepository<IdxConvocatoriaOrgano, Long> {
    void deleteByNumeroConvocatoria(String numeroConvocatoria);
    boolean existsByNumeroConvocatoriaAndOrganoId(String numeroConvocatoria, Integer organoId);

    @Query("SELECT o.descripcion FROM IdxConvocatoriaOrgano idx JOIN CatOrgano o ON idx.organoId = o.id " +
           "WHERE idx.numeroConvocatoria = :num ORDER BY o.descripcion")
    List<String> findDescripcionesByNumeroConvocatoria(@Param("num") String num);
}
