package com.mpole.hana.digitaltwin.menu.entity;

import com.mpole.hana.digitaltwin.menu.dto.MenuCreateRequest;
import com.mpole.hana.digitaltwin.menu.dto.MenuUpdateRequest;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_menu", indexes = {
        @Index(name = "idx_menu_sort_order", columnList = "sort_order"),
        @Index(name = "idx_menu_active", columnList = "active")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Menu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "menu_id")
    private Long menuId;

    @Column(name = "menu_name", nullable = false)
    @Comment("메뉴명")
    private String menuName;

    @Column(name = "menu_code", nullable = false, unique = true)
    @Comment("메뉴 코드")
    private String menuCode;

    @Column(name = "sort_order", nullable = false)
    @Comment("정렬 순서")
    private Integer sortOrder;

    @Column(nullable = false)
    @Builder.Default
    @Comment("활성화 여부")
    private Boolean active = true;

    @OneToMany(mappedBy = "menu")
    @Builder.Default
    private List<RoleMenu> roleMenus = new ArrayList<>();


    // method
    public static Menu create(MenuCreateRequest request) {
        return Menu.builder()
                .menuName(request.menuName())
                .menuCode(request.menuCode())
                .sortOrder(request.sortOrder())
                .active(true)
                .build();
    }
    public void update(MenuUpdateRequest request) {
        this.menuName = request.menuName();
        this.menuCode = request.menuCode();
        if (request.active() != null) {
            this.active = request.active();
        }
    }
    public void changeSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
