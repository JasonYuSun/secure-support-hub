-- V3: Normalize demo account passwords to "password"

UPDATE users
SET password = '$2y$12$kpzMLPvxWR6cfdOe5gjfyevFvjhrYOu3TGDk1/B7woxE2eszPPzgG'
WHERE email IN ('user@example.com', 'triage@example.com', 'admin@example.com');
