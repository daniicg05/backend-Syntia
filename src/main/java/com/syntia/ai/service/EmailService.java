package com.syntia.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Service
public class EmailService {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${mailjet.api-key:}")
    private String mailjetApiKey;

    @Value("${mailjet.secret-key:}")
    private String mailjetSecretKey;

    @Value("${mailjet.sender-email:syntialicante@gmail.com}")
    private String senderEmail;

    @Value("${mailjet.sender-name:Syntia}")
    private String senderName;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${jwt.secret}")
    private String jwtSecret;

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
            String jsonBody = """
                    {
                      "Messages": [{
                        "From": {"Email": "%s", "Name": "%s"},
                        "To": [{"Email": "%s"}],
                        "Subject": "%s",
                        "HTMLPart": %s
                      }]
                    }
                    """.formatted(
                    senderEmail,
                    senderName,
                    destinatario.replace("\"", "\\\""),
                    asunto.replace("\"", "\\\""),
                    escapeJson(contenidoHtml)
            );

            String auth = Base64.getEncoder().encodeToString(
                    (mailjetApiKey + ":" + mailjetSecretKey).getBytes(StandardCharsets.UTF_8)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mailjet.com/v3.1/send"))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Email enviado a: {}", destinatario);
            } else {
                log.error("Error al enviar email a {}: {} {}", destinatario, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Error al enviar email a {}: {}", destinatario, e.getMessage(), e);
        }
    }

    private String escapeJson(String text) {
        return "\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
