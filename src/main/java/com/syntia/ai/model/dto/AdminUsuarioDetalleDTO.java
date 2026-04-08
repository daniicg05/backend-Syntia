package com.syntia.ai.model.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUsuarioDetalleDTO {
    private Long id;
    private String email;
    private String rol;
    private LocalDateTime creadoEn;
    private String empresa;
    private String provincia;
    private String telefono;
}

