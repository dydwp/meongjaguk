package com.mungjaguk.app.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WalkRecordPetRepository {

    private final JdbcTemplate jdbcTemplate;

    public WalkRecordPetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<Long, List<String>> findPetNamesByUserId(Long userId) {
        String sql = """
                SELECT wrp.walk_record_id, p.name
                FROM walk_record_pets wrp
                JOIN walk_records wr ON wrp.walk_record_id = wr.walk_record_id
                JOIN pets p ON wrp.pet_id = p.pet_id
                WHERE wr.user_id = ?
                  AND p.user_id = ?
                ORDER BY wrp.walk_record_id, p.pet_id
                """;

        return jdbcTemplate.query(sql, rs -> {
            Map<Long, List<String>> result = new LinkedHashMap<>();

            while (rs.next()) {
                Long walkRecordId = rs.getLong("walk_record_id");
                result.computeIfAbsent(walkRecordId, key -> new ArrayList<>())
                        .add(rs.getString("name"));
            }

            return result;
        }, userId, userId);
    }

    public Set<Long> findWalkRecordIdsByPetId(Long userId, Long petId) {
        String sql = """
                SELECT DISTINCT wrp.walk_record_id
                FROM walk_record_pets wrp
                JOIN walk_records wr ON wrp.walk_record_id = wr.walk_record_id
                JOIN pets p ON wrp.pet_id = p.pet_id
                WHERE wr.user_id = ?
                  AND p.user_id = ?
                  AND p.pet_id = ?
                """;

        return new LinkedHashSet<>(jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("walk_record_id"),
                userId, userId, petId
        ));
    }

    public List<String> findPetNamesByWalkRecordId(Long walkRecordId, Long userId) {
        String sql = """
                SELECT p.name
                FROM walk_record_pets wrp
                JOIN walk_records wr ON wrp.walk_record_id = wr.walk_record_id
                JOIN pets p ON wrp.pet_id = p.pet_id
                WHERE wrp.walk_record_id = ?
                AND wr.user_id = ?
                AND p.user_id = ?
                ORDER BY p.pet_id
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getString("name"),
                walkRecordId, userId, userId
        );
    }
}