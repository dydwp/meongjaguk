package com.meongjaguk.app.service;

import com.meongjaguk.app.dto.WalkDetailView;
import com.meongjaguk.app.dto.WalkHistoryItemView;
import com.meongjaguk.app.dto.WalkPointView;
import com.meongjaguk.app.entity.ApplicationStatus;
import com.meongjaguk.app.entity.Board;
import com.meongjaguk.app.entity.CoursePoint;
import com.meongjaguk.app.entity.Pet;
import com.meongjaguk.app.entity.Route;
import com.meongjaguk.app.entity.WalkRecord;
import com.meongjaguk.app.entity.WalkRecordPlannedPoint;
import com.meongjaguk.app.entity.WalkRecordPoint;
import com.meongjaguk.app.repository.BoardRepository;
import com.meongjaguk.app.repository.CompanionRequestRepository;
import com.meongjaguk.app.repository.CoursePointRepository;
import com.meongjaguk.app.repository.PetRepository;
import com.meongjaguk.app.repository.RouteRepository;
import com.meongjaguk.app.repository.WalkRecordPetRepository;
import com.meongjaguk.app.repository.WalkRecordPlannedPointRepository;
import com.meongjaguk.app.repository.WalkRecordPointRepository;
import com.meongjaguk.app.repository.WalkRecordRepository;
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

