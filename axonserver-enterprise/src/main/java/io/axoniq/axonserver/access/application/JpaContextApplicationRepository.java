package io.axoniq.axonserver.access.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Author: marc
 */
public interface JpaContextApplicationRepository extends JpaRepository<JpaContextApplication, Long> {

    Optional<JpaContextApplication> findJpaContextApplicationByContextAndName(String context, String name);

    List<JpaContextApplication> findAllByTokenPrefix(String tokenPrefix);

    List<JpaContextApplication> findAllByContext(String context);
}