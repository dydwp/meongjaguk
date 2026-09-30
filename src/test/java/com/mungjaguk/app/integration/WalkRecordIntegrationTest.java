package com.mungjaguk.app.integration;

import com.mungjaguk.app.dto.CoursePointDto;
import com.mungjaguk.app.dto.WalkDetailView;
import com.mungjaguk.app.dto.WalkHistoryItemView;
import com.mungjaguk.app.dto.WalkPointView;
import com.mungjaguk.app.dto.WalkSaveRequest;
import com.mungjaguk.app.entity.Pet;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.entity.WalkRecord;
import com.mungjaguk.app.repository.WalkRecordPetRepository;
import com.mungjaguk.app.repository.WalkRecordPlannedPointRepository;
import com.mungjaguk.app.repository.WalkRecordPointRepository;
import com.mungjaguk.app.repository.WalkRecordRepository;
import com.mungjaguk.app.service.WalkRecordService;
import com.mungjaguk.app.service.WalkService;
import com.mungjaguk.app.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 산책 저장(WalkService) → 활동 내역 조회/삭제(WalkRecordService)를 실제 DB로 */
class WalkRecordIntegrationTest extends IntegrationTestSupport {

    private static final long START = 1_700_000_000_000L;

    @Autowired WalkService walkService;
    @Autowired WalkRecordService walkRecordService;
    @Autowired WalkRecordRepository records;
    @Autowired WalkRecordPointRepository actualPoints;
    @Autowired WalkRecordPlannedPointRepository plannedPoints;
    @Autowired WalkRecordPetRepository walkPets;

    private User me;

    @BeforeEach
    void setUp() {
        me = data.user("용제");
    }

    @Test
    void savedRecommendedWalkShowsInHistoryAndDetail() {
        WalkSaveRequest request = new WalkSaveRequest(null, START, START + 2_292_000L, 2400,
                List.of(new WalkSaveRequest.Point(37.5440001, 127.0430001, START),
                        new WalkSaveRequest.Point(37.5450002, 127.0440002, START + 60_000L)),
                new WalkSaveRequest.RecommendedRoute(" 서울숲 코스 ", "공원을 따라 걷는 코스", 2300L, 35, List.of(
                        new CoursePointDto(2, 37.545, 127.044),
                        new CoursePointDto(1, 37.544, 127.043))));

        Long id = walkService.saveCompletedWalk(me.getUserId(), request);
        flushAndClear();

        WalkHistoryItemView item = walkRecordService.getMyWalkHistory(me.getUserId()).get(0);
        assertEquals(id, item.id());
        assertEquals("서울숲 코스", item.title());
        assertEquals("2.4km", item.distanceLabel());

        WalkDetailView detail = walkRecordService.getDetail(id, me.getUserId()).orElseThrow();
        assertEquals("거리 · 2.3km", detail.plannedDistanceLabel());
        assertEquals("38:12", detail.actualDurationLabel());

        List<WalkPointView> walked = walkRecordService.getWalkPoints(id, me.getUserId());
        assertEquals(2, walked.size());
        assertEquals("37.5450002", walked.get(1).latitude().toPlainString()); // 소수 7자리 그대로 저장
        List<WalkPointView> planned = walkRecordService.getPlannedPoints(id, me.getUserId());
        assertEquals("37.5440000", planned.get(0).latitude().toPlainString());
    }

    @Test
    void walkOnRegisteredCourseUsesCoursePointsAsPlan() {
        var course = data.route("한강 코스");
        data.coursePoints(course, 37.1, 127.1, 37.2, 127.2, 37.3, 127.3);
        Long id = walkService.saveCompletedWalk(me.getUserId(), new WalkSaveRequest(
                course.getCourseId().longValue(), START, START + 600_000L, 1000, null, null));
        flushAndClear();

        assertEquals(3, walkRecordService.getPlannedPoints(id, me.getUserId()).size());
        assertEquals("한강 코스", walkRecordService.getDetail(id, me.getUserId()).orElseThrow().title());
    }

    @Test
    void petNamesAndPetFilterUseLinkTable() {
        Pet bori = data.pet(me, "보리");
        Pet choco = data.pet(me, "초코");
        WalkRecord both = data.walk(me, LocalDateTime.now().minusDays(1), 600, 800);
        WalkRecord onlyChoco = data.walk(me, LocalDateTime.now(), 600, 800);
        data.linkPet(both, bori);
        data.linkPet(both, choco);
        data.linkPet(onlyChoco, choco);
        User other = data.user("남");
        data.linkPet(data.walk(other, LocalDateTime.now(), 60, 10), data.pet(other, "남의 개"));
        flushAndClear();

        Map<Long, List<String>> names = walkPets.findPetNamesByUserId(me.getUserId());
        assertEquals(List.of("보리", "초코"), names.get(both.getWalkRecordId()));
        assertEquals(Set.of(both.getWalkRecordId()), walkPets.findWalkRecordIdsByPetId(me.getUserId(), bori.getPetId()));

        List<WalkHistoryItemView> filtered = walkRecordService.getMyWalkHistory(me.getUserId(), bori.getPetId());
        assertEquals(List.of(both.getWalkRecordId()), filtered.stream().map(WalkHistoryItemView::id).toList());
        assertEquals("보리 · 초코", filtered.get(0).petNamesLabel());
        assertEquals(2, walkRecordService.getMyWalkHistory(me.getUserId()).size()); // 남의 기록은 안 보임
    }

    @Test
    void deleteRemovesRecordAndAllPoints() {
        Long id = walkService.saveCompletedWalk(me.getUserId(), new WalkSaveRequest(null, START, START + 60_000L, 100,
                List.of(new WalkSaveRequest.Point(37.1, 127.1, START)),
                new WalkSaveRequest.RecommendedRoute("추천", null, 500L, 10, List.of(
                        new CoursePointDto(1, 37.1, 127.1), new CoursePointDto(2, 37.2, 127.2)))));
        flushAndClear();

        walkRecordService.deleteMyCompletedWalkRecord(id, me.getUserId());
        flushAndClear();

        assertTrue(records.findById(id).isEmpty());
        assertTrue(actualPoints.findByWalkRecordIdOrderBySequenceNoAsc(id).isEmpty());
        assertTrue(plannedPoints.findByWalkRecordIdOrderBySequenceNoAsc(id).isEmpty());
    }

    @Test
    void othersCannotSeeMyWalk() {
        WalkRecord mine = data.walk(me, LocalDateTime.now(), 60, 100);
        User stranger = data.user("남");
        flushAndClear();

        assertTrue(walkRecordService.getDetail(mine.getWalkRecordId(), stranger.getUserId()).isEmpty());
        assertTrue(walkRecordService.getMyWalkHistory(stranger.getUserId()).isEmpty());
    }
}
