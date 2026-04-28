package com.syntia.ai.service;

/**
 * Modo de ejecución del job de importación masiva BDNS.
 * <ul>
 *   <li>{@link #FULL} — reimporta todos los ejes desde la página 0, independientemente del estado previo.</li>
 *   <li>{@link #INCREMENTAL} — salta ejes ya COMPLETADOS y reanuda los que quedaron en ERROR desde
 *       la última página correctamente procesada.</li>
 * </ul>
 */
public enum ModoImportacion {
    FULL,
    NUEVAS,
    INCREMENTAL
}
