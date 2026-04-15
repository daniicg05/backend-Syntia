package com.syntia.ai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConvocatoriaDetalleDTO {
    private Long id;
    private String codigoBdns;
    private String sector;
    private String descripcion;
    private List<String> tiposBeneficiario;
}

