-- ---------------------------------------------------------------------------
-- AgentOps Firewall — local-only demo user seed.
--
-- WARNING: These users are intentional demo credentials for the LOCAL
-- DEVELOPMENT environment only. The cleartext passwords are publicly
-- documented in README.md and .env.example (admin/admin123,
-- reviewer/reviewer123, viewer/viewer123). Spring's BCryptPasswordEncoder
-- accepts the $2b$ variant produced below.
--
-- Do NOT extend this migration with real user accounts and do NOT apply
-- this migration against any non-development database.
--
-- The migration uses ON CONFLICT DO NOTHING so it is safe to re-run.
-- ---------------------------------------------------------------------------

insert into users (id, username, password_hash, role, status, created_at, updated_at, version)
values
    ('00000000-0000-0000-0000-000000000001',
     'admin',
     '$2b$10$QHqNa6GH40YuhMR01DCbv./BzmP4KrROwhtZJDyJMX3nXeaZ5NkVO',
     'ADMIN',
     'ACTIVE',
     now(),
     now(),
     0),
    ('00000000-0000-0000-0000-000000000002',
     'reviewer',
     '$2b$10$JVhaQ3IjfS43xUxOhX4yCOSX1cYYb2W6UdzLRqEzdl4W0njLr8eOm',
     'REVIEWER',
     'ACTIVE',
     now(),
     now(),
     0),
    ('00000000-0000-0000-0000-000000000003',
     'viewer',
     '$2b$10$6hzjnUfRmikEvVDkEXQFy.sI.lrJYpRdnrid7n.e.WPebEh8dKf0S',
     'VIEWER',
     'ACTIVE',
     now(),
     now(),
     0)
on conflict (username) do nothing;
