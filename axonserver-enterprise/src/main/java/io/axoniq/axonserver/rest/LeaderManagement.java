package io.axoniq.axonserver.rest;

import io.axoniq.axonserver.enterprise.cluster.LocalRaftGroupService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rest APIs for Leader management. Only enabled when active profiles contains internal.
 *
 * @author Marc Gathier
 * @since 4.1
 */
@RestController
@CrossOrigin
@Profile("internal")
@RequestMapping("internal")
public class LeaderManagement {
    private final LocalRaftGroupService localRaftGroupService;

    public LeaderManagement(LocalRaftGroupService localRaftGroupService) {
        this.localRaftGroupService = localRaftGroupService;
    }

    /**
     * Forces the current leader for the specified context to step down.
     * @param name the context
     */
    @GetMapping( path = "context/{name}/stepdown")
    public void stepdown(@PathVariable("name")  String name) {
        localRaftGroupService.stepDown(name);
    }

}
