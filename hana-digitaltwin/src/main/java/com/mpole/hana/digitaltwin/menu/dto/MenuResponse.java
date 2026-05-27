package com.mpole.hana.digitaltwin.menu.dto;

import com.mpole.hana.digitaltwin.menu.entity.Menu;

public record MenuResponse(
        Long menuId,
        String menuName,
        String menuCode,
        Integer sortOrder,
        Boolean active
) {
    public static MenuResponse from(Menu menu) {
        return new MenuResponse(
                menu.getMenuId(),
                menu.getMenuName(),
                menu.getMenuCode(),
                menu.getSortOrder(),
                menu.getActive()
        );
    }
}
