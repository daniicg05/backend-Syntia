package com.syntia.ai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "visual_assets",
        indexes = {
                @Index(name = "idx_visual_assets_asset_key", columnList = "asset_key", unique = true),
                @Index(name = "idx_visual_assets_normalized_entity", columnList = "normalized_entity"),
                @Index(name = "idx_visual_assets_domain", columnList = "domain")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisualAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_key", nullable = false, unique = true, length = 420)
    private String assetKey;

    @Column(name = "entity_name", nullable = false, length = 300)
    private String entityName;

    @Column(name = "normalized_entity", nullable = false, length = 300)
    private String normalizedEntity;

    @Column(name = "visual_type", length = 80)
    private String visualType;

    @Column(length = 255)
    private String domain;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "source_type", length = 80)
    private String sourceType;

    @Column(length = 40)
    private String status;

    private Integer confidence;

    @Builder.Default
    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastUsedAt = now;
        if (this.usageCount == null) this.usageCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
