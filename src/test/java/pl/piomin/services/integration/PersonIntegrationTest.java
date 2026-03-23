package pl.piomin.services.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.piomin.services.application.dto.PersonRequest;
import pl.piomin.services.domain.repository.PersonRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class PersonIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersonRepository personRepository;

    @BeforeEach
    void setUp() {
        personRepository.deleteAll();
    }

    @Test
    @WithMockUser
    void fullCrudFlow_Success() throws Exception {
        // Create
        PersonRequest createRequest = new PersonRequest();
        createRequest.setFirstName("John");
        createRequest.setLastName("Doe");
        createRequest.setEmail("john.doe@example.com");
        createRequest.setPhoneNumber("+1234567890");
        createRequest.setCity("New York");
        createRequest.setDateOfBirth(LocalDate.of(1990, 1, 15));

        String createResponse = mockMvc.perform(post("/api/persons")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long personId = objectMapper.readTree(createResponse).get("id").asLong();

        // Verify in database
        assertThat(personRepository.findById(personId)).isPresent();

        // Read
        mockMvc.perform(get("/api/persons/" + personId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"));

        // Update
        PersonRequest updateRequest = new PersonRequest();
        updateRequest.setFirstName("Jane");
        updateRequest.setLastName("Doe");
        updateRequest.setEmail("john.doe@example.com");
        updateRequest.setCity("Boston");

        mockMvc.perform(put("/api/persons/" + personId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.city").value("Boston"));

        // Verify update in database
        assertThat(personRepository.findById(personId))
                .isPresent()
                .get()
                .satisfies(person -> {
                    assertThat(person.getFirstName()).isEqualTo("Jane");
                    assertThat(person.getCity()).isEqualTo("Boston");
                });

        // Delete
        mockMvc.perform(delete("/api/persons/" + personId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Verify deletion in database
        assertThat(personRepository.findById(personId)).isEmpty();
    }

    @Test
    @WithMockUser
    void createPerson_DuplicateEmail_ReturnsError() throws Exception {
        // Create first person
        PersonRequest request1 = new PersonRequest();
        request1.setFirstName("John");
        request1.setLastName("Doe");
        request1.setEmail("duplicate@example.com");

        mockMvc.perform(post("/api/persons")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Attempt to create second person with same email
        PersonRequest request2 = new PersonRequest();
        request2.setFirstName("Jane");
        request2.setLastName("Smith");
        request2.setEmail("duplicate@example.com");

        mockMvc.perform(post("/api/persons")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getAllPersons_WithPagination_Success() throws Exception {
        // Create multiple persons
        for (int i = 1; i <= 5; i++) {
            PersonRequest request = new PersonRequest();
            request.setFirstName("Person" + i);
            request.setLastName("Test" + i);
            request.setEmail("person" + i + "@example.com");

            mockMvc.perform(post("/api/persons")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        // Get paginated results
        mockMvc.perform(get("/api/persons")
                        .param("page", "0")
                        .param("size", "3")
                        .param("sort", "lastName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @WithMockUser
    void searchByEmail_Found_Success() throws Exception {
        // Create person
        PersonRequest request = new PersonRequest();
        request.setFirstName("Search");
        request.setLastName("Test");
        request.setEmail("search@example.com");

        mockMvc.perform(post("/api/persons")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Search by email
        mockMvc.perform(get("/api/persons/search")
                        .param("email", "search@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("search@example.com"))
                .andExpect(jsonPath("$.firstName").value("Search"));
    }

    @Test
    @WithMockUser
    void searchByEmail_NotFound_ReturnsError() throws Exception {
        mockMvc.perform(get("/api/persons/search")
                        .param("email", "nonexistent@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void updatePerson_NotFound_ReturnsError() throws Exception {
        PersonRequest request = new PersonRequest();
        request.setFirstName("Update");
        request.setLastName("Test");
        request.setEmail("update@example.com");

        mockMvc.perform(put("/api/persons/999")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void deletePerson_NotFound_ReturnsError() throws Exception {
        mockMvc.perform(delete("/api/persons/999")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void createPerson_ValidationError_ReturnsError() throws Exception {
        PersonRequest request = new PersonRequest();
        request.setFirstName("");
        request.setEmail("invalid-email");

        mockMvc.perform(post("/api/persons")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"));
    }
}
