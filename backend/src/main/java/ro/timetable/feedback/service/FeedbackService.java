package ro.timetable.feedback.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.audit.service.AuditService;
import ro.timetable.common.dto.ApiDtos.FeedbackEntryResponse;
import ro.timetable.feedback.entity.FeedbackEntryEntity;
import ro.timetable.feedback.repository.FeedbackEntryRepository;
import ro.timetable.notifications.service.NotificationService;
import ro.timetable.reference.service.SchoolDataService;

@Service
public class FeedbackService {

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int NOTIFICATION_REPLY_PREVIEW_LENGTH = 180;
    private static final String SOURCE_HELP = "HELP";

    private final AuditService auditService;
    private final FeedbackEntryRepository feedbackEntryRepository;
    private final NotificationService notificationService;
    private final SchoolDataService schoolDataService;

    public FeedbackService(
            AuditService auditService,
            FeedbackEntryRepository feedbackEntryRepository,
            NotificationService notificationService,
            SchoolDataService schoolDataService
    ) {
        this.auditService = auditService;
        this.feedbackEntryRepository = feedbackEntryRepository;
        this.notificationService = notificationService;
        this.schoolDataService = schoolDataService;
    }

    @Transactional(readOnly = true)
    public List<FeedbackEntryResponse> listEntries(String actorUsername, List<String> roles) {
        boolean reviewer = canReview(roles);
        List<FeedbackEntryEntity> entities = reviewer
                ? feedbackEntryRepository.findAllByOrderByCreatedAtDescIdDesc()
                : feedbackEntryRepository.findBySubmittedByUsernameOrderByCreatedAtDescIdDesc(actorUsername);

        return entities.stream()
                .filter(this::isHelpEntry)
                .map(entity -> toResponse(entity, reviewer))
                .toList();
    }

    @Transactional(readOnly = true)
    public FeedbackEntryResponse getEntry(Long feedbackId, String actorUsername, List<String> roles) {
        boolean reviewer = canReview(roles);
        return toResponse(accessibleEntry(feedbackId, actorUsername, reviewer), reviewer);
    }

    @Transactional
    public FeedbackEntryResponse submitFeedback(
            String actorUsername,
            List<String> roles,
            String category,
            String satisfaction,
            boolean wantsContact,
            String message
    ) {
        if (roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Trebuie sa fii autentificat pentru a trimite feedback");
        }

        schoolDataService.getProfile(actorUsername);

        String normalizedCategory = normalizeHelpCategory(category);
        String normalizedSatisfaction = normalizeSatisfaction(satisfaction);
        String normalizedMessage = normalizeMessage(message);

        FeedbackEntryEntity entity = new FeedbackEntryEntity();
        entity.setSubmittedByUsername(actorUsername);
        entity.setCategory(normalizedCategory);
        entity.setSatisfaction(normalizedSatisfaction);
        entity.setSource(SOURCE_HELP);
        entity.setWantsContact(wantsContact);
        entity.setMessage(normalizedMessage);
        entity.setStatus("UNOPENED");
        entity.setCreatedAt(Instant.now());

        FeedbackEntryEntity saved = feedbackEntryRepository.save(entity);
        notificationService.createNotifications(
                reviewerUsernames(),
                new NotificationService.NotificationPayload(
                        "Cerere asistenta noua",
                        "Cerere noua de asistenta de la " + actorUsername + " pentru categoria " + labelForCategory(normalizedCategory, SOURCE_HELP),
                        "feedback",
                        "/app/feedback"
                )
        );
        auditService.record(
                "Trimitere asistenta",
                actorUsername,
                "A fost trimisa o cerere de asistenta pentru categoria " + labelForCategory(normalizedCategory, SOURCE_HELP)
        );
        return toResponse(saved, canReview(roles));
    }

    @Transactional
    public FeedbackEntryResponse updateStatus(Long feedbackId, String actorUsername, List<String> roles, String status) {
        ensureReviewer(roles);

        FeedbackEntryEntity entity = accessibleEntry(feedbackId, actorUsername, true);
        String normalizedStatus = normalizeStatus(status);
        Instant now = Instant.now();

        entity.setStatus(normalizedStatus);
        entity.setStatusUpdatedByUsername(actorUsername);
        entity.setStatusUpdatedAt(now);

        FeedbackEntryEntity saved = feedbackEntryRepository.save(entity);
        auditService.record(
                "Actualizare status feedback",
                actorUsername,
                "Feedback #" + feedbackId + " marcat ca " + labelForStatus(normalizedStatus)
        );
        return toResponse(saved, true);
    }

