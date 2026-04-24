package com.syntia.ai.repository;

import com.syntia.ai.model.IdxConvocatoriaObjetivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IdxConvocatoriaObjetivoRepository extends JpaRepository<IdxConvocatoriaObjetivo, Long> {
    void deleteByNumeroConvocatoria(String numeroConvocatoria);
    boolean existsByNumeroConvocatoriaAndObjetivoId(String numeroConvocatoria, Integer objetivoId);

    @Query("SELECT o.descripcion FROM IdxConvocatoriaObjetivo idx JOIN CatObjetivo o ON idx.objetivoId = o.id " +
           "WHERE idx.numeroConvocatoria = :num ORDER BY o.descripcion")
    List<String> findDescripcionesByNumeroConvocatoria(@Param("num") String num);
}
