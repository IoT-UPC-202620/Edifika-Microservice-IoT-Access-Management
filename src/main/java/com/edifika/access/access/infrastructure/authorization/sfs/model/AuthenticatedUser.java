package com.edifika.access.access.infrastructure.authorization.sfs.model;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Principal de Spring Security construido a partir del JWT emitido por IAM.
 *
 * @param userId      id del usuario en IAM.
 * @param username    nombre de usuario o subject del token.
 * @param email       correo del usuario, si el token lo incluye.
 * @param authorities roles normalizados con prefijo "ROLE_".
 */
public record AuthenticatedUser(
        Long userId,
        String username,
        String email,
        Set<String> authorities
) {
    public AuthenticatedUser {
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
    }

    public boolean isAdmin() {
        return authorities.contains(Roles.toAuthority(Roles.ADMIN));
    }

    public boolean canAccessDataOf(Long ownerUserId) {
        return isAdmin() || Objects.equals(userId, ownerUserId);
    }

    public Long requireUserId() {
        if (userId == null) {
            throw new InsufficientAuthenticationException("El token no contiene el id del usuario");
        }
        return userId;
    }

    public List<GrantedAuthority> grantedAuthorities() {
        return authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
