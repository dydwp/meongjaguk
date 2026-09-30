package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.WalkDetailView;
import com.mungjaguk.app.dto.WalkHistoryItemView;
import com.mungjaguk.app.dto.WalkPointView;
import com.mungjaguk.app.entity.CoursePoint;
import com.mungjaguk.app.entity.Pet;
import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.entity.WalkRecord;
import com.mungjaguk.app.entity.WalkRecordPlannedPoint;
import com.mungjaguk.app.entity.WalkRecordPoint;
import com.mungjaguk.app.repository.CoursePointRepository;
import com.mungjaguk.app.repository.PetRepository;
import com.mungjaguk.app.repository.RouteRepository;
import com.mungjaguk.app.repository.WalkRecordPetRepository;
import com.mungjaguk.app.repository.WalkRecordPlannedPointRepository;
import com.mungjaguk.app.repository.WalkRecordPointRepository;
import com.mungjaguk.app.repository.WalkRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.mungjaguk.app.support.Fixtures.route;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalkRecordServiceTest {

    @Test
    void plannedPathIsVisibleOnlyToItsOwner() {
        WalkRecordRepository records = mock(WalkRecordRepository.class);
        WalkRecordPointRepository actualPoints = mock(WalkRecordPointRepository.class);
        WalkRecordPlannedPointRepository plannedPoints = mock(WalkRecordPlannedPointRepository.class);
        WalkRecordService service = new WalkRecordService(
                records,
                mock(RouteRepository.class),
                actualPoints,
                plannedPoints,
                mock(CoursePointRepository.class),
                mock(WalkRecordPetRepository.class),
                mock(PetRepository.class)
        );
        WalkRecord record = completedRecord();
        when(records.findByWalkRecordIdAndUserId(42L, 7L)).thenReturn(Optional.of(record));
        when(plannedPoints.findByWalkRecordIdOrderBySequenceNoAsc(42L)).thenReturn(List.of(
                WalkRecordPlannedPoint.of(42L, 1, 37.544, 127.043),
                WalkRecordPlannedPoint.of(42L, 2, 37.545, 127.044)));

        assertEquals(2, service.getPlannedPoints(42L, 7L).size());
        assertTrue(service.getPlannedPoints(42L, 8L).isEmpty());
        verify(plannedPoints, times(1)).findByWalkRecordIdOrderBySequenceNoAsc(42L);
    }

    @Test
    void deletingWalkRemovesPlannedPointsBeforeParentRecord() {
        WalkRecordRepository records = mock(WalkRecordRepository.class);
        WalkRecordPointRepository actualPoints = mock(WalkRecordPointRepository.class);
        WalkRecordPlannedPointRepository plannedPoints = mock(WalkRecordPlannedPointRepository.class);
        WalkRecordService service = new WalkRecordService(
                records,
                mock(RouteRepository.class),
                actualPoints,
                plannedPoints,
                mock(CoursePointRepository.class),
                mock(WalkRecordPetRepository.class),
                mock(PetRepository.class)
        );
        WalkRecord record = completedRecord();
        when(records.findByWalkRecordIdAndUserId(42L, 7L)).thenReturn(Optional.of(record));

        service.deleteMyCompletedWalkRecord(42L, 7L);

        InOrder order = inOrder(plannedPoints, actualPoints, records);
        order.verify(plannedPoints).deleteByWalkRecordId(42L);
        order.verify(actualPoints).deleteByWalkRecordId(42L);
        order.verify(records).delete(record);
    }

    private WalkRecord completedRecord() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 29, 10, 0);
        return WalkRecord.completed(7L, null, start, start.plusMinutes(10), 600, 700);
    }

    // ---------- 활동 내역 목록 / 상세 ----------

    private final WalkRecordRepository records = mock(WalkRecordRepository.class);
    private final RouteRepository routes = mock(RouteRepository.class);
    private final WalkRecordPointRepository actualPoints = mock(WalkRecordPointRepository.class);
    private final WalkRecordPlannedPointRepository plannedPoints = mock(WalkRecordPlannedPointRepository.class);
    private final CoursePointRepository coursePoints = mock(CoursePointRepository.class);
    private final WalkRecordPetRepository walkPets = mock(WalkRecordPetRepository.class);
    private final PetRepository pets = mock(PetRepository.class);
    private final WalkRecordService service = new WalkRecordService(
            records, routes, actualPoints, plannedPoints, coursePoints, walkPets, pets);

    private WalkRecord record(long id, Long courseId, int durationSeconds, int distanceM) {
        LocalDateTime start = LocalDateTime.of(2026, 9, 29, 10, 0);
        WalkRecord record = WalkRecord.completed(7L, courseId, start, start.plusSeconds(durationSeconds),
                durationSeconds, distanceM);
        ReflectionTestUtils.setField(record, "walkRecordId", id);
        return record;
    }

    @Test
    void historyTitleComesFromPlannedRouteThenCourseThenFreeWalk() {
        WalkRecord planned = record(3L, null, 2100, 2345);
        planned.setPlannedRoute("추천 코스", "설명", 2000L, 30);
        WalkRecord withCourse = record(2L, 5L, 20, 50);
        WalkRecord free = record(1L, null, 600, 0);
        when(records.findByUserIdOrderByStartedAtDesc(7L)).thenReturn(List.of(planned, withCourse, free));
        when(routes.findById(5)).thenReturn(Optional.of(route(5, "한강 코스")));
        when(walkPets.findPetNamesByUserId(7L)).thenReturn(Map.of(3L, List.of("보리", "초코")));

        List<WalkHistoryItemView> history = service.getMyWalkHistory(7L);

        assertEquals(List.of("추천 코스", "한강 코스", "자유 산책"),
                history.stream().map(WalkHistoryItemView::title).toList());
        assertEquals("보리 · 초코", history.get(0).petNamesLabel());
        assertEquals("", history.get(1).petNamesLabel());
        assertEquals("2.3km", history.get(0).distanceLabel());
        assertEquals("약 35분", history.get(0).durationLabel());
        assertEquals("약 1분", history.get(1).durationLabel()); // 1분 미만도 최소 1분
        assertEquals("2026.09.29", history.get(0).dateLabel());
    }

    @Test
    void historyFilteredByMyPet() {
        when(records.findByUserIdOrderByStartedAtDesc(7L))
                .thenReturn(List.of(record(3L, null, 60, 100), record(2L, null, 60, 100), record(1L, null, 60, 100)));
        when(pets.findByPetIdAndUser_UserId(9L, 7L)).thenReturn(Optional.of(new Pet()));
        when(walkPets.findWalkRecordIdsByPetId(7L, 9L)).thenReturn(Set.of(1L, 3L));
        when(walkPets.findPetNamesByUserId(7L)).thenReturn(Map.of());

        assertEquals(List.of(3L, 1L), service.getMyWalkHistory(7L, 9L).stream().map(WalkHistoryItemView::id).toList());
    }

    @Test
    void filteringByOthersPetIsNotFound() {
        when(pets.findByPetIdAndUser_UserId(9L, 7L)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.getMyWalkHistory(7L, 9L));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void detailOfPlannedWalkShowsPlanAndActualLabels() {
        WalkRecord planned = record(3L, null, 2292, 2400);
        planned.setPlannedRoute("추천 코스", "공원 산책", 2300L, 35);
        when(records.findByWalkRecordIdAndUserId(3L, 7L)).thenReturn(Optional.of(planned));
        when(walkPets.findPetNamesByWalkRecordId(3L, 7L)).thenReturn(List.of("보리"));

        WalkDetailView detail = service.getDetail(3L, 7L).orElseThrow();

        assertEquals("추천 코스", detail.title());
        assertTrue(detail.hasRoute());
        assertTrue(detail.deletable());
        assertEquals("공원 산책", detail.description());
        assertEquals("거리 · 2.3km", detail.plannedDistanceLabel());
        assertEquals("예상 소요시간 · 약 35분", detail.plannedDurationLabel());
        assertEquals("보리", detail.petNamesLabel());
        assertEquals("2.4km", detail.actualDistanceLabel());
        assertEquals("38:12", detail.actualDurationLabel());
        assertEquals("2026.09.29 완료", detail.completedDateLabel());
    }

    @Test
    void detailOfFreeWalkHasNoPlan() {
        when(records.findByWalkRecordIdAndUserId(1L, 7L)).thenReturn(Optional.of(record(1L, null, 65, 120)));
        when(walkPets.findPetNamesByWalkRecordId(1L, 7L)).thenReturn(List.of());

        WalkDetailView detail = service.getDetail(1L, 7L).orElseThrow();

        assertEquals("자유 산책", detail.title());
        assertFalse(detail.hasRoute());
        assertEquals("거리 · -", detail.plannedDistanceLabel());
        assertEquals("예상 소요시간 · -", detail.plannedDurationLabel());
        assertEquals("01:05", detail.actualDurationLabel());
    }

    @Test
    void othersWalkIsHidden() {
        when(records.findByWalkRecordIdAndUserId(1L, 8L)).thenReturn(Optional.empty());

        assertTrue(service.getDetail(1L, 8L).isEmpty());
        assertTrue(service.getWalkPoints(1L, 8L).isEmpty());
        verify(actualPoints, never()).findByWalkRecordIdOrderBySequenceNoAsc(1L);
    }

    @Test
    void walkPointsAreReturnedInOrder() {
        when(records.findByWalkRecordIdAndUserId(1L, 7L)).thenReturn(Optional.of(record(1L, null, 60, 100)));
        when(actualPoints.findByWalkRecordIdOrderBySequenceNoAsc(1L)).thenReturn(List.of(
                WalkRecordPoint.of(1L, 1, 37.1, 127.1, LocalDateTime.now()),
                WalkRecordPoint.of(1L, 2, 37.2, 127.2, LocalDateTime.now())));

        List<WalkPointView> points = service.getWalkPoints(1L, 7L);

        assertEquals(2, points.size());
        assertEquals(new BigDecimal("37.2000000"), points.get(1).latitude());
    }

    @Test
    void plannedPointsFallBackToCoursePoints() {
        Route course = route(5, "한강 코스");
        when(records.findByWalkRecordIdAndUserId(2L, 7L)).thenReturn(Optional.of(record(2L, 5L, 60, 100)));
        when(plannedPoints.findByWalkRecordIdOrderBySequenceNoAsc(2L)).thenReturn(List.of());
        when(coursePoints.findByCourse_CourseIdOrderBySequenceNoAsc(5)).thenReturn(List.of(
                CoursePoint.create(course, 1, 37.5, 127.0), CoursePoint.create(course, 2, 37.6, 127.1)));

        List<WalkPointView> points = service.getPlannedPoints(2L, 7L);

        assertEquals(2, points.size());
        assertEquals(0, new BigDecimal("37.6").compareTo(points.get(1).latitude()));
    }

    @Test
    void deletingMissingWalkIsNotFound() {
        when(records.findByWalkRecordIdAndUserId(1L, 7L)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.deleteMyCompletedWalkRecord(1L, 7L));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verify(records, never()).delete(any());
    }

    @Test
    void isMyPetChecksOwner() {
        when(pets.findByPetIdAndUser_UserId(9L, 7L)).thenReturn(Optional.of(new Pet()));

        assertTrue(service.isMyPet(7L, 9L));
        assertFalse(service.isMyPet(8L, 9L));
    }
}
