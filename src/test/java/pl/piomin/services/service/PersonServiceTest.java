package pl.piomin.services.service;

import pl.piomin.services.model.Person;
import pl.piomin.services.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
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

    @InjectMocks
    private PersonService personService;

    private Person person1;
    private Person person2;

    @BeforeEach
    void setUp() {
        person1 = new Person("John", "Doe", "john.doe@example.com", LocalDate.of(1990, 1, 1));
        person1.setId(1L);

        person2 = new Person("Jane", "Smith", "jane.smith@example.com", LocalDate.of(1992, 5, 15));
        person2.setId(2L);
    }

    @Test
    void getAllPersons_ReturnsListOfPersons() {
        List<Person> persons = Arrays.asList(person1, person2);
        when(personRepository.findAll()).thenReturn(persons);

        List<Person> result = personService.getAllPersons();

        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(person1, person2);
        verify(personRepository, times(1)).findAll();
    }

    @Test
    void getPersonById_ExistingId_ReturnsPerson() {
        when(personRepository.findById(1L)).thenReturn(Optional.of(person1));

        Optional<Person> result = personService.getPersonById(1L);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(person1);
        verify(personRepository, times(1)).findById(1L);
    }

    @Test
    void getPersonById_NonExistingId_ReturnsEmpty() {
        when(personRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Person> result = personService.getPersonById(99L);

        assertThat(result).isEmpty();
        verify(personRepository, times(1)).findById(99L);
    }

    @Test
    void createPerson_ValidPerson_ReturnsSavedPerson() {
        when(personRepository.save(any(Person.class))).thenReturn(person1);

        Person result = personService.createPerson(person1);

        assertThat(result).isEqualTo(person1);
        verify(personRepository, times(1)).save(person1);
    }

    @Test
    void updatePerson_ExistingId_ReturnsUpdatedPerson() {
        Person updatedPerson = new Person("Johnny", "Doe", "john.doe@example.com", LocalDate.of(1990, 1, 1));
        updatedPerson.setId(1L);

        when(personRepository.findById(1L)).thenReturn(Optional.of(person1));
        when(personRepository.save(any(Person.class))).thenReturn(updatedPerson);

        Person result = personService.updatePerson(1L, updatedPerson);

        assertThat(result).isEqualTo(updatedPerson);
        verify(personRepository, times(1)).findById(1L);
        verify(personRepository, times(1)).save(person1);
    }

    @Test
    void updatePerson_NonExistingId_ThrowsException() {
        Person updatedPerson = new Person("Johnny", "Doe", "john.doe@example.com", LocalDate.of(1990, 1, 1));

        when(personRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> personService.updatePerson(99L, updatedPerson))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Person not found with id: 99");
        verify(personRepository, times(1)).findById(99L);
        verify(personRepository, times(0)).save(any(Person.class));
    }

    @Test
    void deletePerson_ExistingId_DeletesPerson() {
        when(personRepository.findById(1L)).thenReturn(Optional.of(person1));

        personService.deletePerson(1L);

        verify(personRepository, times(1)).findById(1L);
        verify(personRepository, times(1)).delete(person1);
    }

    @Test
    void deletePerson_NonExistingId_ThrowsException() {
        when(personRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> personService.deletePerson(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Person not found with id: 99");
        verify(personRepository, times(1)).findById(99L);
        verify(personRepository, times(0)).delete(any(Person.class));
    }
}