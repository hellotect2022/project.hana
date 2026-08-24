package com.mpole.hana.digitaltwin.virtualPatrol.service;

import com.mpole.hana.digitaltwin.virtualPatrol.dto.VirtualPatrolRequest;
import com.mpole.hana.digitaltwin.virtualPatrol.dto.VirtualPatrolResponse;
import com.mpole.hana.digitaltwin.virtualPatrol.entity.VirtualPatrolRoute;
import com.mpole.hana.digitaltwin.virtualPatrol.repository.VirtualPatrolRouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VirtualPatrolService {

    private final VirtualPatrolRouteRepository repository;

    @Transactional
    public VirtualPatrolResponse create(VirtualPatrolRequest request) {
        String routeId = request.routeId();
        if (routeId == null || routeId.isBlank()) {
            throw new IllegalArgumentException("routeId(클라이언트 생성 UUID)는 필수입니다.");
        }
        if (repository.existsById(routeId)) {
            throw new IllegalStateException("이미 존재하는 routeId 입니다: " + routeId);
        }
        evictBookmarkSlot(request.bookmarkSlot(), null);
        VirtualPatrolRoute route = VirtualPatrolRoute.builder()
                .routeId(routeId)
                .displayName(request.displayName())
                .bookmarkSlot(request.bookmarkSlot())
                .waypoints(request.waypoints() != null ? request.waypoints() : List.of())
                .build();
        return VirtualPatrolResponse.from(repository.save(route));
    }

    @Transactional(readOnly = true)
    public List<VirtualPatrolResponse> getAll() {
        Sort sort = Sort.by(
                Sort.Order.asc("bookmarkSlot"),
                Sort.Order.asc("createdAt"));
        return repository.findAll(sort).stream()
                .map(VirtualPatrolResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public VirtualPatrolResponse getOne(String routeId) {
        return VirtualPatrolResponse.from(find(routeId));
    }

    @Transactional
    public VirtualPatrolResponse update(String routeId, VirtualPatrolRequest request) {
        VirtualPatrolRoute route = find(routeId);
        evictBookmarkSlot(request.bookmarkSlot(), routeId);
        route.update(request.displayName(), request.bookmarkSlot(), request.waypoints());
        return VirtualPatrolResponse.from(repository.save(route));
    }

    @Transactional
    public void delete(String routeId) {
        if (!repository.existsById(routeId)) {
            throw new EntityNotFoundException("가상순찰 경로를 찾을 수 없습니다. id=" + routeId);
        }
        repository.deleteById(routeId);
    }

    /**
     * 즐겨찾기 슬롯당 1경로 보장 — 지정하려는 slot 을 이미 다른 경로가 점유 중이면 그 경로의 slot 을 비운다.
     * unique 제약 위반 전에 반영되도록 flush.
     */
    private void evictBookmarkSlot(Integer slot, String selfRouteId) {
        if (slot == null) {
            return;
        }
        repository.findByBookmarkSlot(slot)
                .filter(r -> !r.getRouteId().equals(selfRouteId))
                .ifPresent(r -> {
                    r.setBookmarkSlot(null);
                    repository.saveAndFlush(r);
                });
    }

    private VirtualPatrolRoute find(String routeId) {
        return repository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("가상순찰 경로를 찾을 수 없습니다. id=" + routeId));
    }
}
