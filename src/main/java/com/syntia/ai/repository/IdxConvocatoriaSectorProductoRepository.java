package com.syntia.ai.repository;

import com.syntia.ai.model.IdxConvocatoriaSectorProducto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IdxConvocatoriaSectorProductoRepository extends JpaRepository<IdxConvocatoriaSectorProducto, Long> {
    void deleteByNumeroConvocatoria(String numeroConvocatoria);
    boolean existsByNumeroConvocatoriaAndSectorProductoId(String numeroConvocatoria, Integer sectorProductoId);

    @Query("SELECT s.descripcion FROM IdxConvocatoriaSectorProducto idx JOIN CatSectorProducto s ON idx.sectorProductoId = s.id " +
           "WHERE idx.numeroConvocatoria = :num ORDER BY s.descripcion")
    List<String> findDescripcionesByNumeroConvocatoria(@Param("num") String num);
}
