package io.axoniq.axonserver.rest.json;

import io.axoniq.axonserver.KeepNames;
import io.axoniq.axonserver.access.jpa.Application;
import io.axoniq.axonserver.access.jpa.ApplicationRole;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Marc Gathier
 */
@KeepNames
public class ApplicationJSON {

    private String name;

    private String description;

    private String token;
    private List<ApplicationRoleJSON> roles = new ArrayList<>();

    public ApplicationJSON() {
    }

    public ApplicationJSON(Application application) {
        name = application.getName();
        description = application.getDescription();
        roles = application.getRoles().stream().map(ApplicationRoleJSON::new).collect(Collectors.toList());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public List<ApplicationRoleJSON> getRoles() {
        return roles;
    }

    public void setRoles(List<ApplicationRoleJSON> roles) {
        this.roles = roles;
    }

    public Application toApplication() {
        return new Application(name, description, null, token,
                               roles.stream().map(ApplicationRoleJSON::toApplicationRole)
                                    .toArray(ApplicationRole[]::new));
    }

    @KeepNames
    public static class ApplicationRoleJSON {
        private String role;

        private String context;

        private Date endDate;

        public ApplicationRoleJSON() {

        }
        public ApplicationRoleJSON(ApplicationRole applicationRole) {
            role = applicationRole.getRole();
            context = applicationRole.getContext();
            endDate = applicationRole.getEndDate();
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContext() {
            return context;
        }

        public void setContext(String context) {
            this.context = context;
        }

        public Date getEndDate() {
            return endDate;
        }

        public void setEndDate(Date endDate) {
            this.endDate = endDate;
        }

        public ApplicationRole toApplicationRole() {
            return new ApplicationRole(role, context, endDate);
        }
    }
}
