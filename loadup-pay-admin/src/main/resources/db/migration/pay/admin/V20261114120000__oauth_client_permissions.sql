-- Defines independently grantable capabilities; no roles receive them automatically.
INSERT INTO upms_permission (id, permission_name, permission_code, permission_type, resource_path, http_method,
                             created_at, updated_at, deleted)
VALUES ('oauth-client-create', 'Create OAuth clients', 'oauth:client:create', 3,
        '/api/oauth/clients/{registrationId}/create', 'POST', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
       ('oauth-client-read', 'Read OAuth client metadata', 'oauth:client:read', 3,
        '/api/oauth/clients/{registrationId}/detail', 'POST', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
       ('oauth-client-authorize', 'Modify OAuth client authorization', 'oauth:client:authorize', 3,
        '/api/oauth/clients/{registrationId}/authorization', 'POST', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
       ('oauth-client-disable', 'Disable OAuth clients', 'oauth:client:disable', 3,
        '/api/oauth/clients/{registrationId}/disable', 'POST', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
       ('oauth-client-expire', 'Set OAuth client credential expiry', 'oauth:client:expire', 3,
        '/api/oauth/clients/{registrationId}/expiry', 'POST', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
       ('oauth-client-rotate', 'Rotate OAuth client credentials', 'oauth:client:rotate', 3,
        '/api/oauth/clients/{registrationId}/rotate', 'POST', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT
    (permission_code)
    WHERE deleted = FALSE DO NOTHING;
