package com.mungjaguk.app.service;

import com.mungjaguk.app.entity.WalkRecord;
import com.mungjaguk.app.entity.WalkRecordPlannedPoint;
import com.mungjaguk.app.repository.CoursePointRepository;
import com.mungjaguk.app.repository.PetRepository;
import com.mungjaguk.app.repository.RouteRepository;
import com.mungjaguk.app.repository.WalkRecordPetRepository;
import com.mungjaguk.app.repository.WalkRecordPlannedPointRepository;
import com.mungjaguk.app.repository.WalkRecordPointRepository;
import com.mungjaguk.app.repository.WalkRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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
}
