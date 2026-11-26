package ru.itmo.routes.service;

import jakarta.ejb.SessionContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import ru.itmo.routes.domain.ImportOperation;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.security.Principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportHistoryTest {
    @Test
    void anotherUserCannotCompleteOrFailAnImportEvenWhenTheIdIsKnown() throws Exception {
        ImportOperation operation = new ImportOperation("alice");
        ImportHistory history = history("bob", operation);

        assertThatThrownBy(() -> history.fail(1L, "failed")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> history.succeed(1L, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThat(operation.getStatus()).isEqualTo(ImportOperation.Status.RUNNING);
        assertThat(operation.getAddedCount()).isNull();
    }

    @Test
    void ownerMayFinishTheOperationAndFailureNeverHasAnAddedCount() throws Exception {
        ImportOperation operation = new ImportOperation("alice");
        ImportHistory history = history("alice", operation);

        assertThat(history.succeed(1L, 2).getAddedCount()).isEqualTo(2);
        ImportOperation failed = history("alice", new ImportOperation("alice")).fail(2L, "error");
        assertThat(failed.getAddedCount()).isNull();
        assertThat(failed.getStatus()).isEqualTo(ImportOperation.Status.FAILED);
        assertThatThrownBy(() -> history.fail(1L, "error"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Операция импорта уже завершена");
    }

    @Test
    void unknownOperationIsRejected() throws Exception {
        assertThatThrownBy(() -> history("alice", null).succeed(1L, 2))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Операция импорта не найдена");
    }

    private static ImportHistory history(String username, ImportOperation operation) throws Exception {
        ImportHistory history = new ImportHistory();
        EntityManager em = (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                new Class<?>[]{EntityManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("find")) return operation;
                    throw new UnsupportedOperationException(method.getName());
                });
        SessionContext context = (SessionContext) Proxy.newProxyInstance(SessionContext.class.getClassLoader(),
                new Class<?>[]{SessionContext.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getCallerPrincipal")) return (Principal) () -> username;
                    throw new UnsupportedOperationException(method.getName());
                });
        inject(history, "em", em);
        inject(history, "caller", context);
        return history;
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
