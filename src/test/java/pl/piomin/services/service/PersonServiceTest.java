package pl.piomin.services.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import pl.piomin.services.application.dto.PersonRequest;
import pl.piomin.services.application.dto.PersonResponse;
import pl.piomin.services.application.mapper.PersonMapper;
import pl.piomin.services.application.service.PersonService;
import pl.piomin.services.domain.entity.Person;
import pl.piomin.services.domain.repository.PersonRepository;
import pl.piomin.services.infrastructure.exception.PersonNotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonServiceTest {

    @Mock
    private PersonRepository personRepository;

    @Mock
    private PersonMapper personMapper;

    @InjectMocks
    private PersonService personService;

    private Person person;
    private PersonRequest personRequest;
    private PersonResponse personResponse;

    @BeforeEach
    void setUp() {
        person = new Person("John", "Doe", "john.doe@example.com");
        person.setId(1L);
        person.setPhoneNumber("+1234567890");
        person.setCity("New York");

        personRequest = new PersonRequest();
        personRequest.setFirstName("John");
        personRequest.setLastName("Doe");
        personRequest.setEmail("john.doe@example.com");
        personRequest.setPhoneNumber("+1234567890");
        personRequest.setCity("New York");

        personResponse = new PersonResponse();
        personResponse.setId(1L);
        personResponse.setFirstName("John");
        personResponse.setLastName("Doe");
        personResponse.setEmail("john.doe@example.com");
    }

    @Test
    void createPerson_Success() {
        when(personRepository.existsByEmail(personRequest.getEmail())).thenReturn(false);
        when(personMapper.toEntity(personRequest)).thenReturn(person);
        when(personRepository.save(person)).thenReturn(person);
        when(personMapper.toResponse(person)).thenReturn(personResponse);

        PersonResponse result = personService.createPerson(personRequest);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
        verify(personRepository).existsByEmail(personRequest.getEmail());
        verify(personRepository).save(person);
    }

    @Test
    void createPerson_EmailAlreadyExists_ThrowsException() {
        when(personRepository.existsByEmail(personRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> personService.createPerson(personRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");

        verify(personRepository, never()).save(any());
    }

    @Test
    void getPersonById_Success() {
        when(personRepository.findById(1L)).thenReturn(Optional.of(person));
        when(personMapper.toResponse(person)).thenReturn(personResponse);

        PersonResponse result = personService.getPersonById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(personRepository).findById(1L);
    }

    @Test
    void getPersonById_NotFound_ThrowsException() {
        when(personRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> personService.getPersonById(999L))
                .isInstanceOf(PersonNotFoundException.class)
                .hasMessageContaining("Person not found with id: 999");
    }

    @Test
    void getAllPersons_Success() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Person> personPage = new PageImpl<>(List.of(person));

        when(personRepository.findAll(pageable)).thenReturn(personPage);
        when(personMapper.toResponse(person)).thenReturn(personResponse);

        Page<PersonResponse> result = personService.getAllPersons(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("john.doe@example.com");
        verify(personRepository).findAll(pageable);
    }

    @Test
    void updatePerson_Success() {
        PersonRequest updateRequest = new PersonRequest();
        updateRequest.setFirstName("Jane");
        updateRequest.setLastName("Doe");
        updateRequest.setEmail("john.doe@example.com");
        updateRequest.setCity("Boston");

        when(personRepository.findById(1L)).thenReturn(Optional.of(person));
        when(personRepository.save(person)).thenReturn(person);
        when(personMapper.toResponse(person)).thenReturn(personResponse);

        PersonResponse result = personService.updatePerson(1L, updateRequest);

        assertThat(result).isNotNull();
        verify(personMapper).updateEntityFromRequest(updateRequest, person);
        verify(personRepository).save(person);
    }

    @Test
    void updatePerson_EmailChange_EmailAlreadyExists_ThrowsException() {
        PersonRequest updateRequest = new PersonRequest();
        updateRequest.setEmail("another@example.com");

        when(personRepository.findById(1L)).thenReturn(Optional.of(person));
        when(personRepository.existsByEmail("another@example.com")).thenReturn(true);

        assertThatThrownBy(() -> personService.updatePerson(1L, updateRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");

        verify(personRepository, never()).save(any());
    }

    @Test
    void deletePerson_Success() {
        when(personRepository.existsById(1L)).thenReturn(true);

        personService.deletePerson(1L);

        verify(personRepository).deleteById(1L);
    }

    @Test
    void deletePerson_NotFound_ThrowsException() {
        when(personRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> personService.deletePerson(999L))
                .isInstanceOf(PersonNotFoundException.class)
                .hasMessageContaining("Person not found with id: 999");

        verify(personRepository, never()).deleteById(any());
    }

    @Test
    void findByEmail_Success() {
        when(personRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(person));
        when(personMapper.toResponse(person)).thenReturn(personResponse);

        PersonResponse result = personService.findByEmail("john.doe@example.com");

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
        verify(personRepository).findByEmail("john.doe@example.com");
    }

    @Test
    void findByEmail_NotFound_ThrowsException() {
        when(personRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> personService.findByEmail("notfound@example.com"))
                .isInstanceOf(PersonNotFoundException.class)
                .hasMessageContaining("Person not found with email");
    }
}
