package com.syntia.ai.repository;

import com.syntia.ai.model.CatOrgano;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatOrganoRepository extends JpaRepository<CatOrgano, Integer> {
    List<CatOrgano> findByParentIdIsNull();
    List<CatOrgano> findByTipoAdmin(String tipoAdmin);
}
