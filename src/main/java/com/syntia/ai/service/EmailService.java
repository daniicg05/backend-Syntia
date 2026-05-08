package com.syntia.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.scheduling.annotation.Async;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${jwt.secret}")
    private String jwtSecret;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public String generarFirma(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error generando firma HMAC", e);
        }
    }

    public boolean verificarFirma(String token, String firma) {
        String firmaEsperada = generarFirma(token);
        return firmaEsperada.equals(firma);
    }

    @Async
    public void enviarEmailVerificacion(String destinatario, String token) {
        String firma = generarFirma(token);
        String enlace = frontendUrl + "/verificar-email?token=" + token + "&firma=" + firma;
        String asunto = "Verifica tu cuenta en Syntia";
        String contenido = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #0d9488;">Bienvenido a Syntia</h2>
                    <p>Gracias por registrarte. Para activar tu cuenta, haz clic en el siguiente enlace:</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background-color: #0d9488; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Verificar mi cuenta
                        </a>
                    </p>
                    <p style="color: #666; font-size: 14px;">Este enlace expira en 24 horas.</p>
                    <p style="color: #666; font-size: 14px;">Si no te has registrado en Syntia, ignora este mensaje.</p>
                </div>
                """.formatted(enlace);

        enviarEmail(destinatario, asunto, contenido);
    }

    private void enviarEmail(String destinatario, String asunto, String contenidoHtml) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(contenidoHtml, true);
            mailSender.send(mensaje);
            log.info("Email enviado a: {}", destinatario);
        } catch (MessagingException e) {
            log.error("Error al enviar email a {}: {}", destinatario, e.getMessage(), e);
        }
    }
}
