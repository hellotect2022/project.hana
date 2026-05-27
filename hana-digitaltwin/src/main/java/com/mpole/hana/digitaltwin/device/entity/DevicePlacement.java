package com.mpole.hana.digitaltwin.device.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;
import jakarta.persistence.*;
import lombok.*;
import jakarta.persistence.Id;

@Entity
@Table(name = "tbl_device_placement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevicePlacement extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "placement_id")
    private Long placementId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false, unique = true)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unity_zone_id")
    private UnityLocZone unityZone;

    @Column(name = "pos_x", nullable = false) @Builder.Default private Float posX = 0f;
    @Column(name = "pos_y", nullable = false) @Builder.Default private Float posY = 0f;
    @Column(name = "pos_z", nullable = false) @Builder.Default private Float posZ = 0f;
    @Column(name = "rot_x", nullable = false) @Builder.Default private Float rotX = 0f;
    @Column(name = "rot_y", nullable = false) @Builder.Default private Float rotY = 0f;
    @Column(name = "rot_z", nullable = false) @Builder.Default private Float rotZ = 0f;
    @Column(name = "scale_x", nullable = false) @Builder.Default private Float scaleX = 1f;
    @Column(name = "scale_y", nullable = false) @Builder.Default private Float scaleY = 1f;
    @Column(name = "scale_z", nullable = false) @Builder.Default private Float scaleZ = 1f;

    public void applyTransform(Float px, Float py, Float pz,
                               Float rx, Float ry, Float rz,
                               Float sx, Float sy, Float sz) {
        if (px != null) posX = px;
        if (py != null) posY = py;
        if (pz != null) posZ = pz;
        if (rx != null) rotX = rx;
        if (ry != null) rotY = ry;
        if (rz != null) rotZ = rz;
        if (sx != null) scaleX = sx;
        if (sy != null) scaleY = sy;
        if (sz != null) scaleZ = sz;
    }

    public boolean isPlaced() {
        return unityZone != null;
    }
}
