package net.caffeinemc.mods.sodium.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigEntryPointForge {
    /**
     * The mod id to associate this config entrypoint's "owner" with.
     *
     * @return the mod id
     */
    String value();
}
