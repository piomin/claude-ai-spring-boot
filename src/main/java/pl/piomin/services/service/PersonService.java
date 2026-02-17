package pl.piomin.services.service;

import pl.piomin.services.model.Person;
import pl.piomin.services.repository.PersonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PersonService {

    @Autowired
    private PersonRepository personRepository;

    public List<Person> getAllPersons() {
        return personRepository.findAll();
    }

    public Optional<Person> getPersonById(Long id) {
        return personRepository.findById(id);
    }

    public Person createPerson(Person person) {
        return personRepository.save(person);
    }

    public Person updatePerson(Long id, Person personDetails) {
        Person person = personRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Person not found with id: " + id));

        person.setFirstName(personDetails.getFirstName());
        person.setLastName(personDetails.getLastName());
        person.setEmail(personDetails.getEmail());
        person.setBirthDate(personDetails.getBirthDate());

        return personRepository.save(person);
    }

    public void deletePerson(Long id) {
        Person person = personRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Person not found with id: " + id));
        personRepository.delete(person);
    }
}