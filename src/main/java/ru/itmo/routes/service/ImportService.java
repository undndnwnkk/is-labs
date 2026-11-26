package ru.itmo.routes.service;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import ru.itmo.routes.domain.ImportOperation;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Stateless
@RolesAllowed({"user", "admin"})
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ImportService {
    private static final Logger LOG = Logger.getLogger(ImportService.class.getName());
    @Inject private ImportHistory history;
    @Inject private ImportTransaction transaction;

    public ImportOperation run(byte[] contents) {
        Long id = history.start();
        try {
            return transaction.execute(id, contents);
        } catch (RuntimeException exception) {
            LOG.log(Level.WARNING, "Import operation " + id + " rolled back", exception);
            return history.fail(id, failureMessage(exception));
        }
    }

    public List<ImportOperation> history() { return history.all(); }

    static String failureMessage(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException && cause.getMessage() != null) return cause.getMessage();
        }
        return "Импорт не выполнен: внутренняя ошибка сервера";
    }
}
