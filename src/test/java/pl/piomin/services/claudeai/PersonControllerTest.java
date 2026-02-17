package pl.piomin.services.claudeai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.piomin.services.claudeai.domain.Person;
import pl.piomin.services.claudeai.repository.PersonRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class PersonControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PersonRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testCreateAndGetPerson() throws Exception {
        Person person = new Person("John", "Doe", "john.doe@example.com", 30);
        String json = objectMapper.writeValueAsString(person);

        // Create
        mockMvc.perform(post("/api/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(".id").exists());

        // Retrieve all
        mockMvc.perform(get("/api/people"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("[0].firstName").value("John"));
    }

    @Test
    public void testUpdatePerson() throws Exception {
        Person person = repository.save(new Person("Jane", "Doe", "jane.doe@example.com", 25));
        Long id = person.getId();
        Person updated = new Person("Jane", "Smith", "jane.smith@example.com", 26);
        String json = objectMapper.writeValueAsString(updated);

        mockMvc.perform(put("/api/people/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath(".lastName").value("Smith"));
    }

    @Test
    public void testDeletePerson() throws Exception {
        Person person = repository.save(new Person("Mark", "Lee", "mark.lee@example.com", 40));
        Long id = person.getId();

        mockMvc.perform(delete("/api/people/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/people/" + id))
                .andExpect(status().isNotFound());
    }
}
