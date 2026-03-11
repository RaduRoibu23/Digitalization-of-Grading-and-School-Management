package ro.timetable.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
}
