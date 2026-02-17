package pl.piomin.services.claudeai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.piomin.services.claudeai.domain.Person;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {
}
