package pl.piomin.services.controller;

import pl.piomin.services.model.Person;
import pl.piomin.services.service.PersonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PersonController.class)
class PersonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonService personService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllPersons_ReturnsListOfPersons() throws Exception {
        Person person1 = new Person("John", "Doe", "john.doe@example.com", LocalDate.of(1990, 1, 1));
        person1.setId(1L);

        Person person2 = new Person("Jane", "Smith", "jane.smith@example.com", LocalDate.of(1992, 5, 15));
        person2.setId(2L);

        when(personService.getAllPersons()).thenReturn(Arrays.asList(person1, person2));

        mockMvc.perform(get("/api/persons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].firstName").value("John"))
                .andExpect(jsonPath("$[1].firstName").value("Jane"));
    }

    @Test
    void getPersonById_ExistingId_ReturnsPerson() throws Exception {
        Person person = new Person("John", "Doe", "john.doe@example.com", LocalDate.of(1990, 1, 1));
        person.setId(1L);

        when(personService.getPersonById(1L)).thenReturn(Optional.of(person));

        mockMvc.perform(get("/api/persons/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    void getPersonById_NonExistingId_ReturnsNotFound() throws Exception {
        when(personService.getPersonById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/persons/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPerson_ValidPerson_ReturnsCreatedPerson() throws Exception {
        Person person = new Person("Alice", "Johnson", "alice.johnson@example.com", LocalDate.of(1985, 3, 10));
        person.setId(1L);

        when(personService.createPerson(any(Person.class))).thenReturn(person);

        mockMvc.perform(post("/api/persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(person)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Alice"));
    }

    @Test
    void updatePerson_ExistingId_ReturnsUpdatedPerson() throws Exception {
        Person person = new Person("John", "Doe", "john.doe@example.com", LocalDate.of(1990, 1, 1));
        person.setId(1L);

        when(personService.updatePerson(eq(1L), any(Person.class))).thenReturn(person);

        mockMvc.perform(put("/api/persons/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(person)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("John"));
    }

    @Test
    void updatePerson_NonExistingId_ReturnsNotFound() throws Exception {
        Person person = new Person("John", "Doe", "john.doe@example.com", LocalDate.of(1990, 1, 1));

        when(personService.updatePerson(eq(99L), any(Person.class)))
                .thenThrow(new RuntimeException("Person not found with id: 99"));

        mockMvc.perform(put("/api/persons/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(person)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePerson_ExistingId_ReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/persons/1"))
                .andExpect(status().isNoContent());
    }
}