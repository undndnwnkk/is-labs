package ru.itmo.routes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "import_operations")
public class ImportOperation implements Serializable {
    public enum Status { RUNNING, SUCCESS, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String owner;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "added_count")
    private Integer addedCount;

    @Column(length = 1000)
    private String error;

    public ImportOperation() {}

    public ImportOperation(String owner) {
        this.owner = owner;
        this.startedAt = LocalDateTime.now();
        this.status = Status.RUNNING;
    }

    public void succeed(int count) { status = Status.SUCCESS; addedCount = count; error = null; }
    public void fail(String message) {
        status = Status.FAILED;
        addedCount = null;
        error = message.substring(0, Math.min(message.length(), 1000));
    }

    public Long getId() { return id; }
    public String getOwner() { return owner; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public Status getStatus() { return status; }
    public Integer getAddedCount() { return addedCount; }
    public String getError() { return error; }
}
