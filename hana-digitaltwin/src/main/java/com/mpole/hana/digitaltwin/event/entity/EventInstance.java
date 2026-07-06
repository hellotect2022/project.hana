package com.mpole.hana.digitaltwin.event.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "tbl_event_instance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventInstance extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private EventRule rule;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_type", nullable = false, length = 16)
    private EventDisplayType displayType;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "point_id")
    private Long pointId;

    @Column(name = "location_text")
    private String locationText;

    @Column(name = "building_code", length = 64)
    private String buildingCode;

    @Column(name = "floor_code", length = 64)
    private String floorCode;

    @Column(name = "zone_code", length = 64)
    private String zoneCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> context;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EventInstanceStatus status = EventInstanceStatus.OPEN;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "alarm_title")
    private String alarmTitle;

    @Column(name = "alarm_detail", columnDefinition = "TEXT")
    private String alarmDetail;

    @Column(name = "error_code", length = 128)
    private String errorCode;
}
