package ro.timetable.reference.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.common.dto.ApiDtos.CurriculumProfileSummaryResponse;

@Service
public class CurriculumPlanService {

    private record PlanEntry(String materie, Integer ore_pe_saptamana) {
    }

    private final Map<String, Map<String, LinkedHashMap<String, Integer>>> plans;

    public CurriculumPlanService(ObjectMapper objectMapper) {
        this.plans = loadPlans(objectMapper);
    }

    public LinkedHashMap<String, Integer> hoursForClass(String className, String profile) {
        String level = classLevel(className);
        Map<String, LinkedHashMap<String, Integer>> profilePlan = plans.get(profile);
        if (profilePlan == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing curriculum profile " + profile);
        }
        LinkedHashMap<String, Integer> levelPlan = profilePlan.get(level);
        if (levelPlan == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing curriculum level " + level + " for profile " + profile);
        }
        return new LinkedHashMap<>(levelPlan);
    }

    public List<String> allSubjects() {
        Set<String> subjects = new LinkedHashSet<>();
        for (Map<String, LinkedHashMap<String, Integer>> profilePlan : plans.values()) {
            for (LinkedHashMap<String, Integer> levelPlan : profilePlan.values()) {
                subjects.addAll(levelPlan.keySet());
            }
        }
        return new ArrayList<>(subjects);
    }

    public int weeklyHoursForSubject(String className, String profile, String subjectName) {
        return hoursForClass(className, profile).getOrDefault(subjectName, 0);
    }

    public List<CurriculumProfileSummaryResponse> profileSummaries() {
        return plans.entrySet().stream()
                .map(entry -> toSummary(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Map<String, Map<String, LinkedHashMap<String, Integer>>> loadPlans(ObjectMapper objectMapper) {
        try (InputStream inputStream = new ClassPathResource("curriculum-plan.json").getInputStream()) {
            TypeReference<LinkedHashMap<String, LinkedHashMap<String, List<PlanEntry>>>> type = new TypeReference<>() {};
            LinkedHashMap<String, LinkedHashMap<String, List<PlanEntry>>> raw = objectMapper.readValue(inputStream, type);
            LinkedHashMap<String, Map<String, LinkedHashMap<String, Integer>>> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, LinkedHashMap<String, List<PlanEntry>>> profileEntry : raw.entrySet()) {
                LinkedHashMap<String, LinkedHashMap<String, Integer>> byLevel = new LinkedHashMap<>();
                for (Map.Entry<String, List<PlanEntry>> levelEntry : profileEntry.getValue().entrySet()) {
                    LinkedHashMap<String, Integer> subjects = new LinkedHashMap<>();
                    for (PlanEntry entry : levelEntry.getValue()) {
                        subjects.put(entry.materie(), entry.ore_pe_saptamana());
                    }
                    byLevel.put(levelEntry.getKey().toUpperCase(Locale.ROOT), subjects);
                }
                normalized.put(profileEntry.getKey(), byLevel);
            }
            return normalized;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to load curriculum plan");
        }
    }

    private String classLevel(String className) {
        if (className == null || className.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Class name is required");
        }
        return className.trim().split("\\s+")[0].toUpperCase(Locale.ROOT);
    }

    private CurriculumProfileSummaryResponse toSummary(String profileName, Map<String, LinkedHashMap<String, Integer>> profilePlan) {
        LinkedHashMap<String, Integer> totalWeeklyHoursByLevel = new LinkedHashMap<>();
        Map<String, Integer> totalsBySubject = new LinkedHashMap<>();

        profilePlan.forEach((level, subjects) -> {
            int total = subjects.values().stream().mapToInt(Integer::intValue).sum();
            totalWeeklyHoursByLevel.put(level, total);
            subjects.forEach((subjectName, hours) -> totalsBySubject.merge(subjectName, hours, Integer::sum));
        });

        List<String> representativeSubjects = totalsBySubject.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(4)
                .map(Map.Entry::getKey)
                .toList();

        String levelHours = totalWeeklyHoursByLevel.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue() + " ore")
                .reduce((left, right) -> left + ", " + right)
                .orElse("distributie indisponibila");

        String summary = profileName + " are " + totalWeeklyHoursByLevel.getOrDefault("IX", 0)
                + " ore pe saptamana in clasa IX si pune accent pe "
                + String.join(", ", representativeSubjects.stream().limit(3).toList()) + ".";

        List<String> details = List.of(
                "Ore pe nivel: " + levelHours + ".",
                "Materii reprezentative: " + String.join(", ", representativeSubjects) + ".",
                "Planul este extras direct din curriculumul configurat pentru profilul " + profileName + "."
        );

        return new CurriculumProfileSummaryResponse(
                profileName,
                new ArrayList<>(profilePlan.keySet()),
                totalWeeklyHoursByLevel,
                representativeSubjects,
                summary,
                details,
                accentForProfile(profileName)
        );
    }

    private String accentForProfile(String profileName) {
        return switch (profileName) {
            case "Filologie" -> "amber";
            case "Matematica-Informatica" -> "teal";
            case "Matematica-Informatica Intensiv" -> "slate";
            default -> "teal";
        };
    }
}
