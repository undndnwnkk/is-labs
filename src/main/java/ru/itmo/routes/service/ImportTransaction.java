package ru.itmo.routes.service;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ru.itmo.routes.domain.ImportOperation;
import java.util.List;

@Stateless
public class ImportTransaction {
    @Inject private LocationService locations;
    @Inject private RouteService routes;
    @Inject private ImportHistory history;
    @PersistenceContext(unitName = "routesPU") private EntityManager em;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public ImportOperation execute(Long operationId, byte[] contents) {
        List<ImportParser.Entry> entries = ImportParser.parse(contents);
        for (int i = 0; i < entries.size(); i++) {
            ImportParser.Entry entry = entries.get(i);
            try {
                entry.route().setFromId(locations.create(entry.from()).getId());
                if (entry.to() != null) entry.route().setToId(locations.create(entry.to()).getId());
                routes.create(entry.route());
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Запись " + (i + 1) + ": " + ImportService.failureMessage(exception), exception);
            }
        }
        em.flush();
        return history.succeed(operationId, entries.size());
    }
}
