package com.mungjaguk.app.repository;

import com.mungjaguk.app.dto.MeetingRequestView;
import com.mungjaguk.app.dto.MyCompanionRequestView;
import com.mungjaguk.app.dto.MySharedMeetingView;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MyPageQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public MyPageQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MySharedMeetingView> findMySharedMeetings(Long userId) {
        String sql = """
                SELECT
                    wm.meeting_id,
                    wm.title,
                    c.name AS course_name,
                    wm.meeting_date,
                    wm.meeting_time,
                    1 + COUNT(CASE WHEN wa.status = 'ACCEPTED' THEN 1 END) AS current_participants,
                    wm.max_participants,
                    wm.status
                FROM walk_meetings wm
                JOIN courses c ON wm.course_id = c.course_id
                LEFT JOIN walk_applications wa ON wm.meeting_id = wa.meeting_id
                WHERE wm.host_user_id = ?
                GROUP BY wm.meeting_id, wm.title, c.name, wm.meeting_date,
                        wm.meeting_time, wm.max_participants, wm.status, wm.created_at
                ORDER BY wm.created_at DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new MySharedMeetingView(
                rs.getLong("meeting_id"),
                rs.getString("title"),
                rs.getString("course_name"),
                rs.getDate("meeting_date").toLocalDate(),
                rs.getTime("meeting_time").toLocalTime(),
                rs.getInt("current_participants"),
                rs.getInt("max_participants"),
                rs.getString("status")
        ), userId);
    }

    public List<MeetingRequestView> findRequestsForMyMeetings(Long userId) {
        String sql = """
                SELECT
                    wa.application_id,
                    wa.meeting_id,
                    wm.title AS meeting_title,
                    u.nickname AS applicant_nickname,
                    wa.message,
                    wa.status
                FROM walk_applications wa
                JOIN walk_meetings wm ON wa.meeting_id = wm.meeting_id
                JOIN users u ON wa.user_id = u.user_id
                WHERE wm.host_user_id = ?
                ORDER BY wa.created_at DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new MeetingRequestView(
                rs.getLong("application_id"),
                rs.getLong("meeting_id"),
                rs.getString("meeting_title"),
                rs.getString("applicant_nickname"),
                rs.getString("message"),
                rs.getString("status")
        ), userId);
    }

    public List<MyCompanionRequestView> findMyCompanionRequests(Long userId) {
        String sql = """
                SELECT
                    wa.application_id,
                    wa.meeting_id,
                    wm.title AS meeting_title,
                    c.name AS course_name,
                    host.nickname AS host_nickname,
                    wm.meeting_date,
                    wm.meeting_time,
                    wa.status
                FROM walk_applications wa
                JOIN walk_meetings wm ON wa.meeting_id = wm.meeting_id
                JOIN courses c ON wm.course_id = c.course_id
                JOIN users host ON wm.host_user_id = host.user_id
                WHERE wa.user_id = ?
                ORDER BY wa.created_at DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new MyCompanionRequestView(
                rs.getLong("application_id"),
                rs.getLong("meeting_id"),
                rs.getString("meeting_title"),
                rs.getString("course_name"),
                rs.getString("host_nickname"),
                rs.getDate("meeting_date").toLocalDate(),
                rs.getTime("meeting_time").toLocalTime(),
                rs.getString("status")
        ), userId);
    }
}