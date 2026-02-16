package pl.piomin.services.person.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.piomin.services.person.dto.PersonRequest;
import pl.piomin.services.person.dto.PersonResponse;
import pl.piomin.services.person.entity.Person;
import pl.piomin.services.person.repository.PersonRepository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PersonServiceTest {

    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private PersonService personService;

    private PersonRequest validRequest;
    private Person person;

    @BeforeEach
    void setUp() {
        validRequest = new PersonRequest();
        validRequest.setFirstName("John");
        validRequest.setLastName("Doe");
        validRequest.setPesel("1234567890");
        validRequest.setBirthDate(LocalDate.of(1990, 1, 15));
        validRequest.setEmail("john.doe@example.com");
        validRequest.setPhone("123456789");
        validRequest.setAddress("123 Main St");

        person = new Person();
        person.setId(1L);
        person.setFirstName("John");
        person.setLastName("Doe");
        person.setPesel("1234567890");
        person.setBirthDate(LocalDate.of(1990, 1, 15));
        person.setEmail("john.doe@example.com");
        person.setPhone("123456789");
        person.setAddress("123 Main St");
    }

    @Test
    void shouldSavePerson() {
        when(personRepository.save(any(Person.class))).thenReturn(person);

        PersonResponse result = personService.save(validRequest);

        assertNotNull(result);
        assertEquals("John", result.getFirstName());
        assertEquals("Doe", result.getLastName());
        verify(personRepository, times(1)).save(any(Person.class));
    }

    @Test
    void shouldFindAllPersons() {
        List<Person> persons = Arrays.asList(person);
        when(personRepository.findAll()).thenReturn(persons);

        List<PersonResponse> results = personService.findAll();

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("John", results.get(0).getFirstName());
    }

    @Test
    void shouldFindPersonById() {
        when(personRepository.findById(1L)).thenReturn(Optional.of(person));

        Optional<PersonResponse> result = personService.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("John", result.get().getFirstName());
    }

    @Test
    void shouldNotFindPersonByIdWhenNotExists() {
        when(personRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<PersonResponse> result = personService.findById(99L);

        assertFalse(result.isPresent());
    }

    @Test
    void shouldFindPersonByPesel() {
        when(personRepository.findByPesel("1234567890")).thenReturn(Optional.of(person));

        Optional<PersonResponse> result = personService.findByPesel("1234567890");

        assertTrue(result.isPresent());
        assertEquals("1234567890", result.get().getPesel());
    }

    @Test
    void shouldUpdatePerson() {
        PersonRequest updateRequest = new PersonRequest();
        updateRequest.setFirstName("Jane");
        updateRequest.setLastName("Smith");
        updateRequest.setPesel("0987654321");

        when(personRepository.findById(1L)).thenReturn(Optional.of(person));
        when(personRepository.save(any(Person.class))).thenReturn(person);

        Optional<PersonResponse> result = personService.update(1L, updateRequest);

        assertTrue(result.isPresent());
        verify(personRepository, times(1)).save(any(Person.class));
    }

    @Test
    void shouldNotUpdatePersonWhenNotExists() {
        when(personRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<PersonResponse> result = personService.update(99L, validRequest);

        assertFalse(result.isPresent());
        verify(personRepository, never()).save(any(Person.class));
    }

    @Test
    void shouldDeletePerson() {
        when(personRepository.existsById(1L)).thenReturn(true);

        boolean result = personService.delete(1L);

        assertTrue(result);
        verify(personRepository, times(1)).deleteById(1L);
    }

    @Test
    void shouldNotDeletePersonWhenNotExists() {
        when(personRepository.existsById(99L)).thenReturn(false);

        boolean result = personService.delete(99L);

        assertFalse(result);
        verify(personRepository, never()).deleteById(any());
    }
}
