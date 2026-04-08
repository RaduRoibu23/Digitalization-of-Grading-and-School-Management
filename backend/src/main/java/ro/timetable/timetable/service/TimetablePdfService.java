package ro.timetable.timetable.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.timetable.model.TimetableEntry;

@Service
public class TimetablePdfService {

    private static final List<Integer> WEEKDAYS = List.of(1, 2, 3, 4, 5);
    private static final Map<Integer, String> WEEKDAY_LABELS = Map.of(
            1, "Luni",
            2, "Marti",
            3, "Miercuri",
            4, "Joi",
            5, "Vineri"
    );
    private static final Map<Integer, String> TIME_LABELS = Map.of(
            1, "08:00-08:50",
            2, "09:00-09:50",
            3, "10:00-10:50",
            4, "11:00-11:50",
            5, "12:00-12:50",
            6, "13:00-13:50",
            7, "14:00-14:50"
    );

    public ResponseEntity<byte[]> renderClassTimetablePdf(String className, List<TimetableEntry> entries) {
        String safeClassName = (className == null || className.isBlank()) ? "clasa" : className;
        String title = "Orar - " + safeClassName;
        String subtitle = "Export PDF pentru orarul clasei";
        String fileName = slugify("orar_" + safeClassName) + ".pdf";
        return buildPdfResponse(title, subtitle, entries, false, fileName);
    }

    public ResponseEntity<byte[]> renderTeacherTimetablePdf(String teacherName, List<TimetableEntry> entries) {
        String safeTeacherName = (teacherName == null || teacherName.isBlank()) ? "profesor" : teacherName;
        String title = "Orar profesor - " + safeTeacherName;
        String subtitle = "Export PDF pentru programul saptamanal";
        String fileName = slugify("orar_profesor_" + safeTeacherName) + ".pdf";
        return buildPdfResponse(title, subtitle, entries, true, fileName);
    }

    private ResponseEntity<byte[]> buildPdfResponse(
            String title,
            String subtitle,
            List<TimetableEntry> entries,
            boolean teacherView,
            String fileName
    ) {
        byte[] pdf = renderPdf(title, subtitle, entries, teacherView);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header("X-Download-Filename", fileName)
                .body(pdf);
    }

    private byte[] renderPdf(String title, String subtitle, List<TimetableEntry> entries, boolean teacherView) {
        String html = buildHtml(title, subtitle, entries, teacherView);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.useFastMode();
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Nu s-a putut genera PDF-ul pentru orar");
        }
    }

    private String buildHtml(String title, String subtitle, List<TimetableEntry> entries, boolean teacherView) {
        Map<String, TimetableEntry> entriesBySlot = new LinkedHashMap<>();
        int maxSlot = 7;
        for (TimetableEntry entry : entries.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TimetableEntry::weekday).thenComparing(TimetableEntry::indexInDay))
                .toList()) {
            entriesBySlot.put(slotKey(entry.weekday(), entry.indexInDay()), entry);
            maxSlot = Math.max(maxSlot, entry.indexInDay() == null ? 7 : entry.indexInDay());
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\" />");
        html.append("<style>");
        html.append("""
                @page { size: A4 landscape; margin: 18mm 14mm; }
                body { font-family: Arial, sans-serif; color: #10233a; font-size: 11px; }
                h1 { margin: 0 0 8px; font-size: 22px; }
                .subtitle { margin: 0 0 18px; color: #4c627b; font-size: 12px; }
                table { width: 100%; border-collapse: collapse; table-layout: fixed; }
                th, td { border: 1px solid #b8c7d9; padding: 8px; vertical-align: top; }
                th { background: #dfeaf6; font-weight: 700; text-align: center; }
                .time-cell { width: 118px; background: #eef4fb; font-weight: 700; }
                .time-cell small { display: block; margin-top: 4px; color: #5c728a; font-weight: 400; }
                .entry { min-height: 64px; }
                .entry strong { display: block; margin-bottom: 6px; font-size: 11.5px; }
                .meta { display: block; margin-top: 3px; color: #40566f; }
                .empty { color: #91a2b6; text-align: center; }
                """);
        html.append("</style></head><body>");
        html.append("<h1>").append(escapeHtml(title)).append("</h1>");
        html.append("<p class=\"subtitle\">").append(escapeHtml(subtitle)).append("</p>");
        html.append("<table><thead><tr><th>Interval</th>");
        for (int weekday : WEEKDAYS) {
            html.append("<th>").append(escapeHtml(WEEKDAY_LABELS.get(weekday))).append("</th>");
        }
        html.append("</tr></thead><tbody>");

        for (int slot = 1; slot <= maxSlot; slot++) {
            html.append("<tr>");
            html.append("<td class=\"time-cell\">Ora ").append(slot)
                    .append("<small>").append(escapeHtml(TIME_LABELS.getOrDefault(slot, "Slot " + slot))).append("</small></td>");
            for (int weekday : WEEKDAYS) {
                TimetableEntry entry = entriesBySlot.get(slotKey(weekday, slot));
                if (entry == null) {
                    html.append("<td class=\"empty\">-</td>");
                    continue;
                }
                html.append("<td><div class=\"entry\">");
                html.append("<strong>").append(escapeHtml(entry.subjectName())).append("</strong>");
                if (teacherView) {
                    html.append("<span class=\"meta\">").append(escapeHtml(entry.className())).append("</span>");
                    html.append("<span class=\"meta\">").append(escapeHtml(entry.roomName())).append("</span>");
                } else {
                    html.append("<span class=\"meta\">").append(escapeHtml(entry.teacherName())).append("</span>");
                    html.append("<span class=\"meta\">").append(escapeHtml(entry.roomName())).append("</span>");
                }
                html.append("</div></td>");
            }
            html.append("</tr>");
        }

        html.append("</tbody></table></body></html>");
        return html.toString();
    }

    private String slotKey(Integer weekday, Integer indexInDay) {
        return weekday + "-" + indexInDay;
    }

    private String escapeHtml(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String slugify(String value) {
        return value == null
                ? "orar"
                : value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "_")
                        .replaceAll("^_+|_+$", "");
    }
}
