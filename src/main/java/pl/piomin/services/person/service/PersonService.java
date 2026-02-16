package pl.piomin.services.person.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.piomin.services.person.dto.PersonRequest;
import pl.piomin.services.person.dto.PersonResponse;
import pl.piomin.services.person.entity.Person;
import pl.piomin.services.person.repository.PersonRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PersonService {

    private final PersonRepository personRepository;

    public PersonService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Transactional(readOnly = true)
    public List<PersonResponse> findAll() {
        return personRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<PersonResponse> findById(Long id) {
        return personRepository.findById(id).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<PersonResponse> findByPesel(String pesel) {
        return personRepository.findByPesel(pesel).map(this::toResponse);
    }

    @Transactional
    public PersonResponse save(PersonRequest request) {
        Person person = toEntity(request);
        Person saved = personRepository.save(person);
        return toResponse(saved);
    }

    @Transactional
    public Optional<PersonResponse> update(Long id, PersonRequest request) {
        return personRepository.findById(id).map(person -> {
            person.setFirstName(request.getFirstName());
            person.setLastName(request.getLastName());
            person.setPesel(request.getPesel());
            person.setBirthDate(request.getBirthDate());
            person.setEmail(request.getEmail());
            person.setPhone(request.getPhone());
            person.setAddress(request.getAddress());
            return toResponse(personRepository.save(person));
        });
    }

    @Transactional
    public boolean delete(Long id) {
        if (personRepository.existsById(id)) {
            personRepository.deleteById(id);
            return true;
        }
        return false;
    }

    private Person toEntity(PersonRequest request) {
        Person person = new Person();
        person.setFirstName(request.getFirstName());
        person.setLastName(request.getLastName());
        person.setPesel(request.getPesel());
        person.setBirthDate(request.getBirthDate());
        person.setEmail(request.getEmail());
        person.setPhone(request.getPhone());
        person.setAddress(request.getAddress());
        return person;
    }

    private PersonResponse toResponse(Person person) {
        PersonResponse response = new PersonResponse();
        response.setId(person.getId());
        response.setFirstName(person.getFirstName());
        response.setLastName(person.getLastName());
        response.setPesel(person.getPesel());
        response.setBirthDate(person.getBirthDate());
        response.setEmail(person.getEmail());
        response.setPhone(person.getPhone());
        response.setAddress(person.getAddress());
        return response;
    }
}
