package com.syntia.ai.repository;

import com.syntia.ai.model.IdxConvocatoriaRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IdxConvocatoriaRegionRepository extends JpaRepository<IdxConvocatoriaRegion, Long> {
    void deleteByNumeroConvocatoria(String numeroConvocatoria);
    boolean existsByNumeroConvocatoriaAndRegionId(String numeroConvocatoria, Integer regionId);

    @Query("SELECT r.descripcion FROM IdxConvocatoriaRegion idx JOIN Region r ON idx.regionId = r.id " +
           "WHERE idx.numeroConvocatoria = :num ORDER BY r.descripcion")
    List<String> findDescripcionesByNumeroConvocatoria(@Param("num") String num);
}
