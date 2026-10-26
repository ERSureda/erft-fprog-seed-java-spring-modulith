package com.template.api.shared.domain.model;

import java.util.Objects;

public abstract class BaseEntity<ID> {

    protected final ID id;

    protected BaseEntity(ID id) {
        this.id = Objects.requireNonNull(id, "ENTITY_ID_CANNOT_BE_NULL");
    }

    public ID id() {
        return id;
    }

    public ID getId() {
        return id();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEntity<?> that = (BaseEntity<?>) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }
}
