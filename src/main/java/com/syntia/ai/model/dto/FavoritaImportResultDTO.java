package com.syntia.ai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FavoritaImportResultDTO {
    private int importadas;
    private int actualizadas;
    private int omitidas;
}

