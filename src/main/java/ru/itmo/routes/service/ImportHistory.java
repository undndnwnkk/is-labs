package ru.itmo.routes.service;

import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ru.itmo.routes.domain.ImportOperation;
import java.util.List;

@Stateless
@DeclareRoles({"user", "admin"})
@RolesAllowed({"user", "admin"})
public class ImportHistory {
    @PersistenceContext(unitName = "routesPU") private EntityManager em;
    @Resource private SessionContext caller;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Long start() {
        ImportOperation operation = new ImportOperation(caller.getCallerPrincipal().getName());
        em.persist(operation);
        return operation.getId();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public ImportOperation fail(Long id, String message) {
        ImportOperation operation = owned(id);
        operation.fail(message);
        return operation;
    }

    public ImportOperation succeed(Long id, int count) {
        ImportOperation operation = owned(id);
        operation.succeed(count);
        return operation;
    }

    public List<ImportOperation> all() {
        boolean admin = caller.isCallerInRole("admin");
        return em.createQuery("select i from ImportOperation i where :admin = true or i.owner = :owner order by i.id desc",
                        ImportOperation.class)
                .setParameter("admin", admin)
                .setParameter("owner", caller.getCallerPrincipal().getName()).getResultList();
    }

    private ImportOperation owned(Long id) {
        ImportOperation operation = em.find(ImportOperation.class, id);
        if (operation == null || !operation.getOwner().equals(caller.getCallerPrincipal().getName())) {
            throw new IllegalArgumentException("Операция импорта не найдена");
        }
        if (operation.getStatus() != ImportOperation.Status.RUNNING) {
            throw new IllegalArgumentException("Операция импорта уже завершена");
        }
        return operation;
    }
}
