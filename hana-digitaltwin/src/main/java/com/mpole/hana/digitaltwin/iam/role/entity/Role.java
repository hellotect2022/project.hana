package com.mpole.hana.digitaltwin.iam.role.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

@Entity
@Table(name="tbl_role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends DateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="role_id")
    @Comment("role 아이디")
    private Long roleId;

    @Column(name="role_name",unique = true)
    @Comment("권한이름")
    private String roleName;

    @Column(name="description")
    @Comment("권한에 대한 설명")
    private String description;

    public static Role create(String roleName, String description) {
        return Role.builder()
                .roleName(roleName)
                .description(description)
                .build();
    }
    public void update(String roleName, String description) {
        this.roleName = roleName;
        this.description = description;
    }
}
