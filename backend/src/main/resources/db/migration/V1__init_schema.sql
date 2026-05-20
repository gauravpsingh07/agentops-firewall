-- ---------------------------------------------------------------------------
-- AgentOps Firewall — initial schema.
-- Tables mirror the JPA entities under com.agentops.firewall.*. JSON-shaped
-- fields are stored as TEXT for now; a later migration can promote them to
-- JSONB once the policy engine needs to query into them.
-- ---------------------------------------------------------------------------

create table users (
    id              uuid                        primary key,
    username        varchar(80)                 not null unique,
    password_hash   varchar(255)                not null,
    role            varchar(20)                 not null,
    status          varchar(20)                 not null default 'ACTIVE',
    email           varchar(200),
    created_at      timestamp(6) with time zone not null,
    updated_at      timestamp(6) with time zone not null,
    version         bigint                      not null default 0
);

create table agents (
    id              uuid                        primary key,
    name            varchar(120)                not null unique,
    description     text,
    owner_user_id   uuid,
    api_key_hash    varchar(255)                not null,
    status          varchar(20)                 not null default 'ACTIVE',
    last_used_at    timestamp(6) with time zone,
    created_at      timestamp(6) with time zone not null,
    updated_at      timestamp(6) with time zone not null,
    version         bigint                      not null default 0,
    constraint fk_agents_owner
        foreign key (owner_user_id) references users (id) on delete set null
);

create index idx_agents_status on agents (status);

create table policies (
    id                  uuid                        primary key,
    name                varchar(200)                not null,
    description         text,
    priority            integer                     not null default 100,
    effect              varchar(20)                 not null,
    enabled             boolean                     not null default true,
    action_type         varchar(40),
    resource_pattern    varchar(200),
    min_risk_level      varchar(20),
    created_by_user_id  uuid,
    created_at          timestamp(6) with time zone not null,
    updated_at          timestamp(6) with time zone not null,
    version             bigint                      not null default 0,
    constraint fk_policies_created_by
        foreign key (created_by_user_id) references users (id) on delete set null
);

create index idx_policies_action_type on policies (action_type);
create index idx_policies_priority    on policies (priority desc);

create table policy_conditions (
    id          uuid                        primary key,
    policy_id   uuid                        not null,
    field       varchar(120)                not null,
    operator    varchar(20)                 not null,
    value       text                        not null,
    created_at  timestamp(6) with time zone not null,
    updated_at  timestamp(6) with time zone not null,
    version     bigint                      not null default 0,
    constraint fk_policy_conditions_policy
        foreign key (policy_id) references policies (id) on delete cascade
);

create index idx_policy_conditions_policy on policy_conditions (policy_id);

create table action_requests (
    id                  uuid                        primary key,
    agent_id            uuid                        not null,
    action_type         varchar(40)                 not null,
    resource            varchar(255),
    risk_level          varchar(20)                 not null,
    metadata_json       text,
    status              varchar(30)                 not null default 'RECEIVED',
    decision_reason     varchar(500),
    matched_policy_id   uuid,
    created_at          timestamp(6) with time zone not null,
    updated_at          timestamp(6) with time zone not null,
    version             bigint                      not null default 0,
    constraint fk_action_requests_agent
        foreign key (agent_id) references agents (id),
    constraint fk_action_requests_matched_policy
        foreign key (matched_policy_id) references policies (id) on delete set null
);

create index idx_action_requests_agent    on action_requests (agent_id);
create index idx_action_requests_status   on action_requests (status);
create index idx_action_requests_created  on action_requests (created_at desc);

create table policy_decisions (
    id                       uuid                        primary key,
    action_request_id        uuid                        not null,
    decision                 varchar(20)                 not null,
    matched_policy_id        uuid,
    evaluated_at             timestamp(6) with time zone not null,
    evaluation_details_json  text,
    created_at               timestamp(6) with time zone not null,
    updated_at               timestamp(6) with time zone not null,
    version                  bigint                      not null default 0,
    constraint fk_policy_decisions_action_request
        foreign key (action_request_id) references action_requests (id) on delete cascade,
    constraint fk_policy_decisions_matched_policy
        foreign key (matched_policy_id) references policies (id) on delete set null
);

create index idx_policy_decisions_action on policy_decisions (action_request_id);

create table approval_requests (
    id                  uuid                        primary key,
    action_request_id   uuid                        not null,
    status              varchar(20)                 not null default 'PENDING',
    reviewer_user_id    uuid,
    reviewer_note       varchar(1000),
    expires_at          timestamp(6) with time zone,
    decided_at          timestamp(6) with time zone,
    created_at          timestamp(6) with time zone not null,
    updated_at          timestamp(6) with time zone not null,
    version             bigint                      not null default 0,
    constraint fk_approval_requests_action_request
        foreign key (action_request_id) references action_requests (id) on delete cascade,
    constraint fk_approval_requests_reviewer
        foreign key (reviewer_user_id) references users (id) on delete set null
);

create index idx_approval_requests_status on approval_requests (status);
create index idx_approval_requests_action on approval_requests (action_request_id);

create table audit_logs (
    id              uuid                        primary key,
    event_type      varchar(60)                 not null,
    actor_type      varchar(20)                 not null,
    actor_id        uuid,
    subject_type    varchar(40),
    subject_id      uuid,
    summary         varchar(500)                not null,
    details_json    text,
    created_at      timestamp(6) with time zone not null,
    updated_at      timestamp(6) with time zone not null,
    version         bigint                      not null default 0
);

create index idx_audit_logs_actor      on audit_logs (actor_id);
create index idx_audit_logs_subject    on audit_logs (subject_id);
create index idx_audit_logs_event_type on audit_logs (event_type);
create index idx_audit_logs_created    on audit_logs (created_at desc);
