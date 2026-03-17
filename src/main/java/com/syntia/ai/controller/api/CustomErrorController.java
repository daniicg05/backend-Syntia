package com.syntia.ai.controller.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador personalizado para manejar errores globales en la aplicación.
 *
 * <p>Al implementar {@link ErrorController}, Spring Boot delega aquí las
 * solicitudes que llegan a la ruta /error cuando ocurre una excepción no controlada
 * o un problema durante el procesamiento de una petición HTTP.</p>
 *
 * <p>Este controlador devuelve una respuesta JSON con:</p>
 * <ul>
 * <li>Un mensaje de error legible para el cliente.</li>
 * <li>El código de estado detectado en la request original.</li>
 * </ul>
 */
@RestController
public class CustomErrorController implements ErrorController {

    /**
     * Endpoint estándar para errores en Spring Boot.
     *
     * <p>Cuando se produce un error, el contenedor servlet coloca información
     * relevante dentro de atributos de request, por ejemplo:</p>
     * <ul>
     * <li><code>jakarta.servlet.error.status_code</code>: código HTTP del error.</li>
     * <li><code>jakarta.servlet.error.message</code>: mensaje técnico (si existe).</li>
     * <li><code>jakarta.servlet.error.request_uri</code>: URI que generó el error.</li>
     * </ul>
     *
     * @param request petición HTTP actual que contiene metadatos del error
     * @return respuesta JSON con detalle básico del error
     */
    @GetMapping("/error")
    public ResponseEntity<?> handleError(HttpServletRequest request) {

        /** Mapa que representará el cuerpo JSON de salida.*/
        Map<String, Object> response = new HashMap<>();

        /** Mensaje general para el cliente (evita exponer detalles internos sensibles).*/
        response.put("error", "Ha ocurrido un error inesperado");

        /** Obtiene el código de estado HTTP almacenado por el contenedor.
        * Puede ser Integer, String o null, según el flujo de error.*/
        response.put("status", request.getAttribute("jakarta.servlet.error.status_code"));

        /** Responde con HTTP500 de forma explícita.
        * Nota: aunque el body incluye "status" de la request, aquí la cabecera HTTP
        * siempre será500 por diseño actual.*/
        return ResponseEntity.status(500).body(response);
    }
}