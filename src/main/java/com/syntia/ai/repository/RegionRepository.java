package com.syntia.ai.repository;

import com.syntia.ai.model.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {

    List<Region> findByParentIdIsNull();

    List<Region> findByParentId(Long parentId);
}