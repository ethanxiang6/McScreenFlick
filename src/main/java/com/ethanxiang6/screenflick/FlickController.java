package com.ethanxiang6.screenflick;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Runs the combo as a small per-tick state machine.
 *
 * Tick N   (key pressed): lock a target, snap the crosshair flickAngle degrees past its center.
 * Tick N+1 (the hit):     the flicked yaw has now been sent to the server by the normal movement
 *                         packet, so the attack lands with that yaw and the knockback is pushed
 *                         along it. Then snap back to center and start the W-tap or S-tap.
 * Tick N+2 ...            hold the tap for tapTicks, then hand the keys back and re-sprint.
 */
public final class FlickController {
	private enum Phase { IDLE, FLICKED, TAPPING }

	private static Phase phase = Phase.IDLE;
	private static LivingEntity target;
	private static int tapTicksLeft;

	private static boolean suppressForward;
	private static boolean forceBack;

	private FlickController() {
	}

	/** Called from the KeyMapping mixin. Returns null when the key should behave normally. */
	public static Boolean overrideFor(KeyMapping key) {
		if (!suppressForward && !forceBack) {
			return null;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) {
			return null;
		}

		if (suppressForward && key == mc.options.keyUp) {
			return Boolean.FALSE;
		}
		if (forceBack && key == mc.options.keyDown) {
			return Boolean.TRUE;
		}
		return null;
	}

	static void tick(Minecraft mc, boolean pressed) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null) {
			reset();
			return;
		}

		switch (phase) {
			case IDLE -> {
				if (pressed && mc.gui.screen() == null) {
					startFlick(mc, player);
				}
			}
			case FLICKED -> strike(mc, player);
			case TAPPING -> {
				tapTicksLeft--;
				if (tapTicksLeft <= 0) {
					endTap(mc, player);
				}
			}
		}
	}

	private static void startFlick(Minecraft mc, LocalPlayer player) {
		LivingEntity found = findTarget(mc, player);
		if (found == null) {
			return;
		}

		FlickConfig cfg = FlickConfig.get();
		float centerYaw = yawTo(player, found);
		float currentYaw = player.getYRot();

		// Strafe key picks the side: A flicks (and knocks them) left, D flicks right.
		// With neither held, keep going the way the crosshair is already crossing them.
		boolean left = mc.options.keyLeft.isDown();
		boolean right = mc.options.keyRight.isDown();
		int dir;
		if (left && !right) {
			dir = -1;
		} else if (right && !left) {
			dir = 1;
		} else {
			dir = Mth.wrapDegrees(centerYaw - currentYaw) >= 0.0f ? 1 : -1;
		}

		float flickYaw = centerYaw + dir * cfg.flickAngle;
		player.setYRot(currentYaw + Mth.wrapDegrees(flickYaw - currentYaw));

		target = found;
		phase = Phase.FLICKED;
	}

	private static void strike(Minecraft mc, LocalPlayer player) {
		LivingEntity t = target;

		if (t == null || !t.isAlive() || t.isRemoved() || reachDistance(player, t) > player.entityInteractionRange()) {
			if (t != null) {
				snapToCenter(player, t);
			}
			reset();
			return;
		}

		mc.gameMode.attack(player, t);
		player.swing(InteractionHand.MAIN_HAND);

		snapToCenter(player, t);

		FlickConfig cfg = FlickConfig.get();
		suppressForward = true;
		forceBack = cfg.isSTap();
		tapTicksLeft = cfg.tapTicks;
		phase = Phase.TAPPING;

		if (tapTicksLeft <= 0) {
			endTap(mc, player);
		}
	}

	private static void endTap(Minecraft mc, LocalPlayer player) {
		suppressForward = false;
		forceBack = false;

		if (FlickConfig.get().autoResprint
				&& mc.options.keyUp.isDown()
				&& !player.isSprinting()
				&& !player.isUsingItem()
				&& (player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly)) {
			player.setSprinting(true);
		}

		phase = Phase.IDLE;
		target = null;
	}

	private static void reset() {
		suppressForward = false;
		forceBack = false;
		phase = Phase.IDLE;
		target = null;
		tapTicksLeft = 0;
	}

	private static LivingEntity findTarget(Minecraft mc, LocalPlayer player) {
		float cone = FlickConfig.get().targetCone;
		double range = player.entityInteractionRange();
		float yaw = player.getYRot();

		LivingEntity best = null;
		float bestAngle = Float.MAX_VALUE;

		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == player || !(e instanceof LivingEntity living)) {
				continue;
			}
			if (!(e instanceof Player || e instanceof Mob)) {
				continue;
			}
			if (!living.isAlive() || living.isRemoved() || living.isSpectator()) {
				continue;
			}
			if (reachDistance(player, living) > range) {
				continue;
			}

			float angle = Math.abs(Mth.wrapDegrees(yawTo(player, living) - yaw));
			if (angle <= cone && angle < bestAngle) {
				bestAngle = angle;
				best = living;
			}
		}

		return best;
	}

	private static void snapToCenter(LocalPlayer player, Entity t) {
		float current = player.getYRot();
		player.setYRot(current + Mth.wrapDegrees(yawTo(player, t) - current));
	}

	private static float yawTo(LocalPlayer player, Entity t) {
		return (float) Math.toDegrees(Math.atan2(t.getZ() - player.getZ(), t.getX() - player.getX())) - 90.0f;
	}

	/** Distance from your eyes to the closest point of the target's hitbox, matching how the server checks reach. */
	private static double reachDistance(LocalPlayer player, Entity t) {
		Vec3 eye = player.getEyePosition();
		AABB box = t.getBoundingBox();
		double dx = Math.max(box.minX - eye.x, Math.max(0.0, eye.x - box.maxX));
		double dy = Math.max(box.minY - eye.y, Math.max(0.0, eye.y - box.maxY));
		double dz = Math.max(box.minZ - eye.z, Math.max(0.0, eye.z - box.maxZ));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
