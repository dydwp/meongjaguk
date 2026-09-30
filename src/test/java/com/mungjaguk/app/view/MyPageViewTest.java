package com.mungjaguk.app.view;

import com.mungjaguk.app.controller.MyPageController;
import com.mungjaguk.app.controller.WalkController;
import com.mungjaguk.app.dto.MeetingRequestView;
import com.mungjaguk.app.dto.MyCompanionRequestView;
import com.mungjaguk.app.dto.MySharedMeetingView;
import com.mungjaguk.app.dto.PetCardView;
import com.mungjaguk.app.dto.WalkDetailView;
import com.mungjaguk.app.dto.WalkHistoryItemView;
import com.mungjaguk.app.dto.WalkPointView;
import com.mungjaguk.app.service.CompanionService;
import com.mungjaguk.app.service.MyPageService;
import com.mungjaguk.app.service.PetService;
import com.mungjaguk.app.service.UserService;
import com.mungjaguk.app.service.WalkRecordService;
import com.mungjaguk.app.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.mungjaguk.app.support.Fixtures.user;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 마이페이지(member/mypage.html)와 활동 상세(member/activity.html) 화면 */
@WebMvcTest({MyPageController.class, WalkController.class})
class MyPageViewTest extends WebTestSupport {

    @MockitoBean UserService userService;
    @MockitoBean PetService petService;
    @MockitoBean WalkRecordService walkRecordService;
    @MockitoBean MyPageService myPageService;
    @MockitoBean CompanionService companionService;

    private final LocalDateTime future = LocalDateTime.of(2099, 10, 3, 19, 0);
    private final LocalDateTime past = LocalDateTime.of(2020, 1, 1, 9, 0);

    @BeforeEach
    void givenMember() {
        when(userService.findById(USER_ID)).thenReturn(user(USER_ID, "용제"));
    }

    @Test
    void emptyMyPageShowsEmptyMessages() throws Exception {
        mvc.perform(get("/mypage").with(login()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("2026.09 가입 · 카카오 계정"),
                        containsString("새 반려견 등록"),
                        containsString("아직 공유한 산책 모집 글이 없습니다."),
                        containsString("아직 산책 기록이 없습니다."),
                        containsString("0건 · 대기 0건"))));
    }

    @Test
    void petCardsLinkToEditPage() throws Exception {
        when(petService.getMyPets(USER_ID)).thenReturn(List.of(
                new PetCardView(1L, "보리", "말티즈", "소형견", 3, "/images/pets/bori.png", "높음"),
                new PetCardView(2L, "초코", "", "", 0, null, "")));

        mvc.perform(get("/mypage").with(login()))
                .andExpect(content().string(allOf(
                        containsString("href=\"/pet-profile?id=1\""),
                        containsString("src=\"/images/pets/bori.png\""),
                        containsString("말티즈 · 소형견 · 3세"),
                        containsString("활동성 높음"),
                        containsString("🐾"))));
    }

    @Test
    void sharedMeetingsAreListed() throws Exception {
        when(myPageService.getMySharedMeetings(USER_ID)).thenReturn(List.of(
                new MySharedMeetingView(10L, "저녁 산책 모임", "서울숲 코스", future.toLocalDate(), future.toLocalTime(),
                        2, 5, "RECRUITING")));

        mvc.perform(get("/mypage").param("tab", "shared").with(login()))
                .andExpect(content().string(allOf(
                        containsString("href=\"/course-detail-shared?meetingId=10\""),
                        containsString("저녁 산책 모임"),
                        containsString("모집 중"),
                        containsString("2099.10.03 19:00"),
                        containsString("참여 2 / 정원 5"),
                        not(containsString("아직 공유한 산책 모집 글이 없습니다.")))));
    }

    @Test
    void onlyActionableRequestsHaveButtons() throws Exception {
        when(myPageService.getMeetingRequests(USER_ID)).thenReturn(List.of(
                new MeetingRequestView(1L, 10L, "저녁 산책", "민준", "같이 가요", future.toLocalDate(), future.toLocalTime(), "PENDING"),
                new MeetingRequestView(2L, 10L, "저녁 산책", "서연", null, future.toLocalDate(), future.toLocalTime(), "ACCEPTED"),
                new MeetingRequestView(3L, 11L, "지난 산책", "지훈", null, past.toLocalDate(), past.toLocalTime(), "PENDING")));

        mvc.perform(get("/mypage").param("tab", "requests").with(login()))
                .andExpect(content().string(allOf(
                        containsString("2건 · 대기 1건"),
                        containsString("action=\"/mypage/requests/1/accept\""),
                        containsString("action=\"/mypage/requests/1/reject\""),
                        not(containsString("/mypage/requests/2/accept")),
                        not(containsString("/mypage/requests/3/accept")),
                        containsString("name=\"_csrf\""),            // 폼마다 CSRF 토큰
                        containsString("저녁 산책 동행을 신청했어요"),
                        containsString("같이 가요"),
                        containsString(">수락됨<"),
                        containsString("마감됨"))));
    }

    @Test
    void mySentRequestsShowHostAndSchedule() throws Exception {
        when(myPageService.getMyCompanionRequests(USER_ID)).thenReturn(List.of(
                new MyCompanionRequestView(4L, 20L, "주말 산책", "한강 코스", "서연", future.toLocalDate(), future.toLocalTime(), "PENDING")));

        mvc.perform(get("/mypage").param("tab", "requests").with(login()))
                .andExpect(content().string(allOf(
                        containsString("주말 산책"),
                        containsString("서연"),
                        containsString("2099.10.03 19:00"),
                        containsString("신청 대기"))));
    }

    @Test
    void flashMessagesAreShown() throws Exception {
        mvc.perform(get("/mypage").param("tab", "requests").with(login())
                        .flashAttr("errorMessage", "모집 정원이 가득 찼습니다.")
                        .flashAttr("successMessage", "참여 신청을 수락했습니다."))
                .andExpect(content().string(allOf(
                        containsString("모집 정원이 가득 찼습니다."),
                        containsString("참여 신청을 수락했습니다."))));
    }

    @Test
    void activityListWithPetFilter() throws Exception {
        when(petService.getMyPets(USER_ID)).thenReturn(List.of(
                new PetCardView(9L, "보리", "말티즈", "소형견", 3, null, "")));
        when(walkRecordService.isMyPet(USER_ID, 9L)).thenReturn(true);
        when(walkRecordService.getMyWalkHistory(USER_ID, 9L)).thenReturn(List.of(
                new WalkHistoryItemView(3L, "한강 코스", "개인 산책", "보리", "2.3km", "약 35분", "2026.09.19")));

        mvc.perform(get("/mypage").param("tab", "activity").param("petId", "9").with(login()))
                .andExpect(content().string(allOf(
                        containsString("href=\"/activity-detail?id=3&amp;petId=9\""),
                        containsString("href=\"/mypage?tab=activity&amp;petId=9\""),
                        containsString("함께 산책한 반려견: 보리"),
                        containsString("약 35분"),
                        containsString("2026.09.19"))));
    }

    // ---------- 활동 상세 ----------

    @Test
    void activityDetailShowsRecordAndDeleteButton() throws Exception {
        when(walkRecordService.getDetail(3L, USER_ID)).thenReturn(Optional.of(new WalkDetailView(3L, "추천 코스",
                "개인 산책", true, true, "공원을 따라 걷는 코스", "거리 · 2.3km", "예상 소요시간 · 약 35분",
                "보리 · 초코", "2.4km", "38:12", "2026.09.29 완료")));
        when(walkRecordService.getWalkPoints(3L, USER_ID)).thenReturn(List.of(
                new WalkPointView(new BigDecimal("37.5440000"), new BigDecimal("127.0430000"))));

        mvc.perform(get("/activity-detail").param("id", "3").with(login()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("추천 코스"),
                        containsString("거리 · 2.3km"),
                        containsString("예상 소요시간 · 약 35분"),
                        containsString("공원을 따라 걷는 코스"),
                        containsString("38:12"),
                        containsString("함께 산책한 반려견: 보리 · 초코"),
                        containsString("2026.09.29 완료"),
                        containsString("data-lat=\"37.5440000\""),
                        containsString("action=\"/activity-detail/3/delete\""),
                        containsString("기록 삭제"))));
    }

    @Test
    void freeWalkDetailHidesPlanAndDelete() throws Exception {
        when(walkRecordService.getDetail(1L, USER_ID)).thenReturn(Optional.of(new WalkDetailView(1L, "자유 산책",
                "개인 산책", false, false, "", "거리 · -", "예상 소요시간 · -", "", "0.1km", "01:05", "산책 중")));

        mvc.perform(get("/activity-detail").param("id", "1").with(login()))
                .andExpect(content().string(allOf(
                        containsString("자유 산책"),
                        not(containsString("예상 소요시간")),
                        not(containsString("기록 삭제")),
                        not(containsString("함께 산책한 반려견")))));
    }
}
