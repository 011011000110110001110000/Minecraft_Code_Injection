package me.lollopollqo.sus.injection.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;

public class Setter <I, T> extends AbstractHandle<I, MethodHandle>{
    private Setter(@Nullable I instance, MethodHandle handle, boolean isStatic) {
        super(instance, handle, isStatic);
    }

    public void set(T value) throws Throwable {
        if (this.isStatic) {
            this.handle.invoke(value);
        } else {
            this.handle.invoke(this.instance, value);
        }

    }

    @Override
    public void bindTo(@NotNull I instance) {
        super.bindTo(instance);
        this.handle.bindTo(instance);
    }

}
