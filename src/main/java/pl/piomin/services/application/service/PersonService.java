package pl.piomin.services.application.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.piomin.services.application.dto.PersonRequest;
import pl.piomin.services.application.dto.PersonResponse;
import pl.piomin.services.application.mapper.PersonMapper;
import pl.piomin.services.domain.entity.Person;
import pl.piomin.services.domain.repository.PersonRepository;
import pl.piomin.services.infrastructure.exception.PersonNotFoundException;

@Service
@Transactional
public class PersonService {

    private final PersonRepository personRepository;
    private final PersonMapper personMapper;

    public PersonService(PersonRepository personRepository, PersonMapper personMapper) {
        this.personRepository = personRepository;
        this.personMapper = personMapper;
    }

    public PersonResponse createPerson(PersonRequest request) {
        if (personRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        Person person = personMapper.toEntity(request);
        Person saved = personRepository.save(person);
        return personMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PersonResponse getPersonById(Long id) {
        Person person = personRepository.findById(id)
                .orElseThrow(() -> new PersonNotFoundException(id));
        return personMapper.toResponse(person);
    }

    @Transactional(readOnly = true)
    public Page<PersonResponse> getAllPersons(Pageable pageable) {
        return personRepository.findAll(pageable)
                .map(personMapper::toResponse);
    }

    public PersonResponse updatePerson(Long id, PersonRequest request) {
        Person person = personRepository.findById(id)
                .orElseThrow(() -> new PersonNotFoundException(id));

        if (!person.getEmail().equals(request.getEmail()) &&
                personRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        personMapper.updateEntityFromRequest(request, person);
        Person updated = personRepository.save(person);
        return personMapper.toResponse(updated);
    }

    public void deletePerson(Long id) {
        if (!personRepository.existsById(id)) {
            throw new PersonNotFoundException(id);
        }
        personRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public PersonResponse findByEmail(String email) {
        Person person = personRepository.findByEmail(email)
                .orElseThrow(() -> new PersonNotFoundException("Person not found with email: " + email));
        return personMapper.toResponse(person);
    }
}
