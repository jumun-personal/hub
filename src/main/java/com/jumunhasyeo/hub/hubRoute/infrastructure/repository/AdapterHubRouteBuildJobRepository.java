package com.jumunhasyeo.hub.hubRoute.infrastructure.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteBuildJob;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteBuildJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AdapterHubRouteBuildJobRepository implements HubRouteBuildJobRepository {

    private final JpaHubRouteBuildJobRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void request(final Hub hub) {
        repository.save(HubRouteBuildJob.request(hub));
    }

    @Override
    public Optional<HubRouteBuildJobClaim> claimPlanning(final LocalDateTime staleBefore) {
        final UUID token = UUID.randomUUID();
        final String sql = """
                WITH claimed AS (
                    SELECT hub_id
                      FROM p_hub_route_build_job
                     WHERE is_deleted = false
                       AND (
                            (status = 'READY' AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP))
                         OR (status = 'PLANNING' AND modified_at < ?)
                       )
                     ORDER BY created_at
                     LIMIT 1
                     FOR UPDATE SKIP LOCKED
                )
                UPDATE p_hub_route_build_job job
                   SET status = 'PLANNING',
                       processing_token = ?,
                       error_message = NULL,
                       modified_at = CURRENT_TIMESTAMP
                  FROM claimed
                 WHERE job.hub_id = claimed.hub_id
                RETURNING job.hub_id
                """;
        return jdbcTemplate.query(
                sql,
                ps -> {
                    ps.setTimestamp(1, Timestamp.valueOf(staleBefore));
                    ps.setObject(2, token);
                },
                rs -> rs.next()
                        ? Optional.of(new HubRouteBuildJobClaim(rs.getObject("hub_id", UUID.class), token))
                        : Optional.empty()
        );
    }

    @Override
    public Optional<HubRouteBuildJob> findByHubId(final UUID hubId) {
        return repository.findById(hubId);
    }

    @Override
    public int initialize(final UUID hubId, final UUID processingToken, final int pairCount) {
        return jdbcTemplate.update("""
                UPDATE p_hub_route_build_job
                   SET status = ?,
                       total_count = total_count + ?,
                       remaining_count = remaining_count + ?,
                       failed_count = 0,
                       processing_token = NULL,
                       error_message = NULL,
                       modified_at = CURRENT_TIMESTAMP
                 WHERE hub_id = ?
                   AND status = 'PLANNING'
                   AND processing_token = ?
                """,
                pairCount == 0 ? "COMPLETE" : "RUNNING",
                pairCount,
                pairCount,
                hubId,
                processingToken
        );
    }

    @Override
    public Optional<HubRouteBuildJobCounter> completePair(final UUID hubId) {
        return updateCounter("""
                UPDATE p_hub_route_build_job
                   SET remaining_count = remaining_count - 1,
                       modified_at = CURRENT_TIMESTAMP
                 WHERE hub_id = ?
                   AND status = 'RUNNING'
                   AND remaining_count > 0
                RETURNING hub_id, remaining_count, failed_count
                """, hubId);
    }

    @Override
    public Optional<HubRouteBuildJobCounter> failPair(final UUID hubId, final String reason) {
        return jdbcTemplate.query(
                """
                UPDATE p_hub_route_build_job
                   SET remaining_count = remaining_count - 1,
                       failed_count = failed_count + 1,
                       error_message = ?,
                       modified_at = CURRENT_TIMESTAMP
                 WHERE hub_id = ?
                   AND status = 'RUNNING'
                   AND remaining_count > 0
                RETURNING hub_id, remaining_count, failed_count
                """,
                ps -> {
                    ps.setString(1, reason);
                    ps.setObject(2, hubId);
                },
                rs -> rs.next()
                        ? Optional.of(new HubRouteBuildJobCounter(
                        rs.getObject("hub_id", UUID.class),
                        rs.getInt("remaining_count"),
                        rs.getInt("failed_count")))
                        : Optional.empty()
        );
    }

    @Override
    public int retryFailed(final UUID hubId, final int remainingCount) {
        return jdbcTemplate.update("""
                UPDATE p_hub_route_build_job
                   SET status = ?,
                       remaining_count = ?,
                       failed_count = 0,
                       retry_count = retry_count + 1,
                       next_retry_at = CURRENT_TIMESTAMP,
                       processing_token = NULL,
                       error_message = NULL,
                       modified_at = CURRENT_TIMESTAMP
                 WHERE hub_id = ?
                   AND status = 'FAILED'
                   AND is_deleted = false
                """,
                remainingCount == 0 ? "READY" : "RUNNING",
                remainingCount,
                hubId
        );
    }

    @Override
    public void addRemaining(final UUID hubId, final int pairCount) {
        jdbcTemplate.update("""
                UPDATE p_hub_route_build_job
                   SET status = 'RUNNING',
                       total_count = total_count + ?,
                       remaining_count = remaining_count + ?,
                       modified_at = CURRENT_TIMESTAMP
                 WHERE hub_id = ?
                   AND status = 'RUNNING'
                """, pairCount, pairCount, hubId);
    }

    @Override
    public void complete(final UUID hubId) {
        jdbcTemplate.update("""
                UPDATE p_hub_route_build_job
                   SET status = 'COMPLETE',
                       processing_token = NULL,
                       error_message = NULL,
                       modified_at = CURRENT_TIMESTAMP
                 WHERE hub_id = ?
                   AND status IN ('RUNNING', 'PLANNING', 'READY')
                """, hubId);
    }

    @Override
    public int fail(final UUID hubId, final String reason) {
        return jdbcTemplate.update("""
                UPDATE p_hub_route_build_job
                   SET status = 'FAILED',
                       processing_token = NULL,
                       error_message = ?,
                       modified_at = CURRENT_TIMESTAMP
                 WHERE hub_id = ?
                   AND status = 'RUNNING'
                """, reason, hubId);
    }

    @Override
    public void cancel(final UUID hubId, final String reason) {
        jdbcTemplate.update("""
                UPDATE p_hub_route_build_job
                   SET status = 'CANCELLED',
                       processing_token = NULL,
                       error_message = ?,
                       modified_at = CURRENT_TIMESTAMP
                 WHERE hub_id = ?
                   AND status <> 'COMPLETE'
                """, reason, hubId);
    }

    @Override
    public void lockFinalization() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtext('hub-route-build-finalization'))"
            )) {
                statement.execute();
                return null;
            }
        });
    }

    private Optional<HubRouteBuildJobCounter> updateCounter(final String sql, final UUID hubId) {
        return jdbcTemplate.query(
                sql,
                ps -> ps.setObject(1, hubId),
                rs -> rs.next()
                        ? Optional.of(new HubRouteBuildJobCounter(
                        rs.getObject("hub_id", UUID.class),
                        rs.getInt("remaining_count"),
                        rs.getInt("failed_count")))
                        : Optional.empty()
        );
    }
}
