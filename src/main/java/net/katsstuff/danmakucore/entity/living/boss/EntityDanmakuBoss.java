/*
 * This class was created by <Katrix>. It's distributed as
 * part of the DanmakuCore Mod. Get the Source Code in github:
 * https://github.com/Katrix-/DanmakuCore
 *
 * DanmakuCore is Open Source and distributed under the
 * the DanmakuCore license: https://github.com/Katrix-/DanmakuCore/blob/master/LICENSE.md
 */
package net.katsstuff.danmakucore.entity.living.boss;

import java.util.List;
import java.util.UUID;

import com.google.common.base.Optional;

import net.katsstuff.danmakucore.DanmakuCore;
import net.katsstuff.danmakucore.data.Vector3;
import net.katsstuff.danmakucore.entity.living.EntityDanmakuMob;
import net.katsstuff.danmakucore.entity.living.phase.Phase;
import net.katsstuff.danmakucore.helper.TouhouHelper;
import net.katsstuff.danmakucore.misc.LogicalSideOnly;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;

public abstract class EntityDanmakuBoss extends EntityDanmakuMob {

	private final BossInfoServer bossInfo = new BossInfoServer(this.getDisplayName(), BossInfo.Color.WHITE, BossInfo.Overlay.PROGRESS);

	private static final DataParameter<Optional<UUID>> BOSS_INFO_UUID = EntityDataManager.createKey(EntityDanmakuBoss.class,
			DataSerializers.OPTIONAL_UNIQUE_ID);

	public EntityDanmakuBoss(World world) {
		super(world);
		setupPhases();
		DanmakuCore.proxy.addDanmakuBoss(this);
	}

	@Override
	protected void entityInit() {
		super.entityInit();
		dataManager.register(BOSS_INFO_UUID, Optional.absent());
	}

	public UUID getBossInfoUUID() {
		return dataManager.get(BOSS_INFO_UUID).or(new UUID(0L, 0L));
	}

	@Override
	protected void updateAITasks() {
		super.updateAITasks();
		this.bossInfo.setPercent(this.getHealth() / this.getMaxHealth());
	}

	@SuppressWarnings("Guava")
	@Override
	public void onLivingUpdate() {
		super.onLivingUpdate();
		if(!worldObj.isRemote) {
			dataManager.set(BOSS_INFO_UUID, Optional.of(bossInfo.getUniqueId()));
		}
	}

	@Override
	public void onDeath(DamageSource cause) {
		if(phaseManager.hasNextPhase()) {
			Phase currentPhase = phaseManager.getCurrentPhase();
			phaseManager.nextPhase();
			if(!worldObj.isRemote) {
				setHealth(getMaxHealth());
				dropPhaseLoot(cause);
				currentPhase.dropLoot(cause);
			}
			isDead = false;
			deathTime = 0;
		}
		else {
			super.onDeath(cause);
			phaseManager.getCurrentPhase().deconstruct();
			DanmakuCore.proxy.removeDanmakuBoss(this);
		}
	}

	@Override
	public boolean isEntityInvulnerable(DamageSource source) {
		return phaseManager.getCurrentPhase().getCounter() < 0 || super.isEntityInvulnerable(source);
	}

	@Override
	public void addTrackingPlayer(EntityPlayerMP player) {
		super.addTrackingPlayer(player);
		this.bossInfo.addPlayer(player);
	}

	@Override
	public void removeTrackingPlayer(EntityPlayerMP player) {
		super.removeTrackingPlayer(player);
		this.bossInfo.removePlayer(player);
	}

	@Override
	public boolean isNonBoss() {
		return false;
	}

	private void setupPhases() {
		phaseManager.addPhases(getPhaseList());
	}

	public int remainingSpellcards() {
		return (int)phaseManager.getPhaseList().stream().skip(phaseManager.getCurrentPhaseIndex() + 1).filter(Phase::isSpellcard).count();
	}

	public abstract List<Phase> getPhaseList();

	@SuppressWarnings("unused")
	public abstract EnumTouhouCharacters getCharacter();

	@Override
	protected void dropLoot(boolean wasRecentlyHit, int lootingModifier, DamageSource source) {
		super.dropLoot(wasRecentlyHit, lootingModifier, source);
		phaseManager.getCurrentPhase().dropLoot(source);
	}

	@Override
	protected void dropPhaseLoot(DamageSource source) {
		super.dropPhaseLoot(source);

		int powerSpawns = rand.nextInt(6);
		Vector3 pos = new Vector3(this);
		Vector3 angle;
		if(source.getEntity() != null) {
			angle = Vector3.angleToEntity(this, source.getEntity());
		} else {
			angle = Vector3.Down();
		}

		for(int i = 0; i < powerSpawns; i++) {
			worldObj.spawnEntityInWorld(TouhouHelper.createPower(worldObj, pos, angle));
		}

		int pointSpawns = rand.nextInt(8);
		for(int i = 0; i < pointSpawns; i++) {
			worldObj.spawnEntityInWorld(TouhouHelper.createScoreBlue(worldObj, null, pos, angle));
		}

		if(rand.nextInt(100) < 10) {
			worldObj.spawnEntityInWorld(TouhouHelper.createBomb(worldObj, pos, angle));
		}

		if(rand.nextInt(100) < 2) {
			worldObj.spawnEntityInWorld(TouhouHelper.createLife(worldObj, pos, angle));
		}
	}
}
