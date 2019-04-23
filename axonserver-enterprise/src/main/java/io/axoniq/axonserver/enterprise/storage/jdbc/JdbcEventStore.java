package io.axoniq.axonserver.enterprise.storage.jdbc;

import io.axoniq.axonserver.localstorage.EventTypeContext;

import javax.sql.DataSource;

/**
 * @author Marc Gathier
 */
public class JdbcEventStore extends JdbcAbstractStore{

    public JdbcEventStore(EventTypeContext eventTypeContext,
                          DataSource dataSource) {
        super(eventTypeContext, dataSource);
    }

    protected String getTableName() {
        return "DOMAIN_EVENT_ENTRY";
    }

    @Override
    public void deleteAllEventData() {
        throw new UnsupportedOperationException("Development mode deletion is not supported in Jdbc backed event stores");
    }
}
