package com.mpole.hana.digitaltwin.menu.repository;

import com.mpole.hana.digitaltwin.menu.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuRepository extends JpaRepository<Menu,Long> {

    boolean existsByMenuName(String menuName);
    boolean existsByMenuCode(String menuCode);
    List<Menu> findAllByOrderBySortOrderAsc();
}
