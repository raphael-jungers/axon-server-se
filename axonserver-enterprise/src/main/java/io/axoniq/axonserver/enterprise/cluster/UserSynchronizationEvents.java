package io.axoniq.axonserver.enterprise.cluster;

import io.axoniq.axonserver.KeepNames;
import io.axoniq.axonserver.grpc.internal.User;
import io.axoniq.axonserver.grpc.internal.Users;

/**
 * @author Marc Gathier
 */
public class UserSynchronizationEvents {

    @KeepNames
    public static class UsersReceived {
        private final Users users;

        public UsersReceived(Users users) {
            this.users = users;
        }

        public Users getUsers() {
            return users;
        }
    }

    @KeepNames
    public static class UserReceived {
        private final User user;
        private final boolean proxied;

        public UserReceived(User user, boolean proxied) {
            this.user = user;
            this.proxied = proxied;
        }

        public User getUser() {
            return user;
        }

        public boolean isProxied() {
            return proxied;
        }
    }
}
