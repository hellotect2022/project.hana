package com.mpole.hana.digitaltwin.menu.entity;

import com.mpole.hana.digitaltwin.iam.role.entity.Role;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tbl_role_menu", uniqueConstraints = {
        @UniqueConstraint(name = "uk_role_menu", columnNames = {"role_id", "menu_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RoleMenu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="role_menu_id")
    private Long roleMenuId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="menu_id", nullable = false)
    private Menu menu;

    public static RoleMenu of(Role role, Menu menu) {
        return RoleMenu.builder()
                .role(role)
                .menu(menu)
                .build();
    }
}
