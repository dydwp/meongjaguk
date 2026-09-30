package com.meongjaguk.app.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WalkMeetingPetRepository {

    private final JdbcTemplate jdbcTemplate;

    public WalkMeetingPetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 동행 모집 글에 함께할 반려견 연결 저장 (담당: 최주영)
    public void savePetLinks(Long meetingId, List<Long> petIds) {
        if (petIds == null || petIds.isEmpty()) return;

        String sql = """
                INSERT INTO walk_meeting_pets (meeting_id, pet_id)
                VALUES (?, ?)
                """;

        for (Long petId : petIds) {
            jdbcTemplate.update(sql, meetingId, petId);
        }
    }

    // 게시글 수정(반려견 교체)·삭제 시 연결 삭제
    public void deletePetLinks(Long meetingId) {
        jdbcTemplate.update("DELETE FROM walk_meeting_pets WHERE meeting_id = ?", meetingId);
    }

    // 동행 모집 상세에서 연결된 반려견 조회 (담당: 최주영)
    public List<Long> findPetIdsByMeetingId(Long meetingId, Long userId) {
        String sql = """
                SELECT wmp.pet_id
                FROM walk_meeting_pets wmp
                JOIN walk_meetings wm ON wmp.meeting_id = wm.meeting_id
                JOIN pets p ON wmp.pet_id = p.pet_id
                WHERE wmp.meeting_id = ?
                  AND wm.host_user_id = ?
                  AND p.user_id = ?
                ORDER BY wmp.pet_id
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("pet_id"),
                meetingId, userId, userId
        );
    }
}