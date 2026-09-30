package com.meongjaguk.app.scenario;

import com.meongjaguk.app.entity.Board;
import com.meongjaguk.app.entity.User;
import com.meongjaguk.app.support.ScenarioTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 비회원: 둘러보기는 되고, 참여·기록은 로그인으로 안내 */
class GuestScenarioTest extends ScenarioTestSupport {

    @Test
    void guestCanBrowseButMustLoginToJoin() throws Exception {
        User host = data.user("용제");
        Board board = data.board(host, "저녁 산책", LocalDateTime.now().plusDays(1), 4);
        data.route("한강 코스");

        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("저녁 산책")));
        mvc.perform(get("/board")).andExpect(status().isOk());
        mvc.perform(get("/routes")).andExpect(status().isOk());
        mvc.perform(get("/api/courses/routes")).andExpect(jsonPath("$.length()").value(2)); // 모집 코스 + 한강 코스
        mvc.perform(get("/api/meetings"))
                .andExpect(jsonPath("$.items[0].title").value("저녁 산책"))
                .andExpect(jsonPath("$.items[0].hostNickname").value("용제"));
        mvc.perform(get("/api/meetings/" + board.getMeetingId()))
                .andExpect(jsonPath("$.isHost").value(false))
                .andExpect(jsonPath("$.myApplicationStatus").doesNotExist());

        for (String page : new String[]{"/mypage", "/walk-record", "/board/new", "/pet-profile", "/api/notifications"}) {
            mvc.perform(get(page))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", containsString("/login")));
        }
        mvc.perform(post("/api/meetings/" + board.getMeetingId() + "/applications").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/walks").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void unknownPagesShow404Page() throws Exception {
        User me = data.user("용제");

        mvc.perform(get("/activity-detail").param("id", "999999").with(as(me)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/meetings/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("모집 정보를 찾을 수 없어요."));
    }
}
