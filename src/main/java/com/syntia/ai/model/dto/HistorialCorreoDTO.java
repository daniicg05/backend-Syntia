package com.syntia.ai.model.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistorialCorreoDTO {
    private String anterior;
    private String nuevo;
    private LocalDateTime fecha;
    private String actor;
}

