-- V2: Seed default roles and demo users

INSERT INTO roles (name) VALUES ('USER'), ('TRIAGE'), ('ADMIN')
ON CONFLICT (name) DO NOTHING;

-- Password for all demo users is 'password' (BCrypt encoded)
INSERT INTO users (username, email, password) VALUES
('user',   'user@example.com',   '$2a$12$6AjSQ5LH6Ly0EIPdpVFSGe3d9GXqQAWf0YPWYvlWl4TQHVJ4YyVme'),
('triage', 'triage@example.com', '$2a$12$6AjSQ5LH6Ly0EIPdpVFSGe3d9GXqQAWf0YPWYvlWl4TQHVJ4YyVme'),
('admin',  'admin@example.com',  '$2a$12$6AjSQ5LH6Ly0EIPdpVFSGe3d9GXqQAWf0YPWYvlWl4TQHVJ4YyVme')
ON CONFLICT (email) DO NOTHING;

-- Assign roles
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'user@example.com'   AND r.name = 'USER'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'triage@example.com' AND r.name IN ('USER', 'TRIAGE')
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@example.com'  AND r.name IN ('USER', 'TRIAGE', 'ADMIN')
ON CONFLICT DO NOTHING;
