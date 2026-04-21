package com.syntia.ai.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FavoritaImportRequestDTO {

    @NotNull(message = "favoritas es obligatorio")
    @Valid
    private List<FavoritaItemImportDTO> favoritas;
}

