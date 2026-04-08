package com.syntia.ai.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/**
 * Servicio responsable del envío de correos transaccionales.
 *
 * <p>Actualmente soporta el envío de correo de verificación de cuenta
 * usando SMTP configurado en application.properties (spring.mail.*).</p>
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * Email remitente. Por defecto reutiliza spring.mail.username.
     */
    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * URL base del frontend para verificación.
     * Mantener configurable evita hardcodear entornos.
     */
    @Value("${app.frontend.verify-url:http://localhost:3000/verify}")
    private String verifyUrlBase;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Envía un correo HTML con enlace de verificación de cuenta.
     *
     * <p>El token se codifica para URL por seguridad ante caracteres especiales.</p>
     *
     * @param toEmail destinatario
     * @param token token de verificación generado al registrar
     * @throws IllegalStateException si falla la construcción o envío del email
     */
    public void sendVerificationEmail(String toEmail, String token) {
        try {
            String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
            String verificationLink = verifyUrlBase + "?token=" + encodedToken;

            /**
             * Plantilla HTML mínima para compatibilidad amplia en clientes de correo.
             */
            String htmlBody = """
                    <html>
                      <body>
                        <h2>Verifica tu cuenta</h2>
                        <p>Gracias por registrarte en Syntia.</p>
                        <p>Haz clic en el siguiente enlace para activar tu cuenta:</p>
                        <p><a href="%s">Verificar cuenta</a></p>
                      </body>
                    </html>
                    """.formatted(verificationLink);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name()
            );

            helper.setTo(toEmail);
            helper.setFrom(fromEmail);
            helper.setSubject("Verifica tu cuenta");
            helper.setText(htmlBody, true);

            mailSender.send(message);

        } catch (MessagingException | MailException e) {
            throw new IllegalStateException("No se pudo enviar el email de verificación", e);
        }
    }
}