package com.mungjaguk.app.service;

import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 메인 페이지에 필요한 데이터를 모아주는 서비스 (담당: 박용제)
 * 다른 팀원의 Repository는 조회만 하고 수정하지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MainService {

    private final RouteRepository routeRepository;

    /** 추천 산책로를 course_id 순서로 limit개 가져옴 */
    public List<Route> getRecommendRoutes(int limit) {
        PageRequest page = PageRequest.of(0, limit, Sort.by("courseId").ascending());
        return routeRepository.findAll(page).getContent();
    }
}