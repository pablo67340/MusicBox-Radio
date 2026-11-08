package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.item.HeadphoneMaterial;
import com.musicbox.item.HeadphonesItem;
import com.musicbox.item.SpeakerItem;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, MusicBox.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MusicBox.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MusicBox.MOD_ID);

    private static final ResourceKey<ArmorMaterial> HEADPHONE_MATERIAL_KEY = ResourceKey.create(
            Registries.ARMOR_MATERIAL, ResourceLocation.fromNamespaceAndPath(MusicBox.MOD_ID, HeadphoneMaterial.NAME));

    public static final RegistryObject<ArmorMaterial> HEADPHONE_MATERIAL =
            ARMOR_MATERIALS.register(HeadphoneMaterial.NAME, HeadphoneMaterial::create);

    public static final RegistryObject<Item> MUSIC_BOX = ITEMS.register("music_box",
            () -> new BlockItem(ModBlocks.MUSIC_BOX.get(), new Item.Properties()));

    public static final RegistryObject<Item> SPEAKER = ITEMS.register("speaker",
            () -> new SpeakerItem(ModBlocks.SPEAKER.get(), new Item.Properties()));

    // Forge fires the item registry before armor materials, so the material is not registered
    // yet when this runs. The registration lookup hands back the holder the material will be
    // bound into a moment later; ArmorItem only reads it lazily.
    @SuppressWarnings("unchecked")
    public static final RegistryObject<Item> HEADPHONES = ITEMS.register("headphones",
            () -> new HeadphonesItem(
                    ((WritableRegistry<ArmorMaterial>) BuiltInRegistries.ARMOR_MATERIAL)
                            .createRegistrationLookup().getOrThrow(HEADPHONE_MATERIAL_KEY),
                    new Item.Properties().stacksTo(1)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MusicBox.MOD_ID))
                    .icon(() -> new ItemStack(MUSIC_BOX.get()))
                    .displayItems((params, output) -> {
                        output.accept(MUSIC_BOX.get());
                        output.accept(SPEAKER.get());
                        output.accept(HEADPHONES.get());
                    })
                    .build());

    private ModItems() {
    }
}
