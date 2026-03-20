package com.syntia.ai.service;

import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.dto.RecomendacionDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Servicio de agregación de datos para dashboard en API REST.
 * Devuelve estructuras DTO-friendly para serialización JSON.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ProyectoService proyectoService;
    private final RecomendacionService recomendacionService;

    public DashboardService(ProyectoService proyectoService,
                            RecomendacionService recomendacionService) {
        this.proyectoService = proyectoService;
        this.recomendacionService = recomendacionService;
    }

    /**
     * Devuelve el top de recomendaciones por proyecto en formato REST.
     *
     * @param usuarioId ID del usuario autenticado
     * @param topN      máximo de recomendaciones por proyecto
     * @return lista de bloques proyecto + recomendaciones
     */
    public List<TopRecomendacionesProyecto> obtenerTopRecomendacionesPorProyecto(
            Long usuarioId, int topN) {

        int limite = Math.max(1, topN);
        List<Proyecto> proyectos = proyectoService.obtenerProyectos(usuarioId);
        List<TopRecomendacionesProyecto> resultado = new ArrayList<>();

        for (Proyecto proyecto : proyectos) {
            List<RecomendacionDTO> top = recomendacionService.obtenerPorProyecto(proyecto.getId())
                    .stream()
                    .sorted(Comparator.comparingInt(RecomendacionDTO::getPuntuacion).reversed())
                    .limit(limite)
                    .toList();

            resultado.add(new TopRecomendacionesProyecto(toResumenProyecto(proyecto), top));
        }

        return resultado;
    }

    /**
     * Construye el roadmap estratégico en formato REST.
     * - Con fecha de cierre >= hoy: ordenadas por urgencia (más próximas primero)
     * - Sin fecha de cierre: al final, ordenadas por puntuación desc
     */
    public List<RoadmapItem> obtenerRoadmap(Long usuarioId) {
        List<Proyecto> proyectos = proyectoService.obtenerProyectos(usuarioId);
        List<RoadmapItem> conFecha = new ArrayList<>();
        List<RoadmapItem> sinFecha = new ArrayList<>();
        LocalDate hoy = LocalDate.now();

        for (Proyecto proyecto : proyectos) {
            ProyectoResumen proyectoResumen = toResumenProyecto(proyecto);
            List<RecomendacionDTO> recs = recomendacionService.obtenerPorProyecto(proyecto.getId());

            for (RecomendacionDTO rec : recs) {
                if (rec.getFechaCierre() != null && !rec.getFechaCierre().isBefore(hoy)) {
                    conFecha.add(new RoadmapItem(proyectoResumen, rec));
                } else if (rec.getFechaCierre() == null) {
                    sinFecha.add(new RoadmapItem(proyectoResumen, rec));
                }
                // Fechas pasadas: se ignoran
            }
        }

        conFecha.sort(Comparator.comparing(item -> item.recomendacion().getFechaCierre()));
        sinFecha.sort((a, b) -> Integer.compare(
                b.recomendacion().getPuntuacion(),
                a.recomendacion().getPuntuacion()));

        List<RoadmapItem> resultado = new ArrayList<>(conFecha);
        sinFecha.stream().limit(10).forEach(resultado::add);
        return resultado;
    }

    /**
     * Cuenta el total de recomendaciones generadas para todos los proyectos del usuario.
     */
    public long contarTotalRecomendaciones(Long usuarioId) {
        return proyectoService.obtenerProyectos(usuarioId).stream()
                .mapToLong(p -> recomendacionService.contarPorProyecto(p.getId()))
                .sum();
    }

    private ProyectoResumen toResumenProyecto(Proyecto proyecto) {
        return new ProyectoResumen(
                proyecto.getId(),
                proyecto.getNombre(),
                proyecto.getSector(),
                proyecto.getUbicacion()
        );
    }

    /**
     * DTO mínimo de  proyecto para respuestas REST del dashboard.
     */
    public record ProyectoResumen(
            Long id,
            String nombre,
            String sector,
            String ubicacion
    ) {}

    /**
     * Bloque de top recomendaciones por proyecto para respuesta REST.
     */
    public record TopRecomendacionesProyecto(
            ProyectoResumen proyecto,
            List<RecomendacionDTO> recomendaciones
    ) {}

    /**
     * Elemento de roadmap para respuesta REST.
     */
    public record RoadmapItem(
            ProyectoResumen proyecto,
            RecomendacionDTO recomendacion
    ) {}
}