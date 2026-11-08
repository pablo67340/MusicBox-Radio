package com.musicbox.item;

import com.musicbox.MusicBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Headphones are modelled as head armour purely so they occupy the helmet slot on every
 * loader without loader-specific equipment hooks. They grant no protection whatsoever.
 */
public final class HeadphoneMaterial {

    public static final String NAME = "headphones";

    private HeadphoneMaterial() {
    }

    public static ArmorMaterial create() {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            defense.put(type, 0);
        }
        // The worn texture resolves to assets/musicboxradio/textures/models/armor/headphones_layer_1.png.
        return new ArmorMaterial(defense, 0, SoundEvents.ARMOR_EQUIP_LEATHER,
                () -> Ingredient.of(Items.IRON_INGOT),
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(MusicBox.MOD_ID, NAME))),
                0.0F, 0.0F);
    }
}
