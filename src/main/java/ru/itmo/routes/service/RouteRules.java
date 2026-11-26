package ru.itmo.routes.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Validator;
import ru.itmo.routes.domain.Route;
import ru.itmo.routes.dto.RouteForm;

@ApplicationScoped
public class RouteRules {
    @PersistenceContext(unitName = "routesPU") private EntityManager em;
    @Inject private Validator validator;

    public void lock() {
        em.createNativeQuery("select 1 from pg_advisory_xact_lock(12014548)").getSingleResult();
    }

    public void validate(RouteForm form, Integer excludingId) {
        var violations = validator.validate(form);
        if (!violations.isEmpty()) throw new BusinessException(violations.iterator().next().getMessage());
        if (!Double.isFinite(form.getCoordinateX()) || !Float.isFinite(form.getDistance()))
            throw new BusinessException("Координаты и расстояние должны быть конечными числами");
        if (exists("lower(trim(r.name)) = lower(trim(:name))", excludingId, form, true))
            throw new BusinessException("Название маршрута уже используется");
        if (exists("r.coordinates.x = :x and r.coordinates.y = :y", excludingId, form, false))
            throw new BusinessException("Координаты маршрута уже используются");
    }

    private boolean exists(String condition, Integer id, RouteForm form, boolean name) {
        var query = em.createQuery("select count(r) from Route r where (" + condition
                + ") and (:id is null or r.id <> :id)", Long.class).setParameter("id", id);
        if (name) query.setParameter("name", form.getName());
        else query.setParameter("x", form.getCoordinateX()).setParameter("y", form.getCoordinateY());
        return query.getSingleResult() > 0;
    }

    public void checkVersion(Route route, Long expected) {
        if (expected == null || route.getVersion() != expected)
            throw new BusinessException("Маршрут уже изменён другим пользователем. Обновите страницу");
    }
}
