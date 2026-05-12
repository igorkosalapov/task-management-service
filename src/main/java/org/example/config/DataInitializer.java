package org.example.config;

import lombok.RequiredArgsConstructor;
import org.example.entity.User;
import org.example.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User user1 = new User();
            user1.setName("Igor");
            user1.setEmail("igor@example.com");

            User user2 = new User();
            user2.setName("Anna");
            user2.setEmail("anna@example.com");

            User user3 = new User();
            user3.setName("Bob");
            user3.setEmail("bob@example.com");

            userRepository.save(user1);
            userRepository.save(user2);
            userRepository.save(user3);
        }
    }
}
