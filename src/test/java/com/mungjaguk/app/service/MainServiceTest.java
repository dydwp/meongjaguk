package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.MeetCardDto;
import com.mungjaguk.app.dto.WeeklyWalkSummary;
import com.mungjaguk.app.entity.ApplicationStatus;
import com.mungjaguk.app.entity.Board;
import com.mungjaguk.app.entity.BoardStatus;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.entity.WalkRecord;
import com.mungjaguk.app.repository.BoardRepository;
import com.mungjaguk.app.repository.CompanionRequestRepository;
import com.mungjaguk.app.repository.PetRepository;
import com.mungjaguk.app.repository.WalkRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static com.mungjaguk.app.support.Fixtures.board;
import static com.mungjaguk.app.support.Fixtures.pet;
import static com.mungjaguk.app.support.Fixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MainServiceTest {

    private WalkRecordRepository walkRecords;
    private PetRepository pets;
    private BoardRepository boards;
    private CompanionRequestRepository requests;
    private MainService service;

    private final User host = user(1L, "용제");

    @BeforeEach
    void setUp() {
        walkRecords = mock(WalkRecordRepository.class);
        pets = mock(PetRepository.class);
        boards = mock(BoardRepository.class);
        requests = mock(CompanionRequestRepository.class);
        service = new MainService(walkRecords, pets, boards, requests);
    }

    // ---------- 이번 주 나의 산책 ----------

    @Test
    void weeklySummaryAddsUpThisWeeksWalks() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        WalkRecord first = WalkRecord.completed(7L, null, today, today.plusMinutes(10), 600, 1200);
        WalkRecord second = WalkRecord.completed(7L, null, today.plusHours(1), today.plusHours(2), 930, 2600);
        when(walkRecords.findByUserIdAndStatusAndStartedAtGreaterThanEqual(eq(7L), eq("COMPLETED"), any()))
                .thenReturn(List.of(first, second));
        when(walkRecords.findFirstByUserIdAndStatusOrderByStartedAtDesc(7L, "COMPLETED"))
                .thenReturn(Optional.of(second));

        WeeklyWalkSummary summary = service.getWeeklyWalkSummary(7L);

        assertEquals(2, summary.walkCount());
        assertEquals(3.8, summary.distanceKm(), 0.0001);
        assertEquals(26, summary.minutes()); // 1530초 = 25.5분 → 반올림
        assertEquals("오늘 2.6km", summary.lastWalkLabel());
    }

    @Test
    void weeklySummaryStartsFromMondayMidnight() {
        when(walkRecords.findFirstByUserIdAndStatusOrderByStartedAtDesc(7L, "COMPLETED"))
                .thenReturn(Optional.empty());

        service.getWeeklyWalkSummary(7L);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(walkRecords).findByUserIdAndStatusAndStartedAtGreaterThanEqual(eq(7L), eq("COMPLETED"), from.capture());
        assertEquals(DayOfWeek.MONDAY, from.getValue().getDayOfWeek());
        assertEquals(LocalTime.MIDNIGHT, from.getValue().toLocalTime());
        assertFalse(from.getValue().toLocalDate().isAfter(LocalDate.now()));
        assertTrue(from.getValue().toLocalDate().isAfter(LocalDate.now().minusDays(7)));
    }

    @Test
    void weeklySummaryWithoutWalksIsEmpty() {
        when(walkRecords.findFirstByUserIdAndStatusOrderByStartedAtDesc(7L, "COMPLETED"))
                .thenReturn(Optional.empty());

        WeeklyWalkSummary summary = service.getWeeklyWalkSummary(7L);

        assertEquals(0, summary.walkCount());
        assertEquals(0.0, summary.distanceKm());
        assertEquals(0, summary.minutes());
        assertNull(summary.lastWalkLabel());
    }

    @Test
    void lastWalkLabelShowsYesterdayOrDate() {
        LocalDateTime yesterday = LocalDate.now().minusDays(1).atTime(9, 0);
        when(walkRecords.findFirstByUserIdAndStatusOrderByStartedAtDesc(7L, "COMPLETED"))
                .thenReturn(Optional.of(WalkRecord.completed(7L, null, yesterday, yesterday.plusMinutes(20), 1200, 1500)));
        assertEquals("어제 1.5km", service.getWeeklyWalkSummary(7L).lastWalkLabel());

        LocalDateTime older = LocalDate.now().minusDays(5).atTime(9, 0);
        when(walkRecords.findFirstByUserIdAndStatusOrderByStartedAtDesc(7L, "COMPLETED"))
                .thenReturn(Optional.of(WalkRecord.completed(7L, null, older, older.plusMinutes(40), 2400, 3000)));
        String expectedDate = older.getMonthValue() + "/" + older.getDayOfMonth();
        assertEquals(expectedDate + " 3.0km", service.getWeeklyWalkSummary(7L).lastWalkLabel());
    }

    // ---------- 인사말 반려견 이름 ----------

    @Test
    void petNameGetsRightParticle() {
        assertEquals("밤이랑", petWith("밤"));      // 받침 있음
        assertEquals("보리랑", petWith("보리"));    // 받침 없음
        assertEquals("Coco랑", petWith("Coco"));   // 한글이 아니면 "랑"
        assertEquals("초코랑", petWith("  초코 ")); // 앞뒤 공백 제거
    }

    @Test
    void noPetOrBlankNameGivesNull() {
        when(pets.findByUser_UserIdOrderByPetIdAsc(7L)).thenReturn(List.of());
        assertNull(service.getPetWith(7L));

        assertNull(petWith("   "));
    }

    @Test
    void firstRegisteredPetIsUsed() {
        when(pets.findByUser_UserIdOrderByPetIdAsc(7L))
                .thenReturn(List.of(pet(1L, host, "뭉치"), pet(2L, host, "보리")));

        assertEquals("뭉치랑", service.getPetWith(7L));
    }

    private String petWith(String name) {
        when(pets.findByUser_UserIdOrderByPetIdAsc(7L)).thenReturn(List.of(pet(1L, host, name)));
        return service.getPetWith(7L);
    }

    // ---------- 같이 걷기 모집 카드 ----------

    @Test
    void noBoardsGivesEmptyListWithoutCounting() {
        when(boards.findTop6ByOrderByCreatedAtDescMeetingIdDesc()).thenReturn(List.of());

        assertTrue(service.getRecentMeets(3).isEmpty());
        verify(requests, never()).countByMeetingIdsAndStatus(anyCollection(), any());
    }

    @Test
    void recruitingCardsComeFirstAndKeepNewestOrder() {
        LocalDateTime future = LocalDateTime.now().plusDays(2);
        Board newestClosed = board(6L, host, future, 4);
        ReflectionTestUtils.setField(newestClosed, "status", BoardStatus.CLOSED);
        Board full = board(5L, host, future, 2);              // 1(작성자) + 수락 1 = 정원
        Board open = board(4L, host, future, 4);
        Board past = board(3L, host, LocalDateTime.now().minusHours(1), 4);
        Board olderOpen = board(2L, host, future, 5);
        when(boards.findTop6ByOrderByCreatedAtDescMeetingIdDesc())
                .thenReturn(List.of(newestClosed, full, open, past, olderOpen));
        when(requests.countByMeetingIdsAndStatus(List.of(6L, 5L, 4L, 3L, 2L), ApplicationStatus.ACCEPTED))
                .thenReturn(List.<Object[]>of(new Object[]{5L, 1L}, new Object[]{4L, 2L}));

        List<MeetCardDto> cards = service.getRecentMeets(3);

        assertEquals(List.of(4L, 2L, 6L), cards.stream().map(MeetCardDto::getMeetingId).toList());
        assertEquals(3, cards.get(0).getCurrentParticipants()); // 1 + 수락 2
        assertEquals(1, cards.get(1).getCurrentParticipants()); // 수락 없음
        assertEquals("서울숲 코스", cards.get(0).getCourseName());
        assertFalse(cards.get(0).isClosed());
        assertTrue(cards.get(2).isClosed());
    }

    @Test
    void closedCardsFillWhenNotEnoughRecruiting() {
        Board past = board(3L, host, LocalDateTime.now().minusDays(1), 4);
        when(boards.findTop6ByOrderByCreatedAtDescMeetingIdDesc()).thenReturn(List.of(past));
        when(requests.countByMeetingIdsAndStatus(List.of(3L), ApplicationStatus.ACCEPTED)).thenReturn(List.of());

        List<MeetCardDto> cards = service.getRecentMeets(3);

        assertEquals(1, cards.size());
        assertTrue(cards.get(0).isClosed());
    }
}
