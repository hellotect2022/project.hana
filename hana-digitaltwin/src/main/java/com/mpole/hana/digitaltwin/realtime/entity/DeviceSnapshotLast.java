package com.mpole.hana.digitaltwin.realtime.entity;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "tbl_device_snapshot_last")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceSnapshotLast extends DateEntity {
    @Id
    @Column(name = "device_id")
    private Long deviceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "points", columnDefinition = "jsonb")
    private List<XnPointData> points;
}
