-- Retain registrations and credential hashes; remove tenant from OAuth identity metadata.
UPDATE oauth2_registered_client
SET client_settings = (
    (client_settings::jsonb - 'pay.service.tenant' - 'pay.service.enabled' - 'pay.service.audience')
    || CASE WHEN client_settings::jsonb ? 'pay.service.enabled'
         THEN jsonb_build_object('loadup.oauth.enabled', client_settings::jsonb -> 'pay.service.enabled')
         ELSE '{}'::jsonb END
    || CASE WHEN client_settings::jsonb ? 'pay.service.audience'
         THEN jsonb_build_object('loadup.oauth.audience', client_settings::jsonb -> 'pay.service.audience')
         ELSE '{}'::jsonb END
)::text
WHERE client_settings::jsonb ?| ARRAY['pay.service.tenant', 'pay.service.enabled', 'pay.service.audience'];
