package com.syntia.ai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Caché persistente de traducciones de contenido dinámico.
 * Identidad lógica: (sourceLang, targetLang, sourceHash) — el hash actúa como
 * huella estable del texto de origen, de modo que si el texto cambia se
 * regenera automáticamente una nueva entrada y la antigua queda obsoleta.
 */
@Entity
@Table(
        name = "translations",
        indexes = {
                @Index(name = "idx_translations_lookup",
                        columnList = "target_lang, source_hash, source_lang", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Translation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_lang", nullable = false, length = 8)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 8)
    private String targetLang;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    @Column(name = "translated_text", columnDefinition = "TEXT")
    private String translatedText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
