package com.edam.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edam.common.logging.CorrelationIdProvider;
import com.edam.common.security.CurrentUserContext;
import com.edam.institution.api.InstitutionBrandingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalRestExceptionHandlerTest.ProbeRestController.class)
@Import(GlobalRestExceptionHandlerTest.ProbeRestController.class)
class GlobalRestExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstitutionBrandingService brandingService;

    @MockitoBean
    private CurrentUserContext currentUserContext;

    @Test
    @WithMockUser
    void handlesBusinessExceptionWithStandardEnvelopeAndCorrelationId() throws Exception {
        mockMvc.perform(get("/api/test/probe/business")
                        .header(CorrelationIdProvider.CORRELATION_ID_HEADER, "test-corr-bus"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(header().string(CorrelationIdProvider.CORRELATION_ID_HEADER, "test-corr-bus"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.correlationId").value("test-corr-bus"))
                .andExpect(jsonPath("$.error.code").value("EDAM-BUS-422"))
                .andExpect(jsonPath("$.error.message").value("Approval limit rule violated."));
    }

    @Test
    @WithMockUser
    void handlesNotFoundExceptionWith404() throws Exception {
        mockMvc.perform(get("/api/test/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EDAM-NOT-FOUND-404"));
    }

    @Test
    @WithMockUser
    void handlesValidationFailureWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/test/probe/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EDAM-VAL-400"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("code"));
    }

    @Test
    @WithMockUser
    void masksSqlAndDatabaseDetailsOnDataIntegrityViolation() throws Exception {
        mockMvc.perform(get("/api/test/probe/db-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EDAM-CONFLICT-409"))
                .andExpect(content().string(not(containsString("SELECT"))))
                .andExpect(content().string(not(containsString("uk_secret_constraint"))));
    }

    @Test
    @WithMockUser
    void masksInternalExceptionDetailsOnUnexpectedFailure() throws Exception {
        mockMvc.perform(get("/api/test/probe/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("EDAM-SYS-500"))
                .andExpect(content().string(not(containsString("internal secret host"))));
    }

    @RestController
    @RequestMapping("/api/test/probe")
    static class ProbeRestController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException("Approval limit rule violated.");
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Requested item does not exist.");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody ProbePayload payload) {
        }

        @GetMapping("/db-conflict")
        void dbConflict() {
            throw new DataIntegrityViolationException("ERROR: duplicate key value violates uk_secret_constraint SELECT * FROM users");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("Crash connecting to internal secret host 10.0.1.99");
        }
    }

    record ProbePayload(@NotBlank(message = "Code is required") String code) {
    }
}