    @Transactional
    public FeedbackEntryResponse replyToFeedback(Long feedbackId, String actorUsername, List<String> roles, String replyMessage) {
        ensureReviewer(roles);

        FeedbackEntryEntity entity = accessibleEntry(feedbackId, actorUsername, true);
        String source = entity.getSource() == null || entity.getSource().isBlank() ? SOURCE_HELP : entity.getSource();
        if (!SOURCE_HELP.equals(source)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback-ul anonim nu accepta reply");
        }
        if (!entity.isWantsContact()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elevul nu a solicitat sa fie contactat pentru acest feedback");
        }
        if ("RESOLVED".equals(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nu mai poti trimite reply pentru un feedback rezolvat");
        }

        String normalizedReplyMessage = normalizeReplyMessage(replyMessage);
        Instant now = Instant.now();

        entity.setReplyMessage(normalizedReplyMessage);
        entity.setRepliedByUsername(actorUsername);
        entity.setRepliedAt(now);

        if ("UNOPENED".equals(entity.getStatus())) {
            entity.setStatus("IN_PROGRESS");
            entity.setStatusUpdatedByUsername(actorUsername);
            entity.setStatusUpdatedAt(now);
        }

        FeedbackEntryEntity saved = feedbackEntryRepository.save(entity);
        notificationService.sendToUser(
                entity.getSubmittedByUsername(),
                new NotificationService.NotificationPayload(
                        "Raspuns la solicitarea ta",
                        buildReplyNotificationMessage(saved.getCategory(), normalizedReplyMessage),
                        "feedback",
                        "/app/feedback/" + saved.getId()
                )
        );
        auditService.record(
                "Raspuns feedback",
                actorUsername,
                "A fost trimis un raspuns pentru feedback-ul #" + feedbackId
        );
        return toResponse(saved, true);
    }

    private void ensureReviewer(List<String> roles) {
        if (!canReview(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nu ai dreptul sa gestionezi feedback-ul");
        }
    }

    private FeedbackEntryEntity accessibleEntry(Long feedbackId, String actorUsername, boolean reviewer) {
        FeedbackEntryEntity entity = reviewer
                ? feedbackEntryRepository.findById(feedbackId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback-ul nu a fost gasit"))
                : feedbackEntryRepository.findByIdAndSubmittedByUsername(feedbackId, actorUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback-ul nu a fost gasit"));
        if (!isHelpEntry(entity)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback-ul nu a fost gasit");
        }
        return entity;
    }

    private boolean canReview(List<String> roles) {
        return roles.contains("secretariat") || roles.contains("admin") || roles.contains("sysadmin");
    }

    private String normalizeHelpCategory(String category) {
        String normalized = category == null ? null : category.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "general", "orar", "catalog", "documente", "cont" -> normalized;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria feedback-ului este invalida");
        };
    }

    private String normalizeSatisfaction(String satisfaction) {
        String normalized = satisfaction == null ? null : satisfaction.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pozitiva", "neutra", "negativa" -> normalized;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipul de feedback este invalid");
        };
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "UNOPENED", "IN_PROGRESS", "RESOLVED" -> normalized;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statusul feedback-ului este invalid");
        };
    }

    private String normalizeMessage(String message) {
        String normalized = message == null ? null : message.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mesajul feedback-ului este obligatoriu");
        }
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mesajul feedback-ului poate avea maximum 2000 de caractere");
        }
        return normalized;
    }

    private String normalizeReplyMessage(String message) {
        String normalized = message == null ? null : message.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mesajul de reply este obligatoriu");
        }
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mesajul de reply poate avea maximum 2000 de caractere");
        }
        return normalized;
    }

    private List<String> reviewerUsernames() {
        List<String> usernames = new ArrayList<>();
        schoolDataService.getUserProfilesByRole("secretariat").forEach(profile -> usernames.add(profile.username()));
        schoolDataService.getUserProfilesByRole("admin").forEach(profile -> usernames.add(profile.username()));
        schoolDataService.getUserProfilesByRole("sysadmin").forEach(profile -> usernames.add(profile.username()));
        return usernames.stream().distinct().toList();
    }

    private boolean isHelpEntry(FeedbackEntryEntity entity) {
        String source = entity.getSource();
        return source == null || source.isBlank() || SOURCE_HELP.equals(source);
    }

    private String labelForCategory(String category, String source) {
        return switch (category) {
            case "orar" -> "Orar";
            case "catalog" -> "Catalog";
            case "documente" -> "Documente";
            case "cont" -> "Cont";
            case "general" -> "General";
            default -> category;
        };
    }

    private String labelForSatisfaction(String satisfaction) {
        return switch (satisfaction) {
            case "pozitiva" -> "Pozitiva";
            case "neutra" -> "Neutra";
            case "negativa" -> "Negativa";
            default -> satisfaction;
        };
    }

    private String labelForStatus(String status) {
        return switch (status) {
            case "UNOPENED" -> "Nedeschis";
            case "IN_PROGRESS" -> "In curs de rezolvare";
            case "RESOLVED" -> "Rezolvat";
            default -> status;
        };
    }

    private String buildReplyNotificationMessage(String category, String replyMessage) {
        String prefix = "Raspuns la cererea ta de asistenta (" + labelForCategory(category, SOURCE_HELP) + "): ";
        String normalizedReplyMessage = replyMessage.replaceAll("\\s+", " ").trim();
        if (normalizedReplyMessage.length() <= NOTIFICATION_REPLY_PREVIEW_LENGTH) {
            return prefix + normalizedReplyMessage;
        }
        return prefix + normalizedReplyMessage.substring(0, NOTIFICATION_REPLY_PREVIEW_LENGTH - 3) + "...";
    }

    private FeedbackEntryResponse toResponse(FeedbackEntryEntity entity, boolean reviewer) {
        return new FeedbackEntryResponse(
                entity.getId(),
                entity.getCategory(),
                labelForCategory(entity.getCategory(), SOURCE_HELP),
                entity.getSatisfaction(),
                labelForSatisfaction(entity.getSatisfaction()),
                entity.isWantsContact(),
                entity.getStatus(),
                labelForStatus(entity.getStatus()),
                entity.getMessage(),
                entity.getReplyMessage(),
                entity.getSubmittedByUsername(),
                entity.getRepliedByUsername(),
                entity.getStatusUpdatedByUsername(),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString(),
                entity.getRepliedAt() == null ? null : entity.getRepliedAt().toString(),
                entity.getStatusUpdatedAt() == null ? null : entity.getStatusUpdatedAt().toString(),
                reviewer && entity.isWantsContact() && !"RESOLVED".equals(entity.getStatus()),
                reviewer
        );
    }
}
