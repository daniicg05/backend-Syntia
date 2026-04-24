package com.syntia.ai.repository;

import com.syntia.ai.model.IdxConvocatoriaTipoAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IdxConvocatoriaTipoAdminRepository extends JpaRepository<IdxConvocatoriaTipoAdmin, Long> {
    void deleteByNumeroConvocatoria(String numeroConvocatoria);
    boolean existsByNumeroConvocatoriaAndTipoAdmin(String numeroConvocatoria, String tipoAdmin);

    @Query("SELECT DISTINCT idx.tipoAdmin FROM IdxConvocatoriaTipoAdmin idx WHERE idx.numeroConvocatoria = :num")
    List<String> findTiposAdminByNumeroConvocatoria(@Param("num") String num);
}
