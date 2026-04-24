package com.syntia.ai.repository;

import com.syntia.ai.model.IdxConvocatoriaFinalidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IdxConvocatoriaFinalidadRepository extends JpaRepository<IdxConvocatoriaFinalidad, Long> {
    void deleteByNumeroConvocatoria(String numeroConvocatoria);
    boolean existsByNumeroConvocatoriaAndFinalidadId(String numeroConvocatoria, Integer finalidadId);

    @Query("SELECT f.descripcion FROM IdxConvocatoriaFinalidad idx JOIN CatFinalidad f ON idx.finalidadId = f.id " +
           "WHERE idx.numeroConvocatoria = :num ORDER BY f.descripcion")
    List<String> findDescripcionesByNumeroConvocatoria(@Param("num") String num);
}
