package com.mirrorsoul.mirrorsoul_api.common.security;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserRole;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final UUID uuid;
    private final String email;
    private final String password;
    private final UserRole role;
    private final UserStatus status;
    private final Long sessionVersion;

    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.uuid = user.getUuid();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.role = user.getRole();
        this.status = user.getStatus();
        this.sessionVersion = user.getSessionVersion();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (status != UserStatus.ACTIVE) {
            return Collections.emptyList();
        }
        if (role == UserRole.SUPER_ADMIN) {
            return java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        }
        return role == UserRole.ADMIN || role == UserRole.ADMIN_VIEWER
                ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()))
                : Collections.emptyList();
    }

    @Override
    public boolean isEnabled() {
        return status != UserStatus.INACTIVE && status != UserStatus.DELETED
                && status != UserStatus.SUSPENDED && status != UserStatus.WITHDRAWAL_CONFIRMED;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }
}
