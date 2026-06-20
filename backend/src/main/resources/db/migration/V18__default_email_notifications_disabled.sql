-- Email notifications are now opt-in.
-- Every existing user is set to email-disabled; users must explicitly enable
-- email notifications from their profile settings for any email to be sent.

ALTER TABLE IF EXISTS user_profile_settings
    ALTER COLUMN email_notifications_enabled SET DEFAULT FALSE;

UPDATE user_profile_settings
SET email_notifications_enabled = FALSE;
