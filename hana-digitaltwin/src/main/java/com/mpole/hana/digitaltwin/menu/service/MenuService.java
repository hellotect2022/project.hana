package com.mpole.hana.digitaltwin.menu.service;

import com.mpole.hana.digitaltwin.menu.dto.*;
import com.mpole.hana.digitaltwin.menu.entity.Menu;
import com.mpole.hana.digitaltwin.menu.entity.RoleMenu;
import com.mpole.hana.digitaltwin.menu.repository.MenuRepository;
import com.mpole.hana.digitaltwin.menu.repository.RoleMenuRepository;
import com.mpole.hana.digitaltwin.iam.role.entity.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuService {
    private final MenuRepository menuRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final EntityManager entityManager;

    public List<MenuResponse> getMenus() {
        return menuRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(MenuResponse::from)
                .toList();
    }

    public List<Long> getMenuPermissions(Long roleId) {
        return roleMenuRepository.findMenuIdsByRoleId(roleId);
    }

    @Transactional
    public void createMenu(MenuCreateRequest request) {
        if (menuRepository.existsByMenuName(request.menuName())) {
            throw new IllegalStateException("이미 존재하는 메뉴명입니다.");
        }
        if (menuRepository.existsByMenuCode(request.menuCode())) {
            throw new IllegalStateException("이미 존재하는 메뉴 코드입니다.");
        }
        menuRepository.save(Menu.create(request));
    }

    @Transactional
    public void updateMenu(Long menuId, MenuUpdateRequest request) {
        Menu menu = getMenuEntity(menuId);
        menu.update(request);
    }

    @Transactional
    public void updateMenuSortOrders(List<MenuSortOrderRequest> requests) {
        for (MenuSortOrderRequest request : requests) {
            Menu menu = getMenuEntity(request.menuId());
            menu.changeSortOrder(request.sortOrder());
        }
    }

    @Transactional
    public void deleteMenu(Long menuId) {
        if (!menuRepository.existsById(menuId)) {
            throw new EntityNotFoundException("해당 메뉴가 존재하지 않습니다.");
        }
        menuRepository.deleteById(menuId);
    }

    @Transactional
    public void updateMenuPermissions(Long roleId, MenuPermissionRequest request) {
        roleMenuRepository.deleteByRole_RoleId(roleId);
        Role role = entityManager.getReference(Role.class, roleId);
        List<RoleMenu> roleMenus = request.menuIds()
                .stream()
                .map(menuId -> {
                    Menu menu = entityManager.getReference(Menu.class, menuId);
                    return RoleMenu.of(role, menu);
                })
                .toList();
        roleMenuRepository.saveAll(roleMenus);
    }


    private Menu getMenuEntity(Long menuId) {
        return menuRepository.findById(menuId)
                .orElseThrow(() -> new EntityNotFoundException("해당 메뉴가 존재하지 않습니다."));
    }

}
