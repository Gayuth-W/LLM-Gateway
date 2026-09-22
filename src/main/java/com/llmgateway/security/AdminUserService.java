package com.llmgateway.security;

import com.llmgateway.exception.InvalidCredentialsException;
import com.llmgateway.model.AdminUser;
import com.llmgateway.repository.AdminUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Verifies operator credentials.
 *
 * <p><b>Why the scheduler hop matters.</b> BCrypt is intentionally slow and
 * CPU-bound -- roughly 50-100ms at cost 10. Running that inline on a Netty event
 * loop would stall every other in-flight request sharing that thread, which on a
 * gateway means stalling proxied completions. The comparison therefore runs on
 * {@code boundedElastic}. This is the one genuinely blocking operation the login
 * path performs.
 */
@Service
public class AdminUserService {

    /**
     * A real BCrypt hash of a value nobody knows, compared against when the
     * username does not exist. Without it, a missing user returns in ~1ms while a
     * real user takes ~80ms, and the difference alone enumerates valid accounts.
     */
    private static final String DUMMY_HASH =
            "$2a$10$wtBmkOKVzyqDs3cz6Uy.eezVyAnnszPbCJAc9SVUc2O1j.sOJDHRa";

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(AdminUserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @return the operator on success
     * @throws InvalidCredentialsException on unknown user, disabled account, or wrong password
     */
    public Mono<AdminUser> authenticate(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isEmpty()) {
            return Mono.error(new InvalidCredentialsException());
        }

        return repository.findByUsername(username.trim())
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap(maybeUser -> Mono
                        .fromCallable(() -> verify(maybeUser, rawPassword))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private AdminUser verify(java.util.Optional<AdminUser> maybeUser, String rawPassword) {
        if (maybeUser.isEmpty()) {
            passwordEncoder.matches(rawPassword, DUMMY_HASH);   // equalise timing
            throw new InvalidCredentialsException();
        }
        AdminUser user = maybeUser.get();
        if (!user.isEnabled()) {
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
    }
}
