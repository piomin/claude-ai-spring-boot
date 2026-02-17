package pl.piomin.services.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // For demo purposes, we're using a hardcoded user
        // In a real application, you would load the user from a database
        if ("user".equals(username)) {
            return new User("user", "$2a$10$slYQmyNdGzHFp33eD.2VBO0WSjWgSqUtRVTgqZvXpMRQrqvUN/ZPq", // password: password
                    new ArrayList<>());
        } else if ("admin".equals(username)) {
            return new User("admin", "$2a$10$slYQmyNdGzHFp33eD.2VBO0WSjWgSqUtRVTgqZvXpMRQrqvUN/ZPq", // password: password
                    new ArrayList<>());
        } else {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }
    }
}