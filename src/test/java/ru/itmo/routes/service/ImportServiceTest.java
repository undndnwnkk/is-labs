package ru.itmo.routes.service;

import jakarta.ejb.EJBException;
import org.junit.jupiter.api.Test;
import ru.itmo.routes.domain.ImportOperation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ImportServiceTest {
    @Test
    void recordsFailureAfterTheTransactionalCallHasReturnedAndKeepsRecordNumber() throws Exception {
        List<String> calls = new ArrayList<>();
        RecordingHistory history = new RecordingHistory(calls);
        ImportTransaction transaction = new ImportTransaction() {
            @Override public ImportOperation execute(Long id, byte[] bytes) {
                calls.add("execute");
                throw new EJBException(new IllegalArgumentException("Запись 2: название уже используется"));
            }
        };
        ImportService service = service(history, transaction);

        ImportOperation result = service.run(new byte[]{1});

        assertThat(calls).containsExactly("start", "execute", "fail");
        assertThat(result.getStatus()).isEqualTo(ImportOperation.Status.FAILED);
        assertThat(result.getAddedCount()).isNull();
        assertThat(result.getError()).isEqualTo("Запись 2: название уже используется");
    }

    @Test
    void successfulTransactionResultIsReturnedWithoutASeparateSuccessCommit() throws Exception {
        List<String> calls = new ArrayList<>();
        RecordingHistory history = new RecordingHistory(calls);
        ImportOperation committed = new ImportOperation("alice");
        committed.succeed(2);
        ImportTransaction transaction = new ImportTransaction() {
            @Override public ImportOperation execute(Long id, byte[] bytes) {
                calls.add("execute");
                return committed;
            }
        };

        ImportOperation result = service(history, transaction).run(new byte[]{1});

        assertThat(result).isSameAs(committed);
        assertThat(calls).containsExactly("start", "execute");
    }

    @Test
    void serverFailureDoesNotExposeInternalDetailsInHistory() throws Exception {
        RecordingHistory history = new RecordingHistory(new ArrayList<>());
        ImportTransaction transaction = new ImportTransaction() {
            @Override public ImportOperation execute(Long id, byte[] bytes) {
                throw new EJBException("jdbc:postgresql://internal-host secret");
            }
        };

        ImportOperation result = service(history, transaction).run(new byte[]{1});

        assertThat(result.getError()).isEqualTo("Импорт не выполнен: внутренняя ошибка сервера");
    }

    private static ImportService service(ImportHistory history, ImportTransaction transaction) throws Exception {
        ImportService service = new ImportService();
        inject(service, "history", history);
        inject(service, "transaction", transaction);
        return service;
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class RecordingHistory extends ImportHistory {
        private final List<String> calls;
        private final ImportOperation operation = new ImportOperation("alice");
        RecordingHistory(List<String> calls) { this.calls = calls; }
        @Override public Long start() { calls.add("start"); return 1L; }
        @Override public ImportOperation fail(Long id, String message) {
            calls.add("fail");
            operation.fail(message);
            return operation;
        }
    }
}
