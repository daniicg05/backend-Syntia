package com.syntia.ai.repository;

import com.syntia.ai.model.IdxConvocatoriaInstrumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IdxConvocatoriaInstrumentoRepository extends JpaRepository<IdxConvocatoriaInstrumento, Long> {
    void deleteByNumeroConvocatoria(String numeroConvocatoria);
    boolean existsByNumeroConvocatoriaAndInstrumentoId(String numeroConvocatoria, Integer instrumentoId);

    @Query("SELECT i.descripcion FROM IdxConvocatoriaInstrumento idx JOIN CatInstrumento i ON idx.instrumentoId = i.id " +
           "WHERE idx.numeroConvocatoria = :num ORDER BY i.descripcion")
    List<String> findDescripcionesByNumeroConvocatoria(@Param("num") String num);
}
