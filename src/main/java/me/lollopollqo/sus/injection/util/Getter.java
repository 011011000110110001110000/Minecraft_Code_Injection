package me.lollopollqo.sus.injection.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;

public class Getter <I, T> extends AbstractHandle<I, MethodHandle> {
    private Getter(@Nullable I instance, MethodHandle handle, boolean isStatic) {
        super(instance, handle, isStatic);
    }

    @SuppressWarnings("unchecked")
    public T get() throws Throwable {
        return (T) this.handle.invoke(this.instance);
    }

    @Override
    public void bindTo(@NotNull I instance) {
        super.bindTo(instance);
        this.handle.bindTo(instance);
    }
}
