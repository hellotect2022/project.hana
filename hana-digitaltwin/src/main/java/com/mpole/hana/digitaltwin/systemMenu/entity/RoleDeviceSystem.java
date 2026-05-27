package com.mpole.hana.digitaltwin.systemMenu.entity;

import com.mpole.hana.digitaltwin.iam.role.entity.Role;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tbl_role_device_system", uniqueConstraints = {
        @UniqueConstraint(name = "uk_role_device_system", columnNames = {"role_id", "system_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RoleDeviceSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_device_system_id")
    private Long roleDeviceSystemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_id", nullable = false)
    private DeviceSystem deviceSystem;

    public static RoleDeviceSystem of(Role role, DeviceSystem deviceSystem) {
        return RoleDeviceSystem.builder()
                .role(role)
                .deviceSystem(deviceSystem)
                .build();
    }
}
