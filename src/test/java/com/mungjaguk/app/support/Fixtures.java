package com.mungjaguk.app.support;

import com.mungjaguk.app.entity.ApplicationStatus;
import com.mungjaguk.app.entity.Board;
import com.mungjaguk.app.entity.BoardComment;
import com.mungjaguk.app.entity.BoardStatus;
import com.mungjaguk.app.entity.CompanionRequest;
import com.mungjaguk.app.entity.Pet;
import com.mungjaguk.app.entity.Role;
import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.security.LoginUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 단위 테스트용 엔티티 (DB 없이 id·생성일을 직접 채움)
 */
public final class Fixtures {

    private Fixtures() {}

    public static User user(long userId, String nickname) {
        User user = User.create("kakao", "kakao-" + userId, nickname, null, null);
        ReflectionTestUtils.setField(user, "userId", userId);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 9, 1, 10, 0));
        return user;
    }

    public static Route route(int courseId, String name) {
        Route route = new Route();
        route.setCourseId(courseId);
        route.setCourseName(name);
        route.setDescription(name + " 설명");
        route.setDistanceM(2600L);
        route.setEstimatedMinutes(40);
        route.setStartLatitude(37.544);
        route.setStartLongitude(127.043);
        return route;
    }

    /** 모집 중(RECRUITING)인 모집 글 */
    public static Board board(long meetingId, User host, Route course, LocalDateTime meetingAt, int maxParticipants) {
        Board board = Board.create(host, course, "모집 " + meetingId, "설명",
                meetingAt.toLocalDate(), meetingAt.toLocalTime(), maxParticipants, false, null);
        ReflectionTestUtils.setField(board, "meetingId", meetingId);
        ReflectionTestUtils.setField(board, "status", BoardStatus.RECRUITING);
        ReflectionTestUtils.setField(board, "createdAt", LocalDateTime.now().minusDays(1));
        return board;
    }

    public static Board board(long meetingId, User host, LocalDateTime meetingAt, int maxParticipants) {
        return board(meetingId, host, route(1, "서울숲 코스"), meetingAt, maxParticipants);
    }

    public static CompanionRequest request(long applicationId, Board board, User applicant, ApplicationStatus status) {
        CompanionRequest request = CompanionRequest.create(board, applicant);
        ReflectionTestUtils.setField(request, "id", applicationId);
        ReflectionTestUtils.setField(request, "status", status);
        return request;
    }

    public static BoardComment comment(long commentId, Board board, User author, String content) {
        BoardComment comment = BoardComment.create(board, author, content);
        ReflectionTestUtils.setField(comment, "commentId", commentId);
        ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.now());
        return comment;
    }

    public static Pet pet(long petId, User owner, String name) {
        Pet pet = new Pet();
        pet.setPetId(petId);
        pet.setUser(owner);
        pet.setName(name);
        return pet;
    }

    /** 로그인 사용자 (MockMvc oauth2Login().oauth2User(...) 에 넣어 사용) */
    public static LoginUser loginUser(long userId, String nickname) {
        return new LoginUser(userId, nickname, Role.USER, Map.of("id", userId));
    }
}
