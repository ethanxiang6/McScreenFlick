package com.ethanxiang6.screenflick;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

public final class ScreenFlickClient implements ClientModInitializer {
	public static final String MOD_ID = "screenflick";

	private static KeyMapping flickKey;

	@Override
	public void onInitializeClient() {
		FlickConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		flickKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.screenflick.flick", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean pressed = false;
			while (flickKey.consumeClick()) {
				pressed = true;
			}
			FlickController.tick(client, pressed);
		});
	}
}
