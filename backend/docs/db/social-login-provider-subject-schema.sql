-- Allow social members to exist without provider email.
ALTER TABLE member
    ALTER COLUMN email DROP NOT NULL;

-- Provider identity is keyed by provider + provider_subject.
-- Email at link time is no longer part of the social identity model.
ALTER TABLE external_identity
    DROP COLUMN IF EXISTS email_at_link_time;
