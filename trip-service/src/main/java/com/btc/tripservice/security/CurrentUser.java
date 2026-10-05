package com.btc.tripservice.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller's identity, taken only from the verified JWT (never from request bodies or X-User-* headers).
 */
public record CurrentUser(Long id, boolean admin) {

    public static final String ADMIN_ROLE = "ADMIN";

    public static CurrentUser from(Jwt jwt) {
        try {
            return new CurrentUser(Long.valueOf(jwt.getSubject()), ADMIN_ROLE.equals(jwt.getClaimAsString("role")));
        } catch (NumberFormatException exception) {
            throw new BadCredentialsException("Token subject is not a user id");
        }
    }

    public boolean owns(Long ownerId) {
        return id.equals(ownerId);
    }

    /**
     * The owner a list or summary may cover. Administrators may ask for anyone (null means everyone); everyone
     * else always gets their own records, and asking for another user's is refused.
     */
    public Long scopeOwner(Long requestedOwnerId) {
        if (admin) {
            return requestedOwnerId;
        }
        if (requestedOwnerId != null && !owns(requestedOwnerId)) {
            throw new AccessDeniedException("You can only view your own records");
        }
        return id;
    }
}
