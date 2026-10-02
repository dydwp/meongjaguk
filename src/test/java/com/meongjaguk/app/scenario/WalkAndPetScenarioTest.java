package com.meongjaguk.app.scenario;

import com.meongjaguk.app.entity.User;
import com.meongjaguk.app.support.ScenarioTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 반려견 등록 → 산책 기록 저장 → 메인/마이페이지/활동 상세 반영 → 삭제 */
class WalkAndPetScenarioTest extends ScenarioTestSupport {

    @Test
    void registerPetThenWalkThenDeleteRecord() throws Exception {
        User me = data.user("용제");

        // 1) 반려견 등록 (사진 없이) → 메인 인사말에 반려견 이름
        MockMultipartFile petInfo = new MockMultipartFile("petInfo", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"name\": \"초코\", \"breed\": \"푸들\", \"age\": 2, \"size\": \"SMALL\", \"activityLevel\": \"HIGH\"}"
                        .getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/pet-profile/pets").file(petInfo).with(as(me)).with(csrf()))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/pet-profile/pets").with(as(me)))
                .andExpect(jsonPath("$[0].name").value("초코"))
                .andExpect(jsonPath("$[0].sizeLabel").value("소형견"));
        mvc.perform(get("/").with(as(me)))
                .andExpect(content().string(allOf(
                        containsString("초코랑 같이 걸어요"),
                        containsString("아직 산책 기록이 없어요"))));

        // 2) 오늘 추천 코스로 산책 → 저장
        long start = LocalDateTime.now().withHour(0).withMinute(1).atZone(ZoneId.of("Asia/Seoul"))
                .toInstant().toEpochMilli();
        String walk = """
                {"startedAt": %d, "endedAt": %d, "distanceM": 2400,
                 "points": [{"lat": 37.544, "lng": 127.043, "t": %d}, {"lat": 37.545, "lng": 127.044, "t": %d}],
                 "recommendedRoute": {"title": "서울숲 코스", "description": "공원 한 바퀴", "distanceM": 2300,
                   "estimatedMinutes": 35,
                   "points": [{"sequence": 1, "latitude": 37.544, "longitude": 127.043},
                              {"sequence": 2, "latitude": 37.545, "longitude": 127.044}]}}
                """.formatted(start, start + 2_292_000L, start, start + 60_000L);
        MvcResult saved = mvc.perform(post("/api/walks").with(as(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(walk))
                .andExpect(status().isOk())
                .andReturn();
        long walkId = ((Number) json(saved, "$.walkRecordId")).longValue();

        // 3) 메인 이번 주 요약 / 마이페이지 활동 내역 / 활동 상세
        mvc.perform(get("/").with(as(me)))
                .andExpect(content().string(allOf(
                        containsString("<b>1</b>회"),
                        containsString("<b>38</b>분"),
                        containsString("마지막 산책 · 오늘 2.4km"))));
        mvc.perform(get("/mypage").param("tab", "activity").with(as(me)))
                .andExpect(content().string(allOf(
                        containsString("/activity-detail?id=" + walkId),
                        containsString("서울숲 코스"),
                        containsString("약 38분"))));
        mvc.perform(get("/activity-detail").param("id", String.valueOf(walkId)).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("거리 · 2.3km"),
                        containsString("38:12"),
                        containsString("data-lat=\"37.5450000\""))));

        // 4) 다른 회원은 볼 수도 지울 수도 없음
        User stranger = data.user("남");
        mvc.perform(get("/activity-detail").param("id", String.valueOf(walkId)).with(as(stranger)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/activity-detail/" + walkId + "/delete").with(as(stranger)).with(csrf()))
                .andExpect(status().isNotFound());

        // 5) 본인 삭제 → 목록에서 사라짐
        mvc.perform(post("/activity-detail/" + walkId + "/delete").with(as(me)).with(csrf()))
                .andExpect(redirectedUrl("/mypage?tab=activity"))
                .andExpect(flash().attribute("activityMessage", "산책 기록을 삭제했습니다."));
        mvc.perform(get("/mypage").param("tab", "activity").with(as(me)))
                .andExpect(content().string(allOf(
                        containsString("아직 산책 기록이 없습니다."),
                        not(containsString("/activity-detail?id=" + walkId)))));
        mvc.perform(get("/activity-detail").param("id", String.valueOf(walkId)).with(as(me)))
                .andExpect(status().isNotFound());
    }

    @Test
    void petCanBeEditedAndDeletedOnlyByOwner() throws Exception {
        User me = data.user("용제");
        User stranger = data.user("남");
        long petId = data.pet(me, "보리").getPetId();

        mvc.perform(get("/api/pet-profile/pets/" + petId).with(as(stranger))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/pet-profile/pets/" + petId).with(as(stranger)).with(csrf()))
                .andExpect(status().isNotFound());

        MockMultipartFile update = new MockMultipartFile("petInfo", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"name\": \"보리\", \"breed\": \"말티즈\", \"age\": 5, \"size\": \"MEDIUM\", \"activityLevel\": \"LOW\"}"
                        .getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/pet-profile/pets/" + petId)
                        .file(update).with(as(me)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/pet-profile/pets/" + petId).with(as(me)))
                .andExpect(jsonPath("$.age").value(5))
                .andExpect(jsonPath("$.size").value("MEDIUM"));

        mvc.perform(delete("/api/pet-profile/pets/" + petId).with(as(me)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/pet-profile/pets").with(as(me))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void brokenWalkIsRejectedWithReason() throws Exception {
        User me = data.user("용제");

        mvc.perform(post("/api/walks").with(as(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\": 1700000600000, \"endedAt\": 1700000000000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("산책 시간이 올바르지 않습니다."));
        mvc.perform(get("/mypage").param("tab", "activity").with(as(me)))
                .andExpect(content().string(containsString("아직 산책 기록이 없습니다.")));
    }
}
