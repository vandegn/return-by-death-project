package rbd;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class CustomSounds {
    private CustomSounds() {}

    public static final SoundEvent RESPAWN = registerSound("respawn");

    private static SoundEvent registerSound(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ReturnByDeath.MOD_ID, name);
        SoundEvent event = SoundEvent.createVariableRangeEvent(id);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, event);
    }

    public static void initialize() {
        ReturnByDeath.LOGGER.info("Registering " + ReturnByDeath.MOD_ID + " Sounds");
    }
}
