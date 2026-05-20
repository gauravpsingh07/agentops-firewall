-- ---------------------------------------------------------------------------
-- AgentOps Firewall — seed sample policies.
-- These ten policies mirror the documented sample policy set and give the
-- firewall a useful baseline behaviour out of the box. All rows are
-- idempotent (ON CONFLICT DO NOTHING) and clearly use predictable UUIDs
-- in the 10000000-/20000000- namespace so downstream tests and the
-- dashboard can reference them stably.
--
-- Documented limitations (the demo intentionally does not model these):
--   * Policy #2 currently denies READ_SECRET unconditionally. A real
--     implementation would scope the deny to "non-admin agents" once the
--     agent-role concept exists.
--   * Policy #9 currently requires approval for WRITE_DATABASE for any
--     agent. Disabled agents are also blocked by the ingestion layer
--     before policy evaluation, so the "disabled agents" sub-condition
--     from the spec is enforced one layer up.
-- ---------------------------------------------------------------------------

insert into policies (id, name, description, priority, effect, enabled,
                      action_type, resource_pattern, min_risk_level,
                      created_at, updated_at, version)
values
    -- 1. Allow LOW-risk actions (lowest priority so any specific rule wins first).
    ('10000000-0000-0000-0000-000000000001',
     'Allow LOW-risk actions',
     'Catch-all: actions tagged as LOW risk are allowed unless a higher-priority policy overrides.',
     10, 'ALLOW', true,
     null, null, null,
     now(), now(), 0),

    -- 2. Deny READ_SECRET.
    ('10000000-0000-0000-0000-000000000002',
     'Deny READ_SECRET',
     'Never permit agents to read secrets. Demo limitation: scoping to "non-admin agents" requires the agent-role concept which is not yet modeled.',
     100, 'DENY', true,
     'READ_SECRET', null, null,
     now(), now(), 0),

    -- 3. Require approval for DELETE_FILE.
    ('10000000-0000-0000-0000-000000000003',
     'Require approval for DELETE_FILE',
     'Any file deletion goes through human-in-the-loop review.',
     80, 'NEEDS_APPROVAL', true,
     'DELETE_FILE', null, null,
     now(), now(), 0),

    -- 4. Require approval for DEPLOY_CODE.
    ('10000000-0000-0000-0000-000000000004',
     'Require approval for DEPLOY_CODE',
     'Deployments must be approved by a human reviewer.',
     80, 'NEEDS_APPROVAL', true,
     'DEPLOY_CODE', null, null,
     now(), now(), 0),

    -- 5. Deny CALL_EXTERNAL_API to non-allowlisted domains.
    ('10000000-0000-0000-0000-000000000005',
     'Deny non-allowlisted external API calls',
     'External API calls are denied unless the target domain is on the allowlist.',
     90, 'DENY', true,
     'CALL_EXTERNAL_API', null, null,
     now(), now(), 0),

    -- 6. Require approval for SEND_EMAIL with external attachment.
    ('10000000-0000-0000-0000-000000000006',
     'Require approval for external email with attachment',
     'Outbound email with an attachment to a recipient outside the internal allowlist requires approval.',
     70, 'NEEDS_APPROVAL', true,
     'SEND_EMAIL', null, null,
     now(), now(), 0),

    -- 7. Deny ACCESS_CUSTOMER_DATA at HIGH or CRITICAL risk.
    ('10000000-0000-0000-0000-000000000007',
     'Deny customer-data access at HIGH risk',
     'Customer data access at HIGH or CRITICAL risk is never permitted.',
     95, 'DENY', true,
     'ACCESS_CUSTOMER_DATA', null, 'HIGH',
     now(), now(), 0),

    -- 8. Require approval for non-allowlisted terminal commands.
    ('10000000-0000-0000-0000-000000000008',
     'Require approval for non-allowlisted terminal commands',
     'Terminal commands are auto-allowed only when the command type is on the safe allowlist.',
     85, 'NEEDS_APPROVAL', true,
     'RUN_TERMINAL_COMMAND', null, null,
     now(), now(), 0),

    -- 9. Require approval for WRITE_DATABASE.
    ('10000000-0000-0000-0000-000000000009',
     'Require approval for WRITE_DATABASE',
     'Any write to the database goes through review. The "disabled agents" half of the spec is enforced by the ingestion layer (agents in DISABLED/DELETED/ROTATED status cannot submit actions).',
     85, 'NEEDS_APPROVAL', true,
     'WRITE_DATABASE', null, null,
     now(), now(), 0),

    -- 10. Require approval for CREATE_GITHUB_PR touching infra.
    ('10000000-0000-0000-0000-00000000000a',
     'Require approval for infrastructure GitHub PRs',
     'GitHub PRs that touch infrastructure files require approval.',
     80, 'NEEDS_APPROVAL', true,
     'CREATE_GITHUB_PR', null, null,
     now(), now(), 0)
on conflict (id) do nothing;

-- Conditions ----------------------------------------------------------------
-- Policy 5: target domain must be in the allowlist (so NOT_IN means non-match).
insert into policy_conditions (id, policy_id, field, operator, condition_value, created_at, updated_at, version)
values
    ('20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000005',
     'metadata.targetDomain',
     'NOT_IN',
     '["acme.com","internal","trusted-partner.com"]',
     now(), now(), 0)
on conflict (id) do nothing;

-- Policy 6: containsAttachment=true AND recipientDomain is external.
insert into policy_conditions (id, policy_id, field, operator, condition_value, created_at, updated_at, version)
values
    ('20000000-0000-0000-0000-000000000002',
     '10000000-0000-0000-0000-000000000006',
     'metadata.containsAttachment',
     'EQUALS',
     'true',
     now(), now(), 0),
    ('20000000-0000-0000-0000-000000000003',
     '10000000-0000-0000-0000-000000000006',
     'metadata.recipientDomain',
     'NOT_IN',
     '["acme.com","internal"]',
     now(), now(), 0)
on conflict (id) do nothing;

-- Policy 8: commandType must be in the safe allowlist; otherwise approval.
insert into policy_conditions (id, policy_id, field, operator, condition_value, created_at, updated_at, version)
values
    ('20000000-0000-0000-0000-000000000004',
     '10000000-0000-0000-0000-000000000008',
     'metadata.commandType',
     'NOT_IN',
     '["ls","echo","pwd","cat"]',
     now(), now(), 0)
on conflict (id) do nothing;

-- Policy 10: infrastructure flag must be true to trigger approval.
insert into policy_conditions (id, policy_id, field, operator, condition_value, created_at, updated_at, version)
values
    ('20000000-0000-0000-0000-000000000005',
     '10000000-0000-0000-0000-00000000000a',
     'metadata.modifiesInfrastructure',
     'EQUALS',
     'true',
     now(), now(), 0)
on conflict (id) do nothing;

-- Policy 1: scope "Allow LOW-risk actions" to exactly riskLevel=LOW. Without
-- this condition the empty rule would match every risk level and silently
-- allow everything that no higher-priority rule blocked.
insert into policy_conditions (id, policy_id, field, operator, condition_value, created_at, updated_at, version)
values
    ('20000000-0000-0000-0000-000000000006',
     '10000000-0000-0000-0000-000000000001',
     'riskLevel',
     'EQUALS',
     'LOW',
     now(), now(), 0)
on conflict (id) do nothing;