package ro.timetable.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import ro.timetable.common.dto.ApiDtos.NotificationResponse;
import ro.timetable.notifications.entity.NotificationEntity;
import ro.timetable.notifications.repository.NotificationRepository;
import ro.timetable.reference.entity.UserProfileEntity;
import ro.timetable.reference.entity.UserProfileSettingsEntity;
import ro.timetable.reference.repository.UserProfileRepository;

class NotificationServiceTest {

    @Test
    void sendToUserHonorsDisabledNotificationChannels() {
        MailService mailService = mock(MailService.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        UserProfileRepository userProfileRepository = mock(UserProfileRepository.class);
        NotificationService service = new NotificationService(mailService, notificationRepository, userProfileRepository);
        UserProfileEntity profile = userProfile("student001", false, false);

        when(userProfileRepository.findByUsername("student001")).thenReturn(Optional.of(profile));

        NotificationResponse response = service.sendToUser(
                "student001",
                new NotificationService.NotificationPayload("Catalog", "Nota noua", "catalog", "/app/catalog")
        );

        assertThat(response).isNull();
        verify(notificationRepository, never()).save(any(NotificationEntity.class));
        verify(mailService, never()).sendNotificationEmailBestEffort(any(), any(), any());
    }

    @Test
    void sendToUserPersistsInAppAndSendsEmailWhenEnabled() {
        MailService mailService = mock(MailService.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        UserProfileRepository userProfileRepository = mock(UserProfileRepository.class);
        NotificationService service = new NotificationService(mailService, notificationRepository, userProfileRepository);
        UserProfileEntity profile = userProfile("student001", true, true);

        when(userProfileRepository.findByUsername("student001")).thenReturn(Optional.of(profile));
        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(invocation -> {
            NotificationEntity entity = invocation.getArgument(0);
            entity.setId(42L);
            return entity;
        });

        NotificationResponse response = service.sendToUser(
                "student001",
                new NotificationService.NotificationPayload("Catalog", "Nota noua", "catalog", "/app/catalog")
        );

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.title()).isEqualTo("Catalog");
        assertThat(response.category()).isEqualTo("catalog");
        assertThat(response.action_path()).isEqualTo("/app/catalog");
        verify(notificationRepository).save(any(NotificationEntity.class));
        verify(mailService).sendNotificationEmailBestEffort(
                "student001@timetable.local",
                "Ana Ionescu",
                "Catalog: Nota noua"
        );
    }

    private UserProfileEntity userProfile(String username, boolean emailEnabled, boolean inAppEnabled) {
        UserProfileEntity profile = new UserProfileEntity();
        profile.setId(1L);
        profile.setUsername(username);
        profile.setFirstName("Ana");
        profile.setLastName("Ionescu");
        profile.setEmail(username + "@timetable.local");

        UserProfileSettingsEntity settings = new UserProfileSettingsEntity();
        settings.setEmailNotificationsEnabled(emailEnabled);
        settings.setInAppNotificationsEnabled(inAppEnabled);
        profile.setSettings(settings);
        return profile;
    }
}
