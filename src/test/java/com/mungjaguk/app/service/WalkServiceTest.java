package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.CoursePointDto;
import com.mungjaguk.app.dto.WalkSaveRequest;
import com.mungjaguk.app.entity.WalkRecord;
import com.mungjaguk.app.entity.WalkRecordPlannedPoint;
import com.mungjaguk.app.entity.WalkRecordPoint;
import com.mungjaguk.app.repository.WalkRecordPlannedPointRepository;
import com.mungjaguk.app.repository.WalkRecordPointRepository;
import com.mungjaguk.app.repository.WalkRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalkServiceTest {

    private static final long START = 1_700_000_000_000L; // 2023-11-15 07:13:20 (서울)

    private WalkRecordRepository records;
    private WalkRecordPointRepository actualPoints;
    private WalkRecordPlannedPointRepository plannedPoints;
    private WalkService walkService;

    @BeforeEach
    void setUp() {
        records = mock(WalkRecordRepository.class);
        actualPoints = mock(WalkRecordPointRepository.class);
        plannedPoints = mock(WalkRecordPlannedPointRepository.class);
        walkService = new WalkService(records, actualPoints, plannedPoints);
        when(records.save(any(WalkRecord.class))).thenAnswer(invocation -> {
            WalkRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "walkRecordId", 1L);
            return record;
        });
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void completedWalkStoresRecommendedAndActualPathsSeparately() {
        WalkRecordRepository recordRepository = mock(WalkRecordRepository.class);
        WalkRecordPointRepository actualRepository = mock(WalkRecordPointRepository.class);
        WalkRecordPlannedPointRepository plannedRepository = mock(WalkRecordPlannedPointRepository.class);
        WalkService service = new WalkService(recordRepository, actualRepository, plannedRepository);

        when(recordRepository.save(any(WalkRecord.class))).thenAnswer(invocation -> {
            WalkRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "walkRecordId", 42L);
            return record;
        });

        WalkSaveRequest.RecommendedRoute recommended = new WalkSaveRequest.RecommendedRoute(
                "서울숲 반려견 산책 코스", "공원을 따라 걷는 코스", 2600L, 40,
                List.of(new CoursePointDto(2, 37.545, 127.044),
                        new CoursePointDto(1, 37.544, 127.043)));
        WalkSaveRequest request = new WalkSaveRequest(null, 1_700_000_000_000L,
                1_700_000_060_000L, 100,
                List.of(new WalkSaveRequest.Point(37.544, 127.043, 1_700_000_000_000L)),
                recommended);

        assertEquals(42L, service.saveCompletedWalk(7L, request));

        ArgumentCaptor<WalkRecord> recordCaptor = ArgumentCaptor.forClass(WalkRecord.class);
        verify(recordRepository).save(recordCaptor.capture());
        assertEquals(recommended.title(), recordCaptor.getValue().getPlannedTitle());
        assertEquals(recommended.distanceM(), recordCaptor.getValue().getPlannedDistanceM());
        verify(actualRepository).saveAll(any());

        ArgumentCaptor<Iterable<WalkRecordPlannedPoint>> plannedCaptor =
                ArgumentCaptor.forClass((Class) Iterable.class);
        verify(plannedRepository).saveAll(plannedCaptor.capture());
        List<WalkRecordPlannedPoint> points = StreamSupport.stream(
                plannedCaptor.getValue().spliterator(), false).toList();
        assertEquals(2, points.size());
        assertEquals(1, points.get(0).getSequenceNo());
        assertEquals(37.544, points.get(0).getLatitude().doubleValue());
        assertEquals(37.545, points.get(1).getLatitude().doubleValue());
    }

    @Test
    void freeWalkIsSavedInSeoulTime() {
        WalkSaveRequest request = new WalkSaveRequest(null, START, START + 1_530_000L, 2100, null, null);

        assertEquals(1L, walkService.saveCompletedWalk(7L, request));

        WalkRecord saved = savedRecord();
        assertEquals(7L, saved.getUserId());
        assertEquals("COMPLETED", saved.getStatus());
        assertEquals(LocalDateTime.of(2023, 11, 15, 7, 13, 20), saved.getStartedAt());
        assertEquals(LocalDateTime.of(2023, 11, 15, 7, 38, 50), saved.getEndedAt());
        assertEquals(1530, saved.getDurationSeconds());
        assertEquals(2100, saved.getDistanceM());
        assertNull(saved.getPlannedTitle());
        verify(actualPoints, never()).saveAll(any()); // 좌표가 없으면 저장 안 함
        verify(plannedPoints, never()).saveAll(any());
    }

    @Test
    void wrongTimesAreRejected() {
        assertBadRequest(null);
        assertBadRequest(new WalkSaveRequest(null, null, START, 0, null, null));
        assertBadRequest(new WalkSaveRequest(null, START, null, 0, null, null));
        assertBadRequest(new WalkSaveRequest(null, START, START, 0, null, null));
        assertBadRequest(new WalkSaveRequest(null, START, START - 1, 0, null, null));
    }

    @Test
    void missingOrNegativeDistanceIsZero() {
        walkService.saveCompletedWalk(7L, new WalkSaveRequest(null, START, START + 60_000L, null, null, null));
        walkService.saveCompletedWalk(7L, new WalkSaveRequest(null, START, START + 60_000L, -50, null, null));

        ArgumentCaptor<WalkRecord> captor = ArgumentCaptor.forClass(WalkRecord.class);
        verify(records, times(2)).save(captor.capture());
        assertEquals(List.of(0, 0), captor.getAllValues().stream().map(WalkRecord::getDistanceM).toList());
    }

    @Test
    void brokenPointsAreSkippedAndRenumbered() {
        List<WalkSaveRequest.Point> points = Arrays.asList(
                new WalkSaveRequest.Point(37.1, 127.1, START),
                null,
                new WalkSaveRequest.Point(null, 127.2, START + 1000),
                new WalkSaveRequest.Point(37.3, 127.3, null));

        walkService.saveCompletedWalk(7L, new WalkSaveRequest(null, START, START + 60_000L, 100, points, null));

        List<WalkRecordPoint> saved = savedActualPoints();
        assertEquals(2, saved.size());
        assertEquals(List.of(1, 2), saved.stream().map(WalkRecordPoint::getSequenceNo).toList());
        assertEquals(1L, saved.get(0).getWalkRecordId());
        assertEquals(37.3, saved.get(1).getLatitude().doubleValue());
        // 시각이 없는 좌표는 산책 시작 시각으로 기록
        assertEquals(LocalDateTime.of(2023, 11, 15, 7, 13, 20), saved.get(1).getRecordedAt());
    }

    @Test
    void tooManyPointsAreRejected() {
        List<WalkSaveRequest.Point> points = new ArrayList<>();
        for (int i = 0; i <= 10_000; i++) {
            points.add(new WalkSaveRequest.Point(37.0, 127.0, START));
        }

        assertBadRequest(new WalkSaveRequest(null, START, START + 60_000L, 100, points, null));
    }

    @Test
    void recommendedRouteAndCourseIdCannotBothBeSent() {
        assertBadRequest(new WalkSaveRequest(3L, START, START + 60_000L, 100, null, route("추천 코스", 1000L, 2)));
        verify(records, never()).save(any());
    }

    @Test
    void invalidRecommendedRouteIsRejected() {
        assertBadRequest(withRoute(route(" ", 1000L, 2)));
        assertBadRequest(withRoute(route("가".repeat(101), 1000L, 2)));
        assertBadRequest(withRoute(route("추천 코스", 0L, 2)));
        assertBadRequest(withRoute(route("추천 코스", 1000L, 1)));
        assertBadRequest(withRoute(new WalkSaveRequest.RecommendedRoute("추천 코스", null, 1000L, 20,
                List.of(new CoursePointDto(1, 37.0, 127.0), new CoursePointDto(2, 91.0, 127.0)))));
        assertBadRequest(withRoute(new WalkSaveRequest.RecommendedRoute("추천 코스", null, 1000L, 20,
                List.of(new CoursePointDto(1, 37.0, 127.0), new CoursePointDto(2, Double.NaN, 127.0)))));
        assertBadRequest(withRoute(new WalkSaveRequest.RecommendedRoute("추천 코스", "가".repeat(1001), 1000L, 20,
                List.of(new CoursePointDto(1, 37.0, 127.0), new CoursePointDto(2, 37.1, 127.0)))));
        verify(records, never()).save(any());
    }

    private static WalkSaveRequest.RecommendedRoute route(String title, Long distanceM, int pointCount) {
        List<CoursePointDto> points = new ArrayList<>();
        for (int i = 1; i <= pointCount; i++) {
            points.add(new CoursePointDto(i, 37.0 + i / 100.0, 127.0));
        }
        return new WalkSaveRequest.RecommendedRoute(title, null, distanceM, 20, points);
    }

    private static WalkSaveRequest withRoute(WalkSaveRequest.RecommendedRoute route) {
        return new WalkSaveRequest(null, START, START + 60_000L, 100, null, route);
    }

    private void assertBadRequest(WalkSaveRequest request) {
        assertThrows(IllegalArgumentException.class, () -> walkService.saveCompletedWalk(7L, request));
    }

    private WalkRecord savedRecord() {
        ArgumentCaptor<WalkRecord> captor = ArgumentCaptor.forClass(WalkRecord.class);
        verify(records).save(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<WalkRecordPoint> savedActualPoints() {
        ArgumentCaptor<Iterable<WalkRecordPoint>> captor = ArgumentCaptor.forClass((Class) Iterable.class);
        verify(actualPoints).saveAll(captor.capture());
        return StreamSupport.stream(captor.getValue().spliterator(), false).toList();
    }
}
