TRUNCATE TABLE p_hub_route, p_hub_relation, p_hub CASCADE;

INSERT INTO p_hub (
    hub_id, name, address, latitude, longitude, hub_type, status,
    created_at, modified_at, is_deleted
)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'benchmark-start',
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
    md5('route-end-' || sequence)::uuid,
    'end-' || sequence,
    'benchmark',
    37.5 + sequence * 0.0001,
    127.0 + sequence * 0.0001,
    'CENTER',
    'COMPLETE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
FROM generate_series(1, 270) AS sequence;

INSERT INTO p_hub_route (
    route_id, start_hub_id, end_hub_id, build_hub_id, route_status,
    distance_km, duration_minutes, resolved_provider, resolved_by_fallback,
    retry_count, next_refresh_at, created_at, modified_at, is_deleted
)
SELECT
    md5('forward-' || sequence)::uuid,
    '00000000-0000-0000-0000-000000000001'::uuid,
    md5('route-end-' || sequence)::uuid,
    '00000000-0000-0000-0000-000000000001'::uuid,
    'COMPLETE',
    10.0,
    20,
    'KAKAO',
    false,
    0,
    TIMESTAMP '2099-01-01 00:00:00',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
FROM generate_series(1, 270) AS sequence
UNION ALL
SELECT
    md5('reverse-' || sequence)::uuid,
    md5('route-end-' || sequence)::uuid,
    '00000000-0000-0000-0000-000000000001'::uuid,
    '00000000-0000-0000-0000-000000000001'::uuid,
    'COMPLETE',
    10.0,
    20,
    'KAKAO',
    false,
    0,
    TIMESTAMP '2099-01-01 00:00:00',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
FROM generate_series(1, 270) AS sequence;
