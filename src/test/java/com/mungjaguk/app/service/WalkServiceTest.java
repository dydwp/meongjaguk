package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.CoursePointDto;
import com.mungjaguk.app.dto.WalkSaveRequest;
import com.mungjaguk.app.entity.WalkRecord;
import com.mungjaguk.app.entity.WalkRecordPlannedPoint;
import com.mungjaguk.app.repository.PetRepository;
import com.mungjaguk.app.repository.WalkRecordPetRepository;
import com.mungjaguk.app.repository.WalkRecordPlannedPointRepository;
import com.mungjaguk.app.repository.WalkRecordPointRepository;
import com.mungjaguk.app.repository.WalkRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalkServiceTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void completedWalkStoresRecommendedAndActualPathsSeparately() {
        WalkRecordRepository recordRepository = mock(WalkRecordRepository.class);
        WalkRecordPointRepository actualRepository = mock(WalkRecordPointRepository.class);
        WalkRecordPlannedPointRepository plannedRepository = mock(WalkRecordPlannedPointRepository.class);
        WalkRecordPetRepository walkRecordPetRepository = mock(WalkRecordPetRepository.class);
        PetRepository petRepository = mock(PetRepository.class);

        WalkService service = new WalkService(
                recordRepository,
                actualRepository,
                plannedRepository,
                walkRecordPetRepository,
                petRepository
        );

        when(recordRepository.save(any(WalkRecord.class))).thenAnswer(invocation -> {
            WalkRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "walkRecordId", 42L);
            return record;
        });

        WalkSaveRequest.RecommendedRoute recommended = new WalkSaveRequest.RecommendedRoute(
                "서울숲 반려견 산책 코스", "공원을 따라 걷는 코스", 2600L, 40,
                List.of(new CoursePointDto(2, 37.545, 127.044),
                        new CoursePointDto(1, 37.544, 127.043)));
        WalkSaveRequest request = new WalkSaveRequest(
                null,
                1_700_000_000_000L,
                1_700_000_060_000L,
                100,
                List.of(new WalkSaveRequest.Point(37.544, 127.043, 1_700_000_000_000L)),
                recommended,
                List.of()
        );

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
}
