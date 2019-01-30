package io.axoniq.axonserver.access.jpa;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * @author Marc Gathier
 */
@Entity
@Table(name="users")
public class User {
    @Id
    @Column(name="username")
    private String userName;
    private String password;
    private boolean enabled;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, mappedBy = "user")
    private Set<UserRole> roles = new HashSet<>();

    public User(String userName, String password) {
        this(userName, password, new String[]{"READ"});
    }

    public User(String userName, String password, String[] roles) {
        this.userName = userName;
        this.password = password;
        this.enabled = true;
        if( roles == null ) this.roles.add(new UserRole(this, "READ"));
        else {
            Arrays.stream(roles).forEach(r -> this.roles.add(new UserRole(this, r)));
        }
    }

    public User() {
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<UserRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<UserRole> roles) {
        this.roles = roles;
    }

    public void addRole(String string) {
        UserRole userRole = new UserRole(this, string);
        roles.add(userRole);
    }
}
