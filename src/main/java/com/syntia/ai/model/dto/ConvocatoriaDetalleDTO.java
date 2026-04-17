package com.syntia.ai.model.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ConvocatoriaDetalleDTO {
    private Long id;
    private String codigoBdns;
    private String sector;
    private String descripcion;
    private List<String> tiposBeneficiario;
}
