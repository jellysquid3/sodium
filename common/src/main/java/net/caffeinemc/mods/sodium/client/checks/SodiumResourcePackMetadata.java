package net.caffeinemc.mods.sodium.client.checks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.server.packs.metadata.MetadataSectionType;

/**
 * Reads additional metadata for Sodium from a resource pack's `pack.mcmeta` file. This allows the
 * resource pack author to specify which shaders from their pack are not usable with Sodium, but that
 * the author is aware of and is fine with being ignored.
 */
public record SodiumResourcePackMetadata(List<String> ignoredShaders) {
    public static final Codec<SodiumResourcePackMetadata> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(Codec.STRING.listOf().fieldOf("ignored_shaders")
                    .forGetter(SodiumResourcePackMetadata::ignoredShaders))
                    .apply(instance, SodiumResourcePackMetadata::new)
    );
    public static final MetadataSectionType<SodiumResourcePackMetadata> SERIALIZER =
            new MetadataSectionType<>("sodium", CODEC);
}
