package com.syntia.ai.repository;

import com.syntia.ai.model.IdxConvocatoriaReglamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IdxConvocatoriaReglamentoRepository extends JpaRepository<IdxConvocatoriaReglamento, Long> {
    void deleteByNumeroConvocatoria(String numeroConvocatoria);
    boolean existsByNumeroConvocatoriaAndReglamentoId(String numeroConvocatoria, Integer reglamentoId);

    @Query("SELECT r.descripcion FROM IdxConvocatoriaReglamento idx JOIN CatReglamento r ON idx.reglamentoId = r.id " +
           "WHERE idx.numeroConvocatoria = :num ORDER BY r.descripcion")
    List<String> findDescripcionesByNumeroConvocatoria(@Param("num") String num);
}
