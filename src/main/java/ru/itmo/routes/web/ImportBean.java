package ru.itmo.routes.web;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.Part;
import ru.itmo.routes.domain.ImportOperation;
import ru.itmo.routes.service.ImportParser;
import ru.itmo.routes.service.ImportService;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
public class ImportBean implements Serializable {
    @Inject private ImportService service;
    private transient Part file;
    private List<ImportOperation> operations;

    @PostConstruct public void reload() { operations = service.history(); }

    public void upload() {
        FacesContext context = FacesContext.getCurrentInstance();
        if (file == null) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Выберите JSON-файл", null));
            return;
        }
        try (var input = file.getInputStream()) {
            ImportOperation result = service.run(input.readNBytes(ImportParser.MAX_BYTES + 1));
            boolean success = result.getStatus() == ImportOperation.Status.SUCCESS;
            context.addMessage(null, new FacesMessage(success ? FacesMessage.SEVERITY_INFO : FacesMessage.SEVERITY_ERROR,
                    success ? "Импорт #" + result.getId() + ": добавлено маршрутов — " + result.getAddedCount() : result.getError(), null));
            reload();
        } catch (IOException exception) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Не удалось прочитать загруженный файл", null));
        } finally {
            file = null;
        }
    }

    public Part getFile() { return file; }
    public void setFile(Part file) { this.file = file; }
    public List<ImportOperation> getOperations() { return operations; }
}
