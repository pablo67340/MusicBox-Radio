package com.musicbox.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Headphones are cosmetic: no protection, no durability, no enchanting. They are armor only so
 * that they render on the head and occupy the helmet slot.
 */
//? if >=1.21.2 {
/*public final class HeadphoneMaterial {

    public static final String NAME = "headphones";

    private HeadphoneMaterial() {
    }

    // Wearable on the head with the look from assets/musicboxradio/equipment/headphones.json.
    // An armor material is not used here: from 1.21.2 those must be enchantable.
    public static net.minecraft.world.item.equipment.Equippable equippable() {
        return net.minecraft.world.item.equipment.Equippable.builder(net.minecraft.world.entity.EquipmentSlot.HEAD)
                .setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
                //? if >=1.21.4 {
                /^.setAsset(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, com.musicbox.MusicBox.id(NAME)))
                ^///?} else {
                .setModel(com.musicbox.MusicBox.id(NAME))
                //?}
                .build();
    }
}
*///?} else if >=1.20.5 {
public final class HeadphoneMaterial {

    public static final String NAME = "headphones";

    private HeadphoneMaterial() {
    }

    public static net.minecraft.world.item.ArmorMaterial create() {
        java.util.Map<net.minecraft.world.item.ArmorItem.Type, Integer> defense =
                new java.util.EnumMap<>(net.minecraft.world.item.ArmorItem.Type.class);
        for (net.minecraft.world.item.ArmorItem.Type type : net.minecraft.world.item.ArmorItem.Type.values()) {
            defense.put(type, 0);
        }
        // The worn texture resolves to assets/musicboxradio/textures/models/armor/headphones_layer_1.png.
        return new net.minecraft.world.item.ArmorMaterial(defense, 0, SoundEvents.ARMOR_EQUIP_LEATHER,
                () -> Ingredient.of(Items.IRON_INGOT),
                java.util.List.of(new net.minecraft.world.item.ArmorMaterial.Layer(com.musicbox.MusicBox.id(NAME))),
                0.0F, 0.0F);
    }
}
//?} else {
/*public enum HeadphoneMaterial implements net.minecraft.world.item.ArmorMaterial {
    INSTANCE;

    //? if >=1.19.4 {
    @Override
    public int getDurabilityForType(net.minecraft.world.item.ArmorItem.Type type) {
        return 0;
    }

    @Override
    public int getDefenseForType(net.minecraft.world.item.ArmorItem.Type type) {
        return 0;
    }
    //?} else {
    /^@Override
    public int getDurabilityForSlot(net.minecraft.world.entity.EquipmentSlot slot) {
        return 0;
    }

    @Override
    public int getDefenseForSlot(net.minecraft.world.entity.EquipmentSlot slot) {
        return 0;
    }
    ^///?}

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(Items.IRON_INGOT);
    }

    @Override
    public String getName() {
        // Vanilla derives the worn texture from this name as
        // assets/minecraft/textures/models/armor/<name>_layer_1.png, so it is prefixed to
        // stay unique in the vanilla namespace.
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
*///?}
