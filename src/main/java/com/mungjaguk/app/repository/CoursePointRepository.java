package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.CoursePoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CoursePointRepository extends JpaRepository<CoursePoint, Long> {

    /** 코스 하나의 경로 좌표 (순서대로) */
    List<CoursePoint> findByCourse_CourseIdOrderBySequenceNoAsc(Integer courseId);

    /** 여러 코스의 경로 좌표 (게시판 목록에서 한 번에 조회) */
    List<CoursePoint> findByCourse_CourseIdInOrderByCourse_CourseIdAscSequenceNoAsc(Collection<Integer> courseIds);
}
