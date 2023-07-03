package me.lollopollqo.sus.injection.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AbstractHandle<I, H> {
    @Nullable
    protected I instance;
    protected final H handle;
    protected final boolean isStatic;

    protected AbstractHandle(@Nullable I instance, H handle, boolean isStatic) {
        final boolean couldBeStatic = instance == null;

        if (isStatic && !couldBeStatic) {
            throw new IllegalArgumentException("Cannot construct a static handle with a non-null instance");
        }

        this.instance = instance;
        this.handle = handle;
        this.isStatic = isStatic;
    }

    public void bindTo(@NotNull I instance) {
        if (this.isStatic) {
            throw new UnsupportedOperationException("Cannot bind static handle to an object instance");
        }

        this.instance = instance;
    }

    public boolean isBound() {
        return !this.isStatic && this.instance != null;
    }

    public @Nullable I getInstance() {
        return this.instance;
    }

    public H getHandle() {
        return this.handle;
    }

    public boolean isStatic() {
        return this.isStatic;
    }
}
