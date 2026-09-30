package com.meongjaguk.app.scenario;

import com.meongjaguk.app.entity.Board;
import com.meongjaguk.app.entity.User;
import com.meongjaguk.app.support.ScenarioTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 댓글: 작성 → 작성자 알림 → 비회원 조회 → 본인만 삭제 */
class CommentFlowScenarioTest extends ScenarioTestSupport {

    @Test
    void commentNotifiesHostAndOnlyAuthorCanDelete() throws Exception {
        User host = data.user("용제");
        User minjun = data.user("민준");
        Board board = data.board(host, "저녁 산책", LocalDateTime.now().plusDays(1), 4);
        String comments = "/api/meetings/" + board.getMeetingId() + "/comments";

        // 작성자 본인 댓글 (알림 없음) + 민준 댓글 (알림)
        comment(host, comments, "환영해요!");
        MvcResult minjunComment = comment(minjun, comments, "  같이 걸어요  ");
        long commentId = ((Number) json(minjunComment, "$.commentId")).longValue();

        mvc.perform(get("/api/notifications").with(as(host)))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].message").value("민준님: 같이 걸어요"))
                .andExpect(jsonPath("$.items[0].link").value("/course-detail-shared?meetingId=" + board.getMeetingId()));

        // 비회원도 댓글 목록 조회 (최신순), 작성자 댓글 표시
        mvc.perform(get(comments))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("같이 걸어요"))
                .andExpect(jsonPath("$[0].mine").value(false))
                .andExpect(jsonPath("$[1].hostComment").value(true));

        // 다른 사람 댓글 삭제 403, 본인은 204
        mvc.perform(delete(comments + "/" + commentId).with(as(host)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(comments + "/" + commentId).with(as(minjun)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(comments)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void invalidCommentsAreRejected() throws Exception {
        User host = data.user("용제");
        Board board = data.board(host, "저녁 산책", LocalDateTime.now().plusDays(1), 4);
        String comments = "/api/meetings/" + board.getMeetingId() + "/comments";

        mvc.perform(post(comments).with(as(host)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("댓글 내용을 입력해주세요."));
        mvc.perform(post("/api/meetings/999999/comments").with(as(host)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"안녕\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(comments).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"비회원\"}"))
                .andExpect(status().is3xxRedirection());
    }

    private MvcResult comment(User user, String url, String content) throws Exception {
        return mvc.perform(post(url).with(as(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"" + content + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
    }
}
