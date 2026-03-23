package pl.piomin.services.infrastructure.exception;

public class PersonNotFoundException extends RuntimeException {

    public PersonNotFoundException(String message) {
        super(message);
    }

    public PersonNotFoundException(Long id) {
        super("Person not found with id: " + id);
    }
}
