package com.syntia.ai.repository;

import com.syntia.ai.model.BdnsFinalidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BdnsFinalidadesRepository extends JpaRepository<BdnsFinalidad, Integer> {

    @Query("SELECT f FROM BdnsFinalidad f WHERE LOWER(f.nombre) LIKE LOWER(CONCAT('%', :texto, '%')) ORDER BY f.nombre ASC")
    List<BdnsFinalidad> findBestMatch(@Param("texto") String texto);
}

