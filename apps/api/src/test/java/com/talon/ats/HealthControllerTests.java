package com.talon.ats;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.talon.ats.platform.health.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTests {

  @Test
  void reportsTheApiAsAvailable() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();

    mockMvc
        .perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.service").value("talon-api"));
  }
}
