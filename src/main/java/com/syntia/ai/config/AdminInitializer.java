package com.syntia.ai.config;

import com.syntia.ai.model.Rol;
import com.syntia.ai.service.UsuarioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminInitializer {

    private final UsuarioService usuarioService;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    public AdminInitializer(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void crearAdminSiNoExiste() {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("ADMIN_PASSWORD no configurado — se omite la creación del admin inicial");
            return;
        }
        if (usuarioService.buscarPorEmail(adminEmail).isEmpty()) {
            usuarioService.registrar(adminEmail, adminPassword, Rol.ADMIN);
            log.info("Usuario admin creado: {}", adminEmail);
        }
    }
}