package com.syntia.ai.repository;

import com.syntia.ai.model.IdxConvocatoriaActividad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IdxConvocatoriaActividadRepository extends JpaRepository<IdxConvocatoriaActividad, Long> {
    void deleteByNumeroConvocatoria(String numeroConvocatoria);
    boolean existsByNumeroConvocatoriaAndActividadId(String numeroConvocatoria, Integer actividadId);

    @Query("SELECT a.descripcion FROM IdxConvocatoriaActividad idx JOIN CatActividad a ON idx.actividadId = a.id " +
           "WHERE idx.numeroConvocatoria = :num ORDER BY a.descripcion")
    List<String> findDescripcionesByNumeroConvocatoria(@Param("num") String num);
}
