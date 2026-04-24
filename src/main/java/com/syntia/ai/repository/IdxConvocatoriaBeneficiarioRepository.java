package com.syntia.ai.repository;

import com.syntia.ai.model.IdxConvocatoriaBeneficiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface IdxConvocatoriaBeneficiarioRepository extends JpaRepository<IdxConvocatoriaBeneficiario, Long> {
    void deleteByNumeroConvocatoria(String numeroConvocatoria);
    boolean existsByNumeroConvocatoriaAndBeneficiarioId(String numeroConvocatoria, Integer beneficiarioId);

    @Query("SELECT idx.numeroConvocatoria, b.descripcion " +
           "FROM IdxConvocatoriaBeneficiario idx JOIN CatBeneficiario b ON idx.beneficiarioId = b.id " +
           "WHERE idx.numeroConvocatoria IN :numeros ORDER BY b.descripcion")
    List<Object[]> findBeneficiariosByNumeros(@Param("numeros") Collection<String> numeros);
}
