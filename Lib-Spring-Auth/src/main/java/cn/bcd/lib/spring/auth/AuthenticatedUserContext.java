package cn.bcd.lib.spring.auth;

import java.util.Optional;

public final class AuthenticatedUserContext {
    private static final ScopedValue<AuthUser> CURRENT = ScopedValue.newInstance();

    private AuthenticatedUserContext() {
    }

    public static Optional<AuthUser> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    static ScopedValue<AuthUser> scopedValue() {
        return CURRENT;
    }
}
