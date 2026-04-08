package ro.timetable.notifications.service;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import ro.timetable.common.dto.ApiDtos.MailStatusResponse;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class MailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailService.class);
    private static final String AUTOMATED_FOOTER = "Acest email a fost generat automat. Do not reply.";
    private static final String MAILTRAP_SANDBOX_HOST = "sandbox.smtp.mailtrap.io";
    private static final long MAILTRAP_SANDBOX_MIN_INTERVAL_MS = 1200L;
    private static final long MAILTRAP_SANDBOX_RETRY_DELAY_MS = 5000L;
    private static final String MAILTRAP_RATE_LIMIT_MESSAGE = "Too many emails per second";

    private record MailConfigurationStatus(boolean enabled, boolean configured, boolean sandbox, String detail) {
    }

    private final JavaMailSender mailSender;
    private final AtomicBoolean disabledWarningLogged = new AtomicBoolean(false);
    private final AtomicBoolean missingCredentialsWarningLogged = new AtomicBoolean(false);
    private final AtomicBoolean sandboxWarningLogged = new AtomicBoolean(false);
    private final Object sandboxRateLock = new Object();
    private long lastSandboxSendAt = 0L;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from-address:no-reply@timetable.local}")
    private String fromAddress;

    @Value("${app.mail.from-name:Digitalization of Grading and School Management}")
    private String fromName;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${spring.mail.host:localhost}")
    private String smtpHost;

    @Value("${spring.mail.port:2525}")
    private Integer smtpPort;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private boolean smtpAuth;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendWelcomeEmailBestEffort(String recipientEmail, String recipientName, String username, String className) {
        List<String> detailLines = new ArrayList<>();
        detailLines.add("Contul tau de elev a fost creat cu succes in platforma.");
        if (username != null && !username.isBlank()) {
            detailLines.add("Username: " + username);
        }
        if (className != null && !className.isBlank()) {
            detailLines.add("Clasa: " + className);
        }
        detailLines.add("Te poti autentifica din pagina de login folosind credentialele alese la inregistrare.");

        sendHtmlEmailBestEffort(
                recipientEmail,
                "Cont nou creat in platforma scolara",
                "Bun venit, " + fallbackRecipientName(recipientName) + "!",
                detailLines
        );
    }

    @Async
    public void sendNotificationEmailBestEffort(String recipientEmail, String recipientName, String message) {
        sendHtmlEmailBestEffort(
                recipientEmail,
                "Notificare din platforma scolara",
                "Ai primit o notificare noua in platforma.",
                List.of(normalizeLine(message))
        );
    }

    public MailStatusResponse getStatus() {
        MailConfigurationStatus status = configurationStatus();
        return new MailStatusResponse(
                status.enabled(),
                status.configured(),
                normalizeLine(smtpHost),
                smtpPort,
                status.sandbox() ? "mailtrap_sandbox" : "smtp",
                normalizeLine(fromAddress),
                status.detail()
        );
    }

    public void sendTestEmailOrThrow(String recipientEmail, String recipientName, String customMessage) {
        String normalizedEmail = normalizeLine(recipientEmail);
        if (normalizedEmail == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Destinatarul nu are o adresa de email valida.");
        }

        MailConfigurationStatus status = configurationStatus();
        if (!status.enabled() || !status.configured()) {
            throw new ResponseStatusException(BAD_REQUEST, status.detail());
        }

        List<String> detailLines = new ArrayList<>();
        detailLines.add(normalizeLine(customMessage) == null
                ? "Acesta este un email de test generat din platforma."
                : normalizeLine(customMessage));
        if (status.sandbox()) {
            detailLines.add("Configuratia actuala foloseste Mailtrap Sandbox, deci mesajul va aparea in inboxul Mailtrap si nu in casuta reala a destinatarului.");
        }

        try {
            sendHtmlEmailWithSandboxProtection(
                    normalizedEmail,
                    "Email de test din platforma scolara",
                    "Test email pentru " + fallbackRecipientName(recipientName),
                    detailLines,
                    status.sandbox()
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Emailul de test nu a putut fi trimis. Verifica setarile SMTP.");
        }
    }

    private void sendHtmlEmailBestEffort(String recipientEmail, String subject, String headline, List<String> detailLines) {
        MailConfigurationStatus status = configurationStatus();
        if (!status.enabled()) {
            if (disabledWarningLogged.compareAndSet(false, true)) {
                LOGGER.warn(status.detail());
            }
            return;
        }

        if (!status.configured()) {
            if (missingCredentialsWarningLogged.compareAndSet(false, true)) {
                LOGGER.warn(status.detail());
            }
            return;
        }

        if (status.sandbox() && sandboxWarningLogged.compareAndSet(false, true)) {
            LOGGER.warn(status.detail());
        }

        String normalizedEmail = normalizeLine(recipientEmail);
        if (normalizedEmail == null) {
            return;
        }

        try {
            sendHtmlEmailWithSandboxProtection(normalizedEmail, subject, headline, detailLines, status.sandbox());
        } catch (Exception exception) {
            LOGGER.warn("Could not send email to {}", normalizedEmail, exception);
        }
    }

    private MailConfigurationStatus configurationStatus() {
        String normalizedHost = normalizeLine(smtpHost);
        boolean sandbox = normalizedHost != null && MAILTRAP_SANDBOX_HOST.equalsIgnoreCase(normalizedHost);
        boolean configured = normalizedHost != null
                && (!smtpAuth || (normalizeLine(smtpUsername) != null && normalizeLine(smtpPassword) != null));

        if (!mailEnabled) {
            return new MailConfigurationStatus(
                    false,
                    configured,
                    sandbox,
                    "Trimiterea emailurilor este dezactivata. Verifica APP_MAIL_ENABLED."
            );
        }

        if (!configured) {
            return new MailConfigurationStatus(
                    true,
                    false,
                    sandbox,
                    "Trimiterea emailurilor este activa, dar lipsesc credentialele SMTP. Completeaza SPRING_MAIL_USERNAME si SPRING_MAIL_PASSWORD."
            );
        }

        if (sandbox) {
            return new MailConfigurationStatus(
                    true,
                    true,
                    true,
                    "Mailtrap Sandbox este activ. Emailurile ajung in inboxul Mailtrap si nu in casuta reala a destinatarului."
            );
        }

        return new MailConfigurationStatus(true, true, false, "Trimiterea emailurilor este activa.");
    }

    private void sendHtmlEmailWithSandboxProtection(
            String normalizedEmail,
            String subject,
            String headline,
            List<String> detailLines,
            boolean sandbox
    ) throws Exception {
        throttleSandboxIfNeeded(sandbox);
        try {
            sendHtmlEmail(normalizedEmail, subject, headline, detailLines);
        } catch (Exception exception) {
            if (!sandbox || !isSandboxRateLimit(exception)) {
                throw exception;
            }

            waitForSandboxRetry();
            sendHtmlEmail(normalizedEmail, subject, headline, detailLines);
        }
    }

    private void throttleSandboxIfNeeded(boolean sandbox) {
        if (!sandbox) {
            return;
        }

        synchronized (sandboxRateLock) {
            long now = System.currentTimeMillis();
            long waitTime = MAILTRAP_SANDBOX_MIN_INTERVAL_MS - (now - lastSandboxSendAt);
            if (waitTime > 0) {
                try {
                    // Mailtrap Sandbox limiteaza trimiterea rapida, asa ca spatiem mailurile automate.
                    Thread.sleep(waitTime);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Trimiterea emailului a fost intrerupta", exception);
                }
            }
            lastSandboxSendAt = System.currentTimeMillis();
        }
    }

    private void waitForSandboxRetry() {
        synchronized (sandboxRateLock) {
            sleepSafely(MAILTRAP_SANDBOX_RETRY_DELAY_MS);
            lastSandboxSendAt = System.currentTimeMillis();
        }
    }

    private boolean isSandboxRateLimit(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(MAILTRAP_RATE_LIMIT_MESSAGE)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void sleepSafely(long waitTime) {
        try {
            // Mailtrap Sandbox limiteaza trimiterea rapida, asa ca spatiem mailurile automate si re-incercam la nevoie.
            Thread.sleep(waitTime);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Trimiterea emailului a fost intrerupta", exception);
        }
    }

    private void sendHtmlEmail(String normalizedEmail, String subject, String headline, List<String> detailLines) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        helper.setFrom(fromAddress, fromName);
        helper.setTo(normalizedEmail);
        helper.setSubject(normalizeLine(subject));
        helper.setText(renderHtml(headline, detailLines), true);
        mailSender.send(message);
    }

    private String renderHtml(String headline, List<String> detailLines) {
        StringBuilder details = new StringBuilder();
        for (String line : detailLines.stream().map(this::normalizeLine).filter(Objects::nonNull).toList()) {
            details.append("<li style=\"margin-bottom:8px;\">")
                    .append(HtmlUtils.htmlEscape(line))
                    .append("</li>");
        }

        return """
                <div style="font-family:Arial,sans-serif;background:#f4f7fb;padding:24px;color:#102033;">
                  <div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #d7e0ec;border-radius:18px;padding:24px;">
                    <div style="font-size:12px;font-weight:700;letter-spacing:0.12em;text-transform:uppercase;color:#4d7ab8;margin-bottom:12px;">
                      Digitalization of Grading and School Management
                    </div>
                    <h1 style="margin:0 0 16px;font-size:24px;line-height:1.2;color:#102033;">%s</h1>
                    <ul style="margin:0 0 20px 18px;padding:0;color:#30465f;line-height:1.6;">%s</ul>
                    <div style="margin-top:24px;padding-top:16px;border-top:1px solid #d7e0ec;font-size:12px;color:#5f6f82;">
                      %s
                    </div>
                  </div>
                </div>
                """.formatted(
                HtmlUtils.htmlEscape(normalizeLine(headline)),
                details,
                HtmlUtils.htmlEscape(AUTOMATED_FOOTER)
        );
    }

    private String normalizeLine(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private String fallbackRecipientName(String recipientName) {
        String normalized = normalizeLine(recipientName);
        return normalized == null ? "elev" : normalized;
    }
}
