package com.syntia.ai.service;

import com.syntia.ai.model.dto.ConvocatoriaDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Estrategia de importación masiva BDNS por ejes territoriales.
 * <p>
 * En lugar de paginar linealmente (que devuelve un subconjunto rotativo),
 * itera por cada combinación de nivel1 + nivel2:
 * <ul>
 *   <li>ESTADO (todas las páginas)</li>
 *   <li>AUTONOMICA × 19 CCAA (todas las páginas de cada una)</li>
 *   <li>LOCAL (todas las páginas)</li>
 *   <li>OTROS (todas las páginas)</li>
 * </ul>
 * La deduplicación por idBdns evita duplicados entre ejes.
 */
@Slf4j
@Service
public class BdnsImportEstrategiaService {

    private static final int TAM_PAGINA = 50;

    /** Las 19 CCAA exactas que acepta nivel2 en la API BDNS. */
    private static final List<String> CCAA = List.of(
            "Andalucía", "Aragón", "Asturias", "Baleares", "Canarias",
            "Cantabria", "Castilla y León", "Castilla-La Mancha", "Cataluña",
            "Comunidad Valenciana", "Extremadura", "Galicia", "Madrid",
            "Murcia", "Navarra", "País Vasco", "La Rioja", "Ceuta", "Melilla"
    );

    @Value("${bdns.import.delay-ms:300}")
    private long delayMs;

    private final BdnsClientService bdnsClientService;
    private final ConvocatoriaService convocatoriaService;

    public BdnsImportEstrategiaService(BdnsClientService bdnsClientService,
                                       ConvocatoriaService convocatoriaService) {
        this.bdnsClientService = bdnsClientService;
        this.convocatoriaService = convocatoriaService;
    }

    /**
     * Ejecuta la importación completa por todos los ejes territoriales.
     *
     * @param onProgreso callback invocado tras cada página: (ejeActual, totalNuevosAcumulados)
     * @return total de registros nuevos importados
     */
    public int importarTodo(BiConsumer<String, Integer> onProgreso) throws InterruptedException {
        int total = 0;

        total += importarEje("ESTADO", null, total, onProgreso);
        for (String ccaa : CCAA) {
            total += importarEje("AUTONOMICA", ccaa, total, onProgreso);
        }
        total += importarEje("LOCAL", null, total, onProgreso);
        total += importarEje("OTROS", null, total, onProgreso);

        return total;
    }

    private int importarEje(String nivel1, String nivel2, int totalPrevio,
                             BiConsumer<String, Integer> onProgreso) throws InterruptedException {
        String ejeDesc = nivel2 != null ? nivel1 + " – " + nivel2 : nivel1;
        log.info("BDNS estrategia: iniciando eje [{}]", ejeDesc);

        int pag = 0;
        int nuevosEje = 0;

        while (true) {
            onProgreso.accept(ejeDesc + " – pág. " + pag, totalPrevio + nuevosEje);

            List<ConvocatoriaDTO> pagina = bdnsClientService.importarPorEje(nivel1, nivel2, pag, TAM_PAGINA);

            if (pagina.isEmpty()) {
                log.info("BDNS estrategia: eje [{}] completado — {} páginas, {} nuevos", ejeDesc, pag, nuevosEje);
                break;
            }

            int nuevasPag = convocatoriaService.persistirNuevas(pagina);
            nuevosEje += nuevasPag;

            if (pag % 10 == 0) {
                log.info("BDNS estrategia: eje [{}] pág. {} — {} nuevos en este eje", ejeDesc, pag, nuevosEje);
            }

            if (pagina.size() < TAM_PAGINA) {
                log.info("BDNS estrategia: eje [{}] completado — {} páginas, {} nuevos", ejeDesc, pag + 1, nuevosEje);
                break;
            }

            pag++;
            Thread.sleep(delayMs);
        }

        return nuevosEje;
    }
}