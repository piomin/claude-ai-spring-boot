package pl.piomin.services.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pl.piomin.services.application.dto.PersonRequest;
import pl.piomin.services.application.dto.PersonResponse;
import pl.piomin.services.application.service.PersonService;
import pl.piomin.services.infrastructure.exception.PersonNotFoundException;
import pl.piomin.services.presentation.rest.PersonController;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PersonController.class)
class PersonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PersonService personService;

    @Test
    @WithMockUser
    void createPerson_Success() throws Exception {
        PersonRequest request = new PersonRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("john.doe@example.com");
        request.setPhoneNumber("+1234567890");

        PersonResponse response = new PersonResponse();
        response.setId(1L);
        response.setFirstName("John");
        response.setLastName("Doe");
        response.setEmail("john.doe@example.com");

        when(personService.createPerson(any(PersonRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/persons")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));

        verify(personService).createPerson(any(PersonRequest.class));
    }

    @Test
    @WithMockUser
    void createPerson_ValidationError() throws Exception {
        PersonRequest request = new PersonRequest();
        request.setFirstName("");
        request.setEmail("invalid-email");

        mockMvc.perform(post("/api/persons")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(personService, never()).createPerson(any());
    }

    @Test
    @WithMockUser
    void getPersonById_Success() throws Exception {
        PersonResponse response = new PersonResponse();
        response.setId(1L);
        response.setFirstName("John");
        response.setLastName("Doe");
        response.setEmail("john.doe@example.com");

        when(personService.getPersonById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/persons/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("John"));

        verify(personService).getPersonById(1L);
    }

    @Test
    @WithMockUser
    void getPersonById_NotFound() throws Exception {
        when(personService.getPersonById(999L)).thenThrow(new PersonNotFoundException(999L));

        mockMvc.perform(get("/api/persons/999"))
                .andExpect(status().isNotFound());

        verify(personService).getPersonById(999L);
    }

    @Test
    @WithMockUser
    void getAllPersons_Success() throws Exception {
        PersonResponse response1 = new PersonResponse();
        response1.setId(1L);
        response1.setFirstName("John");
        response1.setLastName("Doe");

        PersonResponse response2 = new PersonResponse();
        response2.setId(2L);
        response2.setFirstName("Jane");
        response2.setLastName("Smith");

        PageImpl<PersonResponse> page = new PageImpl<>(List.of(response1, response2), PageRequest.of(0, 20), 2);

        when(personService.getAllPersons(any())).thenReturn(page);

        mockMvc.perform(get("/api/persons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));

        verify(personService).getAllPersons(any());
    }

    @Test
    @WithMockUser
    void updatePerson_Success() throws Exception {
        PersonRequest request = new PersonRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("jane.doe@example.com");

        PersonResponse response = new PersonResponse();
        response.setId(1L);
        response.setFirstName("Jane");
        response.setLastName("Doe");
        response.setEmail("jane.doe@example.com");

        when(personService.updatePerson(eq(1L), any(PersonRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/persons/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"));

        verify(personService).updatePerson(eq(1L), any(PersonRequest.class));
    }

    @Test
    @WithMockUser
    void deletePerson_Success() throws Exception {
        doNothing().when(personService).deletePerson(1L);

        mockMvc.perform(delete("/api/persons/1")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(personService).deletePerson(1L);
    }

    @Test
    @WithMockUser
    void findByEmail_Success() throws Exception {
        PersonResponse response = new PersonResponse();
        response.setId(1L);
        response.setEmail("john.doe@example.com");

        when(personService.findByEmail("john.doe@example.com")).thenReturn(response);

        mockMvc.perform(get("/api/persons/search")
                        .param("email", "john.doe@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));

        verify(personService).findByEmail("john.doe@example.com");
    }

    @Test
    void createPerson_Unauthorized() throws Exception {
        PersonRequest request = new PersonRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("john.doe@example.com");

        mockMvc.perform(post("/api/persons")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(personService, never()).createPerson(any());
    }
}
