package com.syntia.ai.model.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DocumentoBdnsDTO {
    private Long id;
    private String descripcion;
    private String nombreFic;
    private Long tamanio;
    private String fechaPublicacion;
}
