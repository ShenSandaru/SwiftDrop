package com.swiftdrop.delivery.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.swiftdrop.delivery.api.DispatchActivity;
import com.swiftdrop.delivery.api.DispatchActivityAction;
import com.swiftdrop.delivery.api.DispatchActivityDetails;
import com.swiftdrop.delivery.api.DispatchActivityFilter;
import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelStatus;

/** Delivery-owned read projection over the parcel and assignment history tables. */
@Repository
class DispatchActivityHistoryQuery {

    private static final String ACTIVITY_CTE = """
            WITH activity_rows AS (
                SELECT 'ASSIGNMENT' AS activity_source,
                       a.id AS activity_id,
                       p.tracking_number,
                       a.assigned_at AS occurred_at,
                       a.dispatcher_id AS actor_id,
                       CASE
                           WHEN EXISTS (
                               SELECT 1
                               FROM parcel_assignments previous
                               WHERE previous.parcel_id = a.parcel_id
                                 AND previous.assignment_type = a.assignment_type
                                 AND previous.status = 'REASSIGNED'
                                 AND previous.completed_at = a.assigned_at
                           ) THEN 'COURIER_REASSIGNED'
                           WHEN a.assignment_type = 'PICKUP' THEN 'PICKUP_ASSIGNED'
                           WHEN a.assignment_type = 'DELIVERY' THEN 'DELIVERY_ASSIGNED'
                           ELSE 'RETURN_ASSIGNED'
                       END AS action_type,
                       NULL::varchar AS from_status,
                       NULL::varchar AS to_status,
                       a.assignment_type,
                       a.courier_id,
                       NULL::varchar AS hub_code,
                       NULL::integer AS attempt,
                       NULL::varchar AS failure_reason_code
                FROM parcel_assignments a
                JOIN parcels p ON p.id = a.parcel_id

                UNION ALL

                SELECT 'STATUS' AS activity_source,
                       h.id AS activity_id,
                       p.tracking_number,
                       h.occurred_at,
                       h.actor_user_id AS actor_id,
                       CASE
                           WHEN h.to_status = 'AT_HUB' THEN 'HUB_SCAN'
                           WHEN h.to_status = 'IN_TRANSIT' THEN 'TRANSIT'
                           WHEN h.to_status = 'FAILED' THEN 'FAILURE'
                           WHEN h.to_status = 'RETURNING' THEN 'RETURN_INITIATED'
                           ELSE 'CANCELLATION'
                       END AS action_type,
                       h.from_status,
                       h.to_status,
                       NULL::varchar AS assignment_type,
                       NULL::uuid AS courier_id,
                       h.hub_code,
                       h.attempt,
                       h.failure_reason_code
                FROM parcel_status_history h
                JOIN parcels p ON p.id = h.parcel_id
                WHERE h.actor_role IN ('ADMIN', 'DISPATCHER', 'DISPATCHER_ADMIN')
                  AND h.to_status IN (
                      'AT_HUB', 'IN_TRANSIT', 'FAILED', 'RETURNING', 'CANCELLED'
                  )
            )
            """;

    private static final RowMapper<DispatchActivity> ACTIVITY_ROW_MAPPER =
            DispatchActivityHistoryQuery::mapActivity;

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    DispatchActivityHistoryQuery(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    PageResult<DispatchActivity> find(DispatchActivityFilter filter, PageRequest page) {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(page, "page");
        NamedParameterJdbcTemplate template = jdbc.getObject();

        String predicates = predicates(filter);
        MapSqlParameterSource parameters = parameters(filter);
        long totalItems = template.queryForObject(
                ACTIVITY_CTE + "SELECT COUNT(*) FROM activity_rows WHERE 1 = 1" + predicates,
                parameters,
                Long.class);

        parameters.addValue("limit", page.size());
        parameters.addValue("offset", (long) page.page() * page.size());
        List<DispatchActivity> activities = template.query(
                ACTIVITY_CTE + """
                        SELECT *
                        FROM activity_rows
                        WHERE 1 = 1
                        """ + predicates + """
                        ORDER BY occurred_at DESC, activity_source ASC, activity_id DESC
                        LIMIT :limit OFFSET :offset
                        """,
                parameters,
                ACTIVITY_ROW_MAPPER);
        return PageResult.of(activities, page, totalItems);
    }

    private static String predicates(DispatchActivityFilter filter) {
        List<String> predicates = new ArrayList<>();
        if (filter.from() != null) {
            predicates.add("AND occurred_at >= :from");
        }
        if (filter.to() != null) {
            predicates.add("AND occurred_at < :to");
        }
        if (filter.actionType() != null) {
            predicates.add("AND action_type = :actionType");
        }
        if (filter.trackingNumber() != null) {
            predicates.add("AND tracking_number = :trackingNumber");
        }
        if (filter.dispatcherId() != null) {
            predicates.add("AND actor_id = :dispatcherId");
        }
        return predicates.isEmpty() ? "" : "\n" + String.join("\n", predicates) + "\n";
    }

    private static MapSqlParameterSource parameters(DispatchActivityFilter filter) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (filter.from() != null) {
            parameters.addValue("from", OffsetDateTime.ofInstant(filter.from(), ZoneOffset.UTC));
        }
        if (filter.to() != null) {
            parameters.addValue("to", OffsetDateTime.ofInstant(filter.to(), ZoneOffset.UTC));
        }
        if (filter.actionType() != null) {
            parameters.addValue("actionType", filter.actionType().name());
        }
        if (filter.trackingNumber() != null) {
            parameters.addValue("trackingNumber", filter.trackingNumber());
        }
        if (filter.dispatcherId() != null) {
            parameters.addValue("dispatcherId", filter.dispatcherId());
        }
        return parameters;
    }

    private static DispatchActivity mapActivity(ResultSet row, int rowNumber) throws SQLException {
        String source = row.getString("activity_source");
        String activityId = row.getObject("activity_id", UUID.class).toString();
        ParcelAssignmentType assignmentType = enumValue(ParcelAssignmentType.class, row.getString("assignment_type"));
        ParcelStatus fromStatus = enumValue(ParcelStatus.class, row.getString("from_status"));
        ParcelStatus toStatus = enumValue(ParcelStatus.class, row.getString("to_status"));
        FailureReason failureReason = enumValue(FailureReason.class, row.getString("failure_reason_code"));
        int attemptValue = row.getInt("attempt");
        Integer attempt = row.wasNull() ? null : attemptValue;

        return new DispatchActivity(
                source + ":" + activityId,
                row.getString("tracking_number"),
                row.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                DispatchActivityAction.valueOf(row.getString("action_type")),
                row.getObject("actor_id", UUID.class),
                new DispatchActivityDetails(
                        assignmentType,
                        row.getObject("courier_id", UUID.class),
                        fromStatus,
                        toStatus,
                        row.getString("hub_code"),
                        attempt,
                        failureReason));
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
