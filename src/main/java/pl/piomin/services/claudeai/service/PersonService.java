package pl.piomin.services.claudeai.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.piomin.services.claudeai.domain.Person;
import pl.piomin.services.claudeai.repository.PersonRepository;
import java.util.List;
import java.util.Optional;

@Service
public class PersonService {
    private final PersonRepository repository;

    public PersonService(PersonRepository repository) {
        this.repository = repository;
    }

    public List<Person> findAll() {
        return repository.findAll();
    }

    public Optional<Person> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public Person create(Person person) {
        return repository.save(person);
    }

    @Transactional
    public Optional<Person> update(Long id, Person person) {
        return repository.findById(id).map(existing -> {
            existing.setFirstName(person.getFirstName());
            existing.setLastName(person.getLastName());
            existing.setEmail(person.getEmail());
            existing.setAge(person.getAge());
            return repository.save(existing);
        });
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
