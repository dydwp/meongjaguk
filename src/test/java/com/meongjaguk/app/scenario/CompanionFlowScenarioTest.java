package com.meongjaguk.app.scenario;

import com.meongjaguk.app.entity.CompanionRequest;
import com.meongjaguk.app.entity.User;
import com.meongjaguk.app.repository.CompanionRequestRepository;
import com.meongjaguk.app.support.ScenarioTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 같이 걷기 전체 흐름
 * 모집 등록 → 신청 → 작성자 알림 → 마이페이지에서 수락 → 신청자 알림 → 정원 마감
 */
class CompanionFlowScenarioTest extends ScenarioTestSupport {

    @Autowired CompanionRequestRepository requests;

    private User host;
    private User minjun;
    private User seoyeon;

    @BeforeEach
    void setUp() {
        host = data.user("용제");
        minjun = data.user("민준");
        seoyeon = data.user("서연");
    }

    @Test
    void recruitApplyAcceptUntilFull() throws Exception {
        // 1) 작성자가 AI 추천 코스로 2명(본인 포함) 모집 글 등록
        long meetingId = createMeeting(2);

        mvc.perform(get("/api/meetings/" + meetingId))                       // 비회원도 상세 조회
                .andExpect(jsonPath("$.status").value("RECRUITING"))
                .andExpect(jsonPath("$.currentParticipants").value(1))
                .andExpect(jsonPath("$.points.length()").value(2));

        // 2) 민준 신청 → 중복 신청은 409
        mvc.perform(post("/api/meetings/" + meetingId + "/applications").with(as(minjun)).with(csrf()))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/meetings/" + meetingId + "/applications").with(as(minjun)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 신청한 모집이에요."));

        // 3) 작성자 알림: 새 동행 신청 → 마이페이지 받은 신청 탭으로 이동
        mvc.perform(get("/api/notifications").with(as(host)))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].title").value("새 동행 신청이 왔어요"))
                .andExpect(jsonPath("$.items[0].link").value("/mypage?tab=requests"));

        // 4) 작성자 마이페이지에 수락/거절 버튼
        long applicationId = requests.findByMeetingIdAndApplicant_UserId(meetingId, minjun.getUserId())
                .map(CompanionRequest::getId).orElseThrow();
        mvc.perform(get("/mypage").param("tab", "requests").with(as(host)))
                .andExpect(content().string(allOf(
                        containsString("1건 · 대기 1건"),
                        containsString("/mypage/requests/" + applicationId + "/accept"))));

        // 5) 수락 → 신청자에게 알림, 참여 인원 2명 = 정원 → 마감 표시
        mvc.perform(post("/mypage/requests/" + applicationId + "/accept").with(as(host)).with(csrf()))
                .andExpect(redirectedUrl("/mypage?tab=requests"))
                .andExpect(flash().attribute("successMessage", "참여 신청을 수락했습니다."));

        mvc.perform(get("/api/notifications").with(as(minjun)))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].link").value("/course-detail-shared?meetingId=" + meetingId));

        mvc.perform(get("/api/meetings/" + meetingId).with(as(minjun)))
                .andExpect(jsonPath("$.myApplicationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.participantNicknames[1]").value("민준"))
                .andExpect(jsonPath("$.currentParticipants").value(2))
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // 6) 수락된 신청은 취소 불가, 정원이 찬 모집은 추가 신청 불가
        mvc.perform(delete("/api/meetings/" + meetingId + "/applications").with(as(minjun)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("대기 중인 신청만 취소할 수 있어요."));
        mvc.perform(post("/api/meetings/" + meetingId + "/applications").with(as(seoyeon)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("정원이 다 차서 신청할 수 없어요."));

        // 7) 알림 창을 열면 읽음 처리
        mvc.perform(post("/api/notifications/read").with(as(minjun)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").with(as(minjun)))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.items[0].read").value(true));
    }

    @Test
    void applicantCancelsAndHostRejects() throws Exception {
        long meetingId = createMeeting(4);

        // 민준: 신청했다가 취소 → 다시 신청 가능
        mvc.perform(post("/api/meetings/" + meetingId + "/applications").with(as(minjun)).with(csrf()))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/meetings/" + meetingId + "/applications").with(as(minjun)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/meetings/" + meetingId).with(as(minjun)))
                .andExpect(jsonPath("$.myApplicationStatus").doesNotExist());

        // 서연: 신청 → 거절 → 서연 마이페이지 "거절됨"
        mvc.perform(post("/api/meetings/" + meetingId + "/applications").with(as(seoyeon)).with(csrf()))
                .andExpect(status().isCreated());
        long applicationId = requests.findByMeetingIdAndApplicant_UserId(meetingId, seoyeon.getUserId())
                .map(CompanionRequest::getId).orElseThrow();

        // 작성자가 아닌 사람은 거절 불가
        mvc.perform(post("/mypage/requests/" + applicationId + "/reject").with(as(minjun)).with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/mypage/requests/" + applicationId + "/reject").with(as(host)).with(csrf()))
                .andExpect(flash().attribute("rejectMessage", "참여 신청을 거절했습니다."));
        mvc.perform(get("/mypage").param("tab", "requests").with(as(seoyeon)))
                .andExpect(content().string(allOf(containsString("주말 산책"), containsString("거절됨"))));

        // 작성자는 본인 모집에 신청할 수 없음
        mvc.perform(post("/api/meetings/" + meetingId + "/applications").with(as(host)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("본인이 공유한 모집에는 신청할 수 없어요."));
    }

    @Test
    void sharedMeetingAppearsInHostMyPageAndHome() throws Exception {
        long meetingId = createMeeting(4);

        mvc.perform(get("/mypage").param("tab", "shared").with(as(host)))
                .andExpect(content().string(allOf(
                        containsString("/course-detail-shared?meetingId=" + meetingId),
                        containsString("주말 산책"),
                        containsString("참여 1 / 정원 4"))));
        mvc.perform(get("/"))
                .andExpect(content().string(allOf(containsString("주말 산책"), containsString("1 / 4명"))));
    }

    private long createMeeting(int maxParticipants) throws Exception {
        String request = """
                {"course": {"name": "AI 추천 코스", "distanceM": 1800, "estimatedMinutes": 25,
                            "startLatitude": 37.544, "startLongitude": 127.043},
                 "points": [{"sequence": 1, "latitude": 37.544, "longitude": 127.043},
                            {"sequence": 2, "latitude": 37.545, "longitude": 127.044}],
                 "title": "주말 산책", "meetingDate": "%s", "meetingTime": "10:00",
                 "maxParticipants": %d, "petRequired": false}
                """.formatted(LocalDate.now().plusDays(3), maxParticipants);

        MvcResult result = mvc.perform(post("/api/meetings").with(as(host)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) json(result, "$.meetingId")).longValue();
    }
}
