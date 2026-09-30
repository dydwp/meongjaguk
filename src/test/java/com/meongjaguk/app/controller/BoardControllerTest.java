package com.meongjaguk.app.controller;

import com.meongjaguk.app.dto.BoardCreateRequest;
import com.meongjaguk.app.dto.BoardPageDto;
import com.meongjaguk.app.dto.CommentDto;
import com.meongjaguk.app.service.BoardService;
import com.meongjaguk.app.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BoardController.class)
class BoardControllerTest extends WebTestSupport {

    @MockitoBean
    BoardService boardService;

    private static final String CREATE_JSON = """
            {"courseId": 3, "title": "주말 산책", "meetingDate": "2099-01-01", "meetingTime": "10:00",
             "maxParticipants": 4, "petRequired": true}
            """;

    // ---------- 목록 / 상세 (비회원 허용) ----------

    @Test
    void guestCanReadBoardList() throws Exception {
        when(boardService.getBoards(null, 6)).thenReturn(new BoardPageDto(List.of(), false, null));

        mvc.perform(get("/api/meetings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void pageSizeIsClampedBetween1And30() throws Exception {
        when(boardService.getBoards(any(), any(Integer.class))).thenReturn(new BoardPageDto(List.of(), false, null));

        mvc.perform(get("/api/meetings").param("size", "100").param("cursor", "12")).andExpect(status().isOk());
        mvc.perform(get("/api/meetings").param("size", "0")).andExpect(status().isOk());

        verify(boardService).getBoards(12L, 30);
        verify(boardService).getBoards(null, 1);
    }

    @Test
    void guestDetailIsRequestedWithoutUserId() throws Exception {
        mvc.perform(get("/api/meetings/10")).andExpect(status().isOk());

        verify(boardService).getBoard(eq(10L), isNull());
    }

    @Test
    void memberDetailIsRequestedWithUserId() throws Exception {
        mvc.perform(get("/api/meetings/10").with(login())).andExpect(status().isOk());

        verify(boardService).getBoard(10L, USER_ID);
    }

    @Test
    void missingBoardIs404WithMessage() throws Exception {
        when(boardService.getBoard(eq(99L), any())).thenThrow(new NoSuchElementException("모집 정보를 찾을 수 없어요."));

        mvc.perform(get("/api/meetings/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("모집 정보를 찾을 수 없어요."));
    }

    // ---------- 등록 ----------

    @Test
    void guestCannotCreate() throws Exception {
        mvc.perform(post("/api/meetings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
        verify(boardService, never()).createBoard(anyLong(), any());
    }

    @Test
    void memberCreatesBoard() throws Exception {
        when(boardService.createBoard(eq(USER_ID), any())).thenReturn(77L);

        mvc.perform(post("/api/meetings").with(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.meetingId").value(77));

        ArgumentCaptor<BoardCreateRequest> captor = ArgumentCaptor.forClass(BoardCreateRequest.class);
        verify(boardService).createBoard(eq(USER_ID), captor.capture());
        assertEquals(3, captor.getValue().courseId());
        assertEquals(LocalDate.of(2099, 1, 1), captor.getValue().meetingDate());
        assertEquals(LocalTime.of(10, 0), captor.getValue().meetingTime());
        assertEquals(Boolean.TRUE, captor.getValue().petRequired());
    }

    @Test
    void validationErrorIs400WithMessage() throws Exception {
        when(boardService.createBoard(eq(USER_ID), any())).thenThrow(new IllegalArgumentException("제목을 입력해주세요."));

        mvc.perform(post("/api/meetings").with(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("제목을 입력해주세요."));
    }

    @Test
    void malformedDateIs400() throws Exception {
        mvc.perform(post("/api/meetings").with(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"meetingDate\": \"내일\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값 형식을 확인해주세요."));
    }

    // ---------- 수정 / 삭제 ----------

    @Test
    void guestCannotUpdateOrDelete() throws Exception {
        mvc.perform(put("/api/meetings/10").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().is3xxRedirection());
        mvc.perform(delete("/api/meetings/10").with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(boardService, never()).updateBoard(anyLong(), anyLong(), any());
        verify(boardService, never()).deleteBoard(anyLong(), anyLong());
    }

    @Test
    void hostUpdatesBoard() throws Exception {
        mvc.perform(put("/api/meetings/10").with(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingId").value(10));

        ArgumentCaptor<BoardCreateRequest> captor = ArgumentCaptor.forClass(BoardCreateRequest.class);
        verify(boardService).updateBoard(eq(10L), eq(USER_ID), captor.capture());
        assertEquals("주말 산책", captor.getValue().title());
    }

    @Test
    void updatingOthersBoardIs403() throws Exception {
        doThrow(new AccessDeniedException("본인이 작성한 글만 수정할 수 있어요."))
                .when(boardService).updateBoard(eq(10L), eq(USER_ID), any());

        mvc.perform(put("/api/meetings/10").with(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인이 작성한 글만 수정할 수 있어요."));
    }

    @Test
    void hostDeletesBoard() throws Exception {
        mvc.perform(delete("/api/meetings/10").with(login()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(boardService).deleteBoard(10L, USER_ID);
    }

    @Test
    void deletingOthersBoardIs403() throws Exception {
        doThrow(new AccessDeniedException("본인이 작성한 글만 삭제할 수 있어요."))
                .when(boardService).deleteBoard(10L, USER_ID);

        mvc.perform(delete("/api/meetings/10").with(login()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인이 작성한 글만 삭제할 수 있어요."));
    }

    // ---------- 댓글 ----------

    @Test
    void guestCanReadComments() throws Exception {
        when(boardService.getComments(10L, null)).thenReturn(List.of(
                new CommentDto(1L, "용제", true, false, "환영해요", LocalDateTime.of(2026, 9, 30, 9, 0))));

        mvc.perform(get("/api/meetings/10/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].authorNickname").value("용제"))
                .andExpect(jsonPath("$[0].hostComment").value(true));
    }

    @Test
    void memberAddsComment() throws Exception {
        when(boardService.addComment(10L, USER_ID, "같이 가요"))
                .thenReturn(new CommentDto(5L, "용제", false, true, "같이 가요", LocalDateTime.now()));

        mvc.perform(post("/api/meetings/10/comments").with(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"같이 가요\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commentId").value(5))
                .andExpect(jsonPath("$.mine").value(true));
    }

    @Test
    void deletingOthersCommentIs403() throws Exception {
        doThrow(new AccessDeniedException("본인이 작성한 댓글만 삭제할 수 있어요."))
                .when(boardService).deleteComment(10L, 5L, USER_ID);

        mvc.perform(delete("/api/meetings/10/comments/5").with(login()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인이 작성한 댓글만 삭제할 수 있어요."));
    }

    @Test
    void deletingOwnCommentIs204() throws Exception {
        mvc.perform(delete("/api/meetings/10/comments/5").with(login()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(boardService).deleteComment(10L, 5L, USER_ID);
    }
}