import static com.meongjaguk.app.support.Fixtures.board;
import static com.meongjaguk.app.support.Fixtures.request;
import static com.meongjaguk.app.support.Fixtures.route;
import static com.meongjaguk.app.support.Fixtures.user;
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
                mock(PetRepository.class),
                mock(PetService.class),
                mock(CompanionRequestRepository.class),
                mock(BoardRepository.class)
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
                mock(PetRepository.class),
                mock(PetService.class),
                mock(CompanionRequestRepository.class),
                mock(BoardRepository.class)
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
    private final PetService petService = mock(PetService.class);
    private final CompanionRequestRepository requests = mock(CompanionRequestRepository.class); // 동행 산책 참가자 (추가: 김환중)
    private final BoardRepository boards = mock(BoardRepository.class); // 동행 산책 모집글 제목 (추가: 김환중)
    private final WalkRecordService service = new WalkRecordService(
            records, routes, actualPoints, plannedPoints, coursePoints, walkPets, pets, petService, requests, boards);

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

    // ---------- 동행 산책 기록 (추가: 김환중) ----------

    /** 개최자(7번)가 기록한 10번 모집의 동행 산책 기록 5번 */
    private WalkRecord meetingRecord() {
        WalkRecord record = record(5L, null, 1800, 2000);
        record.linkMeeting(10L);
        when(records.findByWalkRecordIdAndUserId(5L, 7L)).thenReturn(Optional.of(record));
        when(records.findById(5L)).thenReturn(Optional.of(record));
        return record;
    }

    private void givenApplication(long userId, ApplicationStatus status) {
        Board meeting = board(10L, user(7L, "용제"), LocalDateTime.now().minusHours(1), 4);
        when(requests.findByMeetingIdAndApplicant_UserId(10L, userId))
                .thenReturn(Optional.of(request(1L, meeting, user(userId, "민준"), status)));
    }

    @Test
    void myHistoryIncludesMeetingWalksWithMeetingTag() {
        WalkRecord meeting = record(5L, null, 1800, 2000);
        meeting.linkMeeting(10L);
        when(records.findByUserIdOrderByStartedAtDesc(7L)).thenReturn(List.of(meeting, record(1L, null, 60, 100)));
        when(walkPets.findPetNamesByUserId(7L)).thenReturn(Map.of());

        List<WalkHistoryItemView> history = service.getMyWalkHistory(7L);

        assertEquals(List.of(5L, 1L), history.stream().map(WalkHistoryItemView::id).toList());
        assertEquals(List.of("동행 산책", "개인 산책"), history.stream().map(WalkHistoryItemView::tagLabel).toList());
    }

    @Test
    void acceptedParticipantCanViewMeetingWalkWithHostPets() {
        meetingRecord();
        givenApplication(8L, ApplicationStatus.ACCEPTED);
        when(walkPets.findPetNamesByWalkRecordId(5L, 7L)).thenReturn(List.of("보리"));
        when(walkPets.findPetIdsByWalkRecordId(5L, 7L)).thenReturn(List.of(1L));
        when(actualPoints.findByWalkRecordIdOrderBySequenceNoAsc(5L)).thenReturn(List.of(
                WalkRecordPoint.of(5L, 1, 37.1, 127.1, LocalDateTime.now()),
                WalkRecordPoint.of(5L, 2, 37.2, 127.2, LocalDateTime.now())));

        WalkDetailView detail = service.getDetail(5L, 8L).orElseThrow();

        assertEquals("동행 산책", detail.tagLabel());
        assertEquals("보리", detail.petNamesLabel()); // 조회자가 아니라 기록 주인의 반려견
        assertFalse(detail.deletable());
        assertEquals(2, service.getWalkPoints(5L, 8L).size());
        service.getWalkPets(5L, 8L);
        verify(petService).getMyPetsByIds(7L, List.of(1L));
    }

    @Test
    void pendingRejectedOrUnrelatedUsersCannotViewMeetingWalk() {
        meetingRecord();
        givenApplication(8L, ApplicationStatus.PENDING);
        givenApplication(9L, ApplicationStatus.REJECTED);

        for (long userId : new long[]{8L, 9L, 11L}) {
            assertTrue(service.getDetail(5L, userId).isEmpty(), "user " + userId);
            assertTrue(service.getWalkPoints(5L, userId).isEmpty());
            assertTrue(service.getPlannedPoints(5L, userId).isEmpty());
            assertTrue(service.getWalkPets(5L, userId).isEmpty());
        }
        verify(actualPoints, never()).findByWalkRecordIdOrderBySequenceNoAsc(5L);
        verify(plannedPoints, never()).findByWalkRecordIdOrderBySequenceNoAsc(5L);
        verify(walkPets, never()).findPetIdsByWalkRecordId(any(), any());
    }

    @Test
    void othersPersonalWalkIsHiddenEvenFromAcceptedParticipant() {
        when(records.findById(1L)).thenReturn(Optional.of(record(1L, null, 60, 100))); // meetingId 없음

        assertTrue(service.getDetail(1L, 8L).isEmpty());
        verify(requests, never()).findByMeetingIdAndApplicant_UserId(any(), any());
    }

    @Test
    void hostSeesMeetingWalkButCannotDeleteIt() {
        WalkRecord record = meetingRecord();

        WalkDetailView detail = service.getDetail(5L, 7L).orElseThrow();
        assertEquals("동행 산책", detail.tagLabel());
        assertFalse(detail.deletable());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.deleteMyCompletedWalkRecord(5L, 7L));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertEquals("동행 산책 기록은 삭제할 수 없습니다.", e.getReason());
        verify(records, never()).delete(record);
        verify(actualPoints, never()).deleteByWalkRecordId(5L);
        verify(plannedPoints, never()).deleteByWalkRecordId(5L);
    }
    // ---------- 활동 내역 동행 산책 표시 (추가: 김환중) ----------

    private WalkRecord startedAt(WalkRecord record, LocalDateTime startedAt) {
        ReflectionTestUtils.setField(record, "startedAt", startedAt);
        return record;
    }

    /** 10번 모집(제목 "모집 10")에 연결된 개최자(7번)의 동행 기록 5번, 9월 30일 시작 */
    private WalkRecord meetingWalk() {
        WalkRecord record = startedAt(record(5L, null, 1800, 2000), LocalDateTime.of(2026, 9, 30, 8, 0));
        record.linkMeeting(10L);
        when(boards.findAllById(List.of(10L)))
                .thenReturn(List.of(board(10L, user(7L, "용제"), LocalDateTime.now().minusHours(1), 4)));
        return record;
    }

    private List<Long> ids(List<WalkHistoryItemView> history) {
        return history.stream().map(WalkHistoryItemView::id).toList();
    }

    @Test
    void hostHistoryMixesPersonalAndMeetingWalksByStartTime() {
        WalkRecord meeting = meetingWalk();
        WalkRecord newer = startedAt(record(1L, null, 600, 500), LocalDateTime.of(2026, 10, 1, 9, 0));
        WalkRecord older = startedAt(record(2L, null, 600, 500), LocalDateTime.of(2026, 9, 28, 9, 0));
        when(records.findByUserIdOrderByStartedAtDesc(7L)).thenReturn(List.of(older, meeting, newer));
        when(walkPets.findPetNamesByUserId(7L)).thenReturn(Map.of(5L, List.of("보리")));
        // 같은 기록이 수락된 모집 쪽에서 또 나와도 한 번만
        when(requests.findMeetingIdsByApplicantAndStatus(7L, ApplicationStatus.ACCEPTED)).thenReturn(List.of(10L));
        when(records.findByMeetingIdIn(List.of(10L))).thenReturn(List.of(meeting));

        List<WalkHistoryItemView> history = service.getMyWalkHistory(7L);

        assertEquals(List.of(1L, 5L, 2L), ids(history));
        assertEquals(List.of("개인 산책", "동행 산책", "개인 산책"),
                history.stream().map(WalkHistoryItemView::tagLabel).toList());
        assertEquals(List.of("자유 산책", "모집 10", "자유 산책"),
                history.stream().map(WalkHistoryItemView::title).toList());
        assertEquals("보리", history.get(1).petNamesLabel()); // 개최자에게는 함께한 반려견 표시
    }

    @Test
    void acceptedParticipantSeesHostsMeetingWalkWithoutPetNames() {
        WalkRecord meeting = meetingWalk();
        WalkRecord mineNewer = startedAt(record(21L, null, 600, 500), LocalDateTime.of(2026, 10, 1, 9, 0));
        WalkRecord mineOlder = startedAt(record(20L, null, 600, 500), LocalDateTime.of(2026, 9, 29, 9, 0));
        when(records.findByUserIdOrderByStartedAtDesc(8L)).thenReturn(List.of(mineNewer, mineOlder));
        when(requests.findMeetingIdsByApplicantAndStatus(8L, ApplicationStatus.ACCEPTED)).thenReturn(List.of(10L));
        when(records.findByMeetingIdIn(List.of(10L))).thenReturn(List.of(meeting));
        when(walkPets.findPetNamesByUserId(8L)).thenReturn(Map.of(20L, List.of("코코")));
        when(walkPets.findPetNamesByUserId(7L)).thenReturn(Map.of(5L, List.of("보리")));

        List<WalkHistoryItemView> history = service.getMyWalkHistory(8L);

        assertEquals(List.of(21L, 5L, 20L), ids(history));
        assertEquals("동행 산책", history.get(1).tagLabel());
        assertEquals("모집 10", history.get(1).title());
        assertEquals("", history.get(1).petNamesLabel()); // 참가자에게는 반려견 표시 안 함
        assertEquals("코코", history.get(2).petNamesLabel());
    }

    @Test
    void pendingRejectedOrUnrelatedUsersDoNotSeeMeetingWalk() {
        WalkRecord meeting = meetingWalk();
        when(records.findByMeetingIdIn(List.of(10L))).thenReturn(List.of(meeting));
        // 대기·거절 신청은 수락(ACCEPTED) 모집 목록에 들어가지 않음
        when(requests.findMeetingIdsByApplicantAndStatus(8L, ApplicationStatus.PENDING)).thenReturn(List.of(10L));
        when(requests.findMeetingIdsByApplicantAndStatus(9L, ApplicationStatus.REJECTED)).thenReturn(List.of(10L));

        for (long userId : new long[]{8L, 9L, 11L}) {
            assertTrue(service.getMyWalkHistory(userId).isEmpty(), "user " + userId);
            verify(requests).findMeetingIdsByApplicantAndStatus(userId, ApplicationStatus.ACCEPTED);
        }
        verify(records, never()).findByMeetingIdIn(any());
    }

    @Test
    void petFilterKeepsHostsMeetingWalkButNotParticipantsSide() {
        WalkRecord meeting = meetingWalk();
        when(records.findByUserIdOrderByStartedAtDesc(7L)).thenReturn(List.of(meeting, record(1L, null, 600, 500)));
        when(pets.findByPetIdAndUser_UserId(9L, 7L)).thenReturn(Optional.of(new Pet()));
        when(walkPets.findWalkRecordIdsByPetId(7L, 9L)).thenReturn(Set.of(5L)); // 개최자가 9번 반려견과 함께한 동행 기록
        when(walkPets.findPetNamesByUserId(7L)).thenReturn(Map.of());

        assertEquals(List.of(5L), ids(service.getMyWalkHistory(7L, 9L)));

        when(records.findByUserIdOrderByStartedAtDesc(8L)).thenReturn(List.of(record(20L, null, 600, 500)));
        when(requests.findMeetingIdsByApplicantAndStatus(8L, ApplicationStatus.ACCEPTED)).thenReturn(List.of(10L));
        when(records.findByMeetingIdIn(List.of(10L))).thenReturn(List.of(meeting));
        when(pets.findByPetIdAndUser_UserId(3L, 8L)).thenReturn(Optional.of(new Pet()));
        when(walkPets.findWalkRecordIdsByPetId(8L, 3L)).thenReturn(Set.of(20L)); // 참가자 반려견은 동행 기록에 연결되지 않음
        when(walkPets.findPetNamesByUserId(8L)).thenReturn(Map.of());

        assertEquals(List.of(20L), ids(service.getMyWalkHistory(8L, 3L)));
    }

    @Test
    void meetingWalkDetailTitleIsMeetingTitle() {
        meetingRecord();
        givenApplication(8L, ApplicationStatus.ACCEPTED);
        when(boards.findById(10L))
                .thenReturn(Optional.of(board(10L, user(7L, "용제"), LocalDateTime.now().minusHours(1), 4)));

        assertEquals("모집 10", service.getDetail(5L, 7L).orElseThrow().title());
        assertEquals("모집 10", service.getDetail(5L, 8L).orElseThrow().title());
    }
}
