package com.exchange.matching.security;

import com.exchange.matching.persistence.entity.UserEntity;
import com.exchange.matching.persistence.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Charge un utilisateur depuis la base pour Spring Security (cours: "UserDetailsService:
 * service pour charger les utilisateurs depuis la base").
 *
 * Utilise par l'AuthenticationManager lors de la connexion: il compare ensuite
 * le mot de passe saisi au hache BCrypt renvoye ici.
 *
 * Le role devient l'autorite "ROLE_TRADER" ou "ROLE_ADMIN", exactement comme
 * dans le cours (prefixe "ROLE_" attendu par hasRole()).
 *
 * Le verrouillage temporaire (trop d'echecs) est porte par le drapeau
 * accountLocked: Spring refuse alors la connexion avec une LockedException.
 * La suspension par un administrateur n'est volontairement PAS traduite en
 * compte "disabled": Spring verifie ce drapeau AVANT le mot de passe, ce qui
 * reveleraient a n'importe qui qu'un compte existe et est suspendu. Elle est
 * verifiee apres un mot de passe correct, dans AuthService.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Utilisateur inconnu: " + username));
        return User.withUsername(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
            .accountLocked(user.isLocked(Instant.now()))
            .build();
    }
}
