package ro.timetable.documents.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.audit.service.AuditService;
import ro.timetable.catalog.service.CatalogService;
import ro.timetable.common.dto.ApiDtos.CatalogResponse;
import ro.timetable.common.dto.ApiDtos.CatalogSubjectResponse;
import ro.timetable.common.dto.ApiDtos.DocumentRequestResponse;
import ro.timetable.common.dto.ApiDtos.GradeResponse;
import ro.timetable.documents.entity.DocumentRequestEntity;
import ro.timetable.documents.repository.DocumentRequestRepository;
import ro.timetable.notifications.service.NotificationService;
import ro.timetable.reference.model.SchoolClass;
import ro.timetable.reference.model.UserProfile;
import ro.timetable.reference.service.SchoolDataService;

@Service
public class DocumentService {

    public static final String TYPE_STUDENT_CERTIFICATE = "student_certificate";
    public static final String TYPE_TRANSCRIPT = "transcript";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private static final String STUDENT_CERTIFICATE_SERIES = "ADE";
    private static final String TRANSCRIPT_SERIES = "FMT";
    private static final String SCHOOL_INSPECTORATE = "Inspectoratul Scolar Judetean Arges";
    private static final String SCHOOL_NAME = "Colegiul National \"Dinicu Golescu\"";
    private static final String SCHOOL_ADDRESS = "Str. Negru Voda nr. 66, Campulung Muscel, jud. Arges";
    private static final String SCHOOL_PHONE = "0248 510 570";
    private static final String SCHOOL_EMAIL = "cndgolescu@yahoo.com";
    private static final String STUDY_FORM = "zi";
    private static final String STUDENT_CERTIFICATE_TEMPLATE = "templates/student-certificate-template.html";
    private static final String TRANSCRIPT_TEMPLATE = "templates/transcript-template.html";
    private static final DateTimeFormatter RO_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT);

    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SchoolDataService schoolDataService;
    private final CatalogService catalogService;
    private final DocumentRequestRepository documentRequestRepository;
    private final ObjectMapper objectMapper;

    public DocumentService(
            AuditService auditService,
            NotificationService notificationService,
            SchoolDataService schoolDataService,
            CatalogService catalogService,
            DocumentRequestRepository documentRequestRepository,
            ObjectMapper objectMapper
    ) {
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.schoolDataService = schoolDataService;
        this.catalogService = catalogService;
        this.documentRequestRepository = documentRequestRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<DocumentRequestResponse> listRequests(String actorUsername, List<String> roles) {
        if (canReview(roles)) {
            return documentRequestRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                    .map(entity -> toResponse(entity, actorUsername, roles))
                    .toList();
        }
        if (roles.contains("student") || roles.contains("parent")) {
            String studentUsername = schoolDataService.resolveAcademicStudentProfile(actorUsername, roles).username();
            return documentRequestRepository.findByStudentUsernameOrderByCreatedAtDescIdDesc(studentUsername).stream()
                    .map(entity -> toResponse(entity, actorUsername, roles))
                    .toList();
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nu ai acces la documente");
    }

    @Transactional
    public DocumentRequestResponse createStudentRequest(String actorUsername, List<String> roles, String type, String purpose) {
        if (!roles.contains("student") && !roles.contains("parent")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doar elevii si parintii pot solicita documente");
        }

        UserProfile student = schoolDataService.resolveAcademicStudentProfile(actorUsername, roles);

        String normalizedType = normalizeType(type);
        String normalizedPurpose = normalizePurpose(purpose);

        DocumentRequestEntity entity = new DocumentRequestEntity();
        entity.setDocumentType(normalizedType);
        entity.setStatus(STATUS_PENDING);
        entity.setRequestedByUsername(actorUsername);
        entity.setStudentUsername(student.username());
        entity.setPurpose(normalizedPurpose);
        entity.setCreatedAt(Instant.now());

        DocumentRequestEntity saved = documentRequestRepository.save(entity);
        notificationService.createNotifications(
                schoolDataService.academicNotificationRecipients(student.username()),
                new NotificationService.NotificationPayload(
                        "Cerere document inregistrata",
                        "Cererea pentru " + labelForType(normalizedType) + " a fost inregistrata pentru elevul " + student.username() + ".",
                        "documents",
                        "/app/documente"
                )
        );
        notificationService.createNotifications(
                reviewerUsernames(),
                new NotificationService.NotificationPayload(
                        "Cerere document noua",
                        "Solicitare noua: " + labelForType(normalizedType) + " pentru elevul " + student.username(),
                        "documents",
                        "/app/documente"
                )
        );
        auditService.record(
                "Solicitare document",
                actorUsername,
                "A fost solicitata emiterea documentului " + labelForType(normalizedType)
        );
        return toResponse(saved, actorUsername, roles);
    }

    @Transactional
    public DocumentRequestResponse approveRequest(Long requestId, String actorUsername, List<String> roles) {
        ensureReviewer(roles);
        DocumentRequestEntity entity = requireRequest(requestId);
        if (!STATUS_PENDING.equals(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cererea nu mai este in asteptare");
        }

        int nextDocumentNumber = nextDocumentNumber(entity.getDocumentType());
        entity.setStatus(STATUS_APPROVED);
        entity.setReviewedAt(Instant.now());
        entity.setReviewedByUsername(actorUsername);
        entity.setSeries(seriesForType(entity.getDocumentType()));
        entity.setDocumentNumber(nextDocumentNumber);
        entity.setSnapshotJson(serializeSnapshot(buildSnapshot(entity)));

        DocumentRequestEntity saved = documentRequestRepository.save(entity);
        notificationService.createNotifications(
                schoolDataService.academicNotificationRecipients(saved.getStudentUsername()),
                new NotificationService.NotificationPayload(
                        "Cerere document procesata",
                        "Cererea pentru " + labelForType(saved.getDocumentType()) + " a fost aprobata.",
                        "documents",
                        "/app/documente"
                )
        );
        auditService.record(
                "Aprobare document",
                actorUsername,
                "A fost aprobata cererea " + saved.getId() + " pentru utilizatorul " + saved.getStudentUsername()
        );
        return toResponse(saved, actorUsername, roles);
    }

    @Transactional
    public DocumentRequestResponse rejectRequest(Long requestId, String actorUsername, List<String> roles, String reason) {
        ensureReviewer(roles);
        DocumentRequestEntity entity = requireRequest(requestId);
        if (!STATUS_PENDING.equals(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cererea nu mai este in asteptare");
        }

        String normalizedReason = normalizeReason(reason);
        entity.setStatus(STATUS_REJECTED);
        entity.setReviewedAt(Instant.now());
        entity.setReviewedByUsername(actorUsername);
        entity.setResolutionNote(normalizedReason);

        DocumentRequestEntity saved = documentRequestRepository.save(entity);
        notificationService.createNotifications(
                schoolDataService.academicNotificationRecipients(saved.getStudentUsername()),
                new NotificationService.NotificationPayload(
                        "Cerere document procesata",
                        "Cererea pentru " + labelForType(saved.getDocumentType()) + " a fost respinsa: " + normalizedReason,
                        "documents",
                        "/app/documente"
                )
        );
        auditService.record(
                "Respingere document",
                actorUsername,
                "A fost respinsa cererea " + saved.getId() + " pentru utilizatorul " + saved.getStudentUsername()
        );
        return toResponse(saved, actorUsername, roles);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadApprovedDocument(Long requestId, String actorUsername, List<String> roles) {
        DocumentRequestEntity entity = requireRequest(requestId);
        if (!STATUS_APPROVED.equals(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Documentul nu este disponibil pentru descarcare");
        }
        if (!canDownload(entity, actorUsername, roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nu ai acces la acest document");
        }

        Map<String, Object> snapshot = deserializeSnapshot(entity.getSnapshotJson());
        byte[] pdf = renderPdf(entity.getDocumentType(), snapshot);
        String downloadFileName = fileName(entity, snapshot);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadFileName + "\"")
                .header("X-Download-Filename", downloadFileName)
                .body(pdf);
    }

    private DocumentRequestEntity requireRequest(Long requestId) {
        return documentRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cererea nu exista"));
    }

    private void ensureReviewer(List<String> roles) {
        if (!canReview(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doar secretariatul si sysadmin-ul pot procesa cereri");
        }
    }

    private boolean canReview(List<String> roles) {
        return roles.contains("secretariat") || roles.contains("sysadmin");
    }

    private boolean canDownload(DocumentRequestEntity entity, String actorUsername, List<String> roles) {
        return canReview(roles)
                || Objects.equals(actorUsername, entity.getStudentUsername())
                || Objects.equals(actorUsername, entity.getRequestedByUsername())
                || (roles.contains("parent") && schoolDataService.isParentOfStudent(actorUsername, entity.getStudentUsername()));
    }

    private String normalizeType(String type) {
        String normalized = type == null ? null : type.trim().toLowerCase(Locale.ROOT);
        if (!TYPE_STUDENT_CERTIFICATE.equals(normalized) && !TYPE_TRANSCRIPT.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipul de document nu este disponibil inca");
        }
        return normalized;
    }

    private String normalizePurpose(String purpose) {
        String normalized = purpose == null ? null : purpose.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scopul este obligatoriu");
        }
        if (normalized.length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scopul poate avea maximum 20 de caractere");
        }
        return normalized;
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? null : reason.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Motivul respingerii este obligatoriu");
        }
        if (normalized.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Motivul respingerii este prea lung");
        }
        return normalized;
    }

    private int nextDocumentNumber(String documentType) {
        return documentRequestRepository.findTopByDocumentTypeAndStatusAndDocumentNumberIsNotNullOrderByDocumentNumberDesc(
                        documentType,
                        STATUS_APPROVED
                )
                .map(entity -> (entity.getDocumentNumber() == null ? 0 : entity.getDocumentNumber()) + 1)
                .orElse(1);
    }

    private List<String> reviewerUsernames() {
        List<String> usernames = new ArrayList<>();
        schoolDataService.getUserProfilesByRole("secretariat").forEach(profile -> usernames.add(profile.username()));
        schoolDataService.getUserProfilesByRole("sysadmin").forEach(profile -> usernames.add(profile.username()));
        return usernames.stream().distinct().toList();
    }

    private String labelForType(String documentType) {
        return switch (documentType) {
            case TYPE_STUDENT_CERTIFICATE -> "Adeverinta de elev";
            case TYPE_TRANSCRIPT -> "Situatie Scolara";
            default -> documentType;
        };
    }

    private String seriesForType(String documentType) {
        return switch (documentType) {
            case TYPE_STUDENT_CERTIFICATE -> STUDENT_CERTIFICATE_SERIES;
            case TYPE_TRANSCRIPT -> TRANSCRIPT_SERIES;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tip document invalid");
        };
    }

    private Map<String, Object> buildSnapshot(DocumentRequestEntity entity) {
        return switch (entity.getDocumentType()) {
            case TYPE_STUDENT_CERTIFICATE -> buildStudentCertificateSnapshot(entity);
            case TYPE_TRANSCRIPT -> buildTranscriptSnapshot(entity);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tip document invalid");
        };
    }

    private Map<String, Object> buildStudentCertificateSnapshot(DocumentRequestEntity entity) {
        UserProfile student = schoolDataService.getProfile(entity.getStudentUsername());
        if (!"student".equals(student.role())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Documentul poate fi emis doar pentru un elev");
        }
        if (student.classId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Elevul nu are clasa setata");
        }
        if (student.cnp() == null || student.cnp().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Elevul nu are CNP setat");
        }

        SchoolClass schoolClass = schoolDataService.getClassById(student.classId());
        Instant issueInstant = entity.getReviewedAt() == null ? Instant.now() : entity.getReviewedAt();
        LocalDate birthDate = birthDateFromCnp(student.cnp());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("inspectorate", SCHOOL_INSPECTORATE);
        snapshot.put("school_name", SCHOOL_NAME);
        snapshot.put("school_address", SCHOOL_ADDRESS);
        snapshot.put("school_phone", SCHOOL_PHONE);
        snapshot.put("school_email", SCHOOL_EMAIL);
        snapshot.put("document_series", entity.getSeries());
        snapshot.put("document_number", formatDocumentNumber(entity.getDocumentNumber()));
        snapshot.put("issue_date", formatDate(issueInstant));
        snapshot.put("student_first_name", student.firstName());
        snapshot.put("student_last_name", student.lastName());
        snapshot.put("student_name", student.lastName() + " " + student.firstName());
        snapshot.put("student_cnp", student.cnp());
        snapshot.put("birth_date", birthDate.format(RO_DATE_FORMAT));
        snapshot.put("school_year", schoolYearFor(issueInstant));
        snapshot.put("class_name", student.className());
        snapshot.put("class_profile", schoolClass.profile());
        snapshot.put("study_form", STUDY_FORM);
        snapshot.put("purpose", entity.getPurpose());
        snapshot.put("verification_code", verificationCode(entity));
        return snapshot;
    }

    private Map<String, Object> buildTranscriptSnapshot(DocumentRequestEntity entity) {
        UserProfile student = schoolDataService.getProfile(entity.getStudentUsername());
        if (!"student".equals(student.role())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Documentul poate fi emis doar pentru un elev");
        }
        if (student.classId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Elevul nu are clasa setata");
        }

        SchoolClass schoolClass = schoolDataService.getClassById(student.classId());
        Instant issueInstant = entity.getReviewedAt() == null ? Instant.now() : entity.getReviewedAt();
        CatalogResponse catalog = catalogService.getCatalogForStudent(student.username(), List.of("student"), student.username());

        List<Map<String, Object>> subjectSnapshots = new ArrayList<>();
        int subjectsWithGrades = 0;
        List<Double> computedAverages = new ArrayList<>();
        for (CatalogSubjectResponse row : catalog.subjects()) {
            List<GradeResponse> grades = row.grades() == null ? List.of() : row.grades();
            if (!grades.isEmpty()) {
                subjectsWithGrades++;
            }
            if (row.average() != null) {
                computedAverages.add(row.average());
            }

            List<String> gradeValues = grades.stream()
                    .map(grade -> String.valueOf(grade.grade_value()))
                    .toList();

            Map<String, Object> subjectSnapshot = new LinkedHashMap<>();
            subjectSnapshot.put("subject_name", row.subject_name());
            subjectSnapshot.put("grade_values", gradeValues);
            subjectSnapshot.put("grade_values_label", gradeValues.isEmpty() ? "-" : String.join(", ", gradeValues));
            subjectSnapshot.put("average", formatAverage(row.average()));
            subjectSnapshots.add(subjectSnapshot);
        }

        Double overallAverage = computedAverages.isEmpty()
                ? null
                : computedAverages.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("inspectorate", SCHOOL_INSPECTORATE);
        snapshot.put("school_name", SCHOOL_NAME);
        snapshot.put("school_address", SCHOOL_ADDRESS);
        snapshot.put("school_phone", SCHOOL_PHONE);
        snapshot.put("school_email", SCHOOL_EMAIL);
        snapshot.put("document_series", entity.getSeries());
        snapshot.put("document_number", formatDocumentNumber(entity.getDocumentNumber()));
        snapshot.put("issue_date", formatDate(issueInstant));
        snapshot.put("student_first_name", student.firstName());
        snapshot.put("student_last_name", student.lastName());
        snapshot.put("student_name", student.lastName() + " " + student.firstName());
        snapshot.put("class_name", student.className());
        snapshot.put("class_profile", schoolClass.profile());
        snapshot.put("school_year", schoolYearFor(issueInstant));
        snapshot.put("purpose", entity.getPurpose());
        snapshot.put("subject_count", subjectSnapshots.size());
        snapshot.put("subjects_with_grades", subjectsWithGrades);
        snapshot.put("overall_average", formatAverage(overallAverage));
        snapshot.put("subjects", subjectSnapshots);
        snapshot.put("verification_code", verificationCode(entity));
        return snapshot;
    }

    private String serializeSnapshot(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Nu s-a putut salva continutul documentului");
        }
    }

    private Map<String, Object> deserializeSnapshot(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Documentul nu are continut salvat");
        }
        try {
            return objectMapper.readValue(snapshotJson, new TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Nu s-a putut citi continutul documentului");
        }
    }

    private byte[] renderPdf(String documentType, Map<String, Object> snapshot) {
        String template = switch (documentType) {
            case TYPE_STUDENT_CERTIFICATE -> readTemplate(STUDENT_CERTIFICATE_TEMPLATE);
            case TYPE_TRANSCRIPT -> readTemplate(TRANSCRIPT_TEMPLATE);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipul de document nu este disponibil");
        };
        String html = switch (documentType) {
            case TYPE_TRANSCRIPT -> applyTemplate(template, snapshot, Map.of("subjects_rows", transcriptRows(snapshot)));
            default -> applyTemplate(template, snapshot, Map.of());
        };

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Nu s-a putut genera PDF-ul");
        }
    }

    private String readTemplate(String resourcePath) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(resourcePath).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Nu s-a putut incarca sablonul documentului");
        }
    }

    private String applyTemplate(String template, Map<String, Object> snapshot, Map<String, String> rawValues) {
        String html = template;
        for (Map.Entry<String, String> entry : rawValues.entrySet()) {
            html = html.replace("{{ " + entry.getKey() + " }}", entry.getValue());
            html = html.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            String value = escapeHtml(entry.getValue());
            html = html.replace("{{ " + entry.getKey() + " }}", value);
            html = html.replace("{{" + entry.getKey() + "}}", value);
        }
        return html;
    }

    private String transcriptRows(Map<String, Object> snapshot) {
        List<Map<String, Object>> subjects = objectMapper.convertValue(
                snapshot.getOrDefault("subjects", List.of()),
                new TypeReference<>() {
                }
        );

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < subjects.size(); index++) {
            Map<String, Object> row = subjects.get(index);
            builder.append("<tr>");
            builder.append("<td>").append(index + 1).append("</td>");
            builder.append("<td>").append(escapeHtml(row.get("subject_name"))).append("</td>");
            builder.append("<td>").append(escapeHtml(row.get("grade_values_label"))).append("</td>");
            builder.append("<td>").append(escapeHtml(row.get("average"))).append("</td>");
            builder.append("</tr>");
        }
        builder.append("<tr>");
        builder.append("<td colspan=\"3\"><strong>Media totala</strong></td>");
        builder.append("<td><strong>").append(escapeHtml(snapshot.get("overall_average"))).append("</strong></td>");
        builder.append("</tr>");
        return builder.toString();
    }

    private String escapeHtml(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private LocalDate birthDateFromCnp(String cnp) {
        if (cnp == null || !cnp.matches("\\d{13}")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Elevul nu are un CNP valid");
        }

        int sexDigit = Character.getNumericValue(cnp.charAt(0));
        int century = switch (sexDigit) {
            case 1, 2 -> 1900;
            case 3, 4 -> 1800;
            case 5, 6, 7, 8 -> 2000;
            default -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Elevul nu are un CNP valid");
        };

        int year = century + Integer.parseInt(cnp.substring(1, 3));
        int month = Integer.parseInt(cnp.substring(3, 5));
        int day = Integer.parseInt(cnp.substring(5, 7));
        return LocalDate.of(year, month, day);
    }

    private String formatDate(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDate().format(RO_DATE_FORMAT);
    }

    private String schoolYearFor(Instant instant) {
        LocalDate localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate();
        int startYear = localDate.getMonthValue() >= 9 ? localDate.getYear() : localDate.getYear() - 1;
        return startYear + "-" + (startYear + 1);
    }

    private String formatDocumentNumber(Integer documentNumber) {
        int safeNumber = documentNumber == null ? 0 : documentNumber;
        return String.format(Locale.ROOT, "%05d", safeNumber);
    }

    private String verificationCode(DocumentRequestEntity entity) {
        return seriesForType(entity.getDocumentType()) + "-" + formatDocumentNumber(entity.getDocumentNumber()) + "-" + entity.getId();
    }

    private String fileName(DocumentRequestEntity entity, Map<String, Object> snapshot) {
        String studentPrefix = studentPrefix(snapshot, entity.getStudentUsername());
        return switch (entity.getDocumentType()) {
            case TYPE_STUDENT_CERTIFICATE -> studentPrefix + "_Adeverinta_Student.pdf";
            case TYPE_TRANSCRIPT -> studentPrefix + "_Situatie_Scolara.pdf";
            default -> "document.pdf";
        };
    }

    private String studentPrefix(Map<String, Object> snapshot, String studentUsername) {
        String lastName = normalizeFileToken(snapshot.get("student_last_name"));
        String firstName = normalizeFileToken(snapshot.get("student_first_name"));
        if (lastName != null && firstName != null) {
            return lastName + "_" + firstName;
        }

        String fullName = snapshot.get("student_name") == null ? null : String.valueOf(snapshot.get("student_name")).trim();
        if (fullName != null && !fullName.isBlank()) {
            String[] parts = fullName.split("\\s+", 2);
            String fallbackLastName = normalizeFileToken(parts[0]);
            String fallbackFirstName = normalizeFileToken(parts.length > 1 ? parts[1] : parts[0]);
            if (fallbackLastName != null && fallbackFirstName != null) {
                return fallbackLastName + "_" + fallbackFirstName;
            }
        }

        UserProfile student = schoolDataService.getProfile(studentUsername);
        return sanitizeFileToken(student.lastName()) + "_" + sanitizeFileToken(student.firstName());
    }

    private String normalizeFileToken(Object value) {
        String sanitized = sanitizeFileToken(value);
        return "Document".equals(sanitized) ? null : sanitized;
    }

    private String sanitizeFileToken(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return normalized.isBlank() ? "Document" : normalized;
    }

    private String formatAverage(Double value) {
        if (value == null) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private DocumentRequestResponse toResponse(DocumentRequestEntity entity, String actorUsername, List<String> roles) {
        return new DocumentRequestResponse(
                entity.getId(),
                entity.getDocumentType(),
                labelForType(entity.getDocumentType()),
                entity.getStatus(),
                entity.getPurpose(),
                entity.getSeries(),
                entity.getDocumentNumber(),
                entity.getStudentUsername(),
                entity.getRequestedByUsername(),
                entity.getReviewedByUsername(),
                entity.getResolutionNote(),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString(),
                entity.getReviewedAt() == null ? null : entity.getReviewedAt().toString(),
                canReview(roles) && STATUS_PENDING.equals(entity.getStatus()),
                STATUS_APPROVED.equals(entity.getStatus()) && canDownload(entity, actorUsername, roles)
        );
    }
}
