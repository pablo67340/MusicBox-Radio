package com.musicbox.platform;

import net.minecraft.core.Holder;

import java.util.function.Supplier;

/** A registered value, plus its registry holder for the few APIs that take one. */
public interface RegistryRef<T> extends Supplier<T> {

    Holder<T> holder();
}
