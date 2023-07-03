package me.lollopollqo.sus.injection.util;

import org.jetbrains.annotations.Nullable;

import java.lang.invoke.VarHandle;

public class TypedVarHandle <T> extends AbstractHandle<T, VarHandle> {
    private TypedVarHandle(@Nullable T instance, VarHandle handle, boolean isStatic) {
        super(instance, handle, isStatic);
    }
}
