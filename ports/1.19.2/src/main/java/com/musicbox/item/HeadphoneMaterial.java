package com.musicbox.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Headphones are modelled as head armour purely so they occupy the helmet slot on every
 * loader without loader-specific equipment hooks. They grant no protection whatsoever.
 */
public enum HeadphoneMaterial implements ArmorMaterial {
    INSTANCE;

    @Override
    public int getDurabilityForSlot(EquipmentSlot slot) {
        return 0;
    }

    @Override
    public int getDefenseForSlot(EquipmentSlot slot) {
        return 0;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(Items.IRON_INGOT);
    }

    @Override
    public String getName() {
        // Vanilla derives the worn texture from this name as
        // assets/minecraft/textures/models/armor/<name>_layer_1.png. Keeping it in the
        // vanilla namespace means the same code works on Forge and Fabric alike, so the
        // name is prefixed to stay unique.
        return "musicboxradio_headphones";
    }

    @Override
    public float getToughness() {
        return 0.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0F;
    }
}
