package com.meongjaguk.app.scenario;

import com.meongjaguk.app.support.ScenarioTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Docker·AWS 가 사용하는 상태 확인 주소 (/actuator/health) */
class HealthCheckScenarioTest extends ScenarioTestSupport {

    @Test
    void guestCanCheckHealthAndOnlyStatusIsShown() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // DB 종류 등 내부 정보는 노출하지 않음
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsAreNotOpenToGuests() throws Exception {
        mvc.perform(get("/actuator/env"))
                .andExpect(status().is3xxRedirection());
    }
}
