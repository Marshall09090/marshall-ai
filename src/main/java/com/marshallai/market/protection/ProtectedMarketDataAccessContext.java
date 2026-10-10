package com.marshallai.market.protection;

public final class ProtectedMarketDataAccessContext {

    private static final ThreadLocal<ProtectedMarketDataAuthorization>
            AUTHORIZATION =
            new ThreadLocal<>();

    private ProtectedMarketDataAccessContext() {
    }

    public static ProtectedMarketDataAuthorization
    currentAuthorization() {

        return AUTHORIZATION.get();
    }

    public static AuthorizationScope authorize(
            ProtectedMarketDataAuthorization authorization) {

        if (authorization == null) {
            throw new IllegalArgumentException(
                    "Protected market-data authorization cannot be null"
            );
        }

        ProtectedMarketDataAuthorization previous =
                AUTHORIZATION.get();

        AUTHORIZATION.set(
                authorization
        );

        return new AuthorizationScope(
                previous
        );
    }

    public static void clear() {
        AUTHORIZATION.remove();
    }

    public static final class AuthorizationScope
            implements AutoCloseable {

        private final ProtectedMarketDataAuthorization previous;

        private boolean closed;

        private AuthorizationScope(
                ProtectedMarketDataAuthorization previous) {

            this.previous = previous;
        }

        @Override
        public void close() {

            if (closed) {
                return;
            }

            if (previous == null) {
                AUTHORIZATION.remove();
            } else {
                AUTHORIZATION.set(
                        previous
                );
            }

            closed = true;
        }
    }
}