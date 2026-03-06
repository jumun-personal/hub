TRUNCATE TABLE p_hub_route, p_hub_relation, p_outbox_events, p_hub CASCADE;

INSERT INTO p_hub (
    hub_id, name, address, latitude, longitude, hub_type, status,
    created_at, modified_at, is_deleted
)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'bench-read-target',
    'benchmark',
    37.5,
    127.0,
    'CENTER',
    'COMPLETE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
);

INSERT INTO p_hub (
    hub_id, name, address, latitude, longitude, hub_type, status,
    created_at, modified_at, is_deleted
)
SELECT
    md5('current-center-' || sequence)::uuid,
    'current-center-' || sequence,
    'benchmark',
    37.5 + sequence * 0.0001,
    127.0 + sequence * 0.0001,
    'CENTER',
    'COMPLETE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
FROM generate_series(2, 100) AS sequence;
