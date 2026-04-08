package ro.timetable.reference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ro.timetable.common.util.PersistentStateService;
import ro.timetable.reference.model.SchoolClass;

class SchoolDataServiceTest {

    @Test
    void generateTimetablesKeepsProfileSubjectsOutOfFirstSlot() {
        CurriculumPlanService curriculumPlanService = new CurriculumPlanService(new ObjectMapper());
        PersistentStateService persistentStateService = mock(PersistentStateService.class);
        ReferenceDataPersistenceService referenceDataPersistenceService = mock(ReferenceDataPersistenceService.class);

        when(referenceDataPersistenceService.hasReferenceData()).thenReturn(false);
        when(persistentStateService.loadTimetableEntries()).thenReturn(List.of());

        SchoolDataService service = new SchoolDataService(
                curriculumPlanService,
                persistentStateService,
                referenceDataPersistenceService
        );

        service.init();

        assertThat(service.getUserProfilesByRole("professor"))
                .hasSize(21);

        for (SchoolClass schoolClass : service.getClasses()) {
            service.generateTimetable(schoolClass.id());
        }

        for (SchoolClass schoolClass : service.getClasses()) {
            Set<String> profileSubjects = profileSubjectsFor(schoolClass.profile());
            assertThat(service.getTimetableForClass(schoolClass.id()))
                    .allMatch(entry -> !profileSubjects.contains(entry.subjectName()) || entry.indexInDay() > 1,
                            schoolClass.name() + " should keep profile-heavy subjects away from the first slot");
        }
    }

    private Set<String> profileSubjectsFor(String profile) {
        return switch (profile) {
            case "Filologie" -> Set.of(
                    "Limba si literatura romana",
                    "Istorie",
                    "Geografie"
            );
            case "Matematica-Informatica", "Matematica-Informatica Intensiv" -> Set.of(
                    "Limba si literatura romana",
                    "Matematica",
                    "Fizica",
                    "Informatica",
                    "Informatica intensiv"
            );
            default -> Set.of();
        };
    }
}
