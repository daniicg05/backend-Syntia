package com.syntia.ai.repository;

import com.syntia.ai.model.Translation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TranslationRepository extends JpaRepository<Translation, Long> {

    Optional<Translation> findFirstByTargetLangAndSourceHashAndSourceLang(
            String targetLang, String sourceHash, String sourceLang);

    List<Translation> findAllByTargetLangAndSourceHashInAndSourceLang(
            String targetLang, List<String> sourceHashes, String sourceLang);
}
