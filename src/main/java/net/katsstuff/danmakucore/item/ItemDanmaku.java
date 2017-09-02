/*
 * This class was created by <Katrix>. It's distributed as
 * part of the DanmakuCore Mod. Get the Source Code in github:
 * https://github.com/Katrix-/DanmakuCore
 *
 * DanmakuCore is Open Source and distributed under the
 * the DanmakuCore license: https://github.com/Katrix-/DanmakuCore/blob/master/LICENSE.md
 */
package net.katsstuff.danmakucore.item;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.katsstuff.danmakucore.DanmakuCore;
import net.katsstuff.danmakucore.data.Quat;
import net.katsstuff.danmakucore.data.ShotData;
import net.katsstuff.danmakucore.data.Vector3;
import net.katsstuff.danmakucore.entity.danmaku.DanmakuTemplate;
import net.katsstuff.danmakucore.entity.danmaku.DanmakuVariant;
import net.katsstuff.danmakucore.entity.danmaku.form.Form;
import net.katsstuff.danmakucore.helper.BooleanNBTProperty;
import net.katsstuff.danmakucore.helper.DanmakuCreationHelper;
import net.katsstuff.danmakucore.helper.DanmakuHelper;
import net.katsstuff.danmakucore.helper.DoubleNBTProperty;
import net.katsstuff.danmakucore.helper.IntNBTProperty;
import net.katsstuff.danmakucore.helper.LogHelper;
import net.katsstuff.danmakucore.helper.MathUtil;
import net.katsstuff.danmakucore.helper.NBTProperty;
import net.katsstuff.danmakucore.helper.StringNBTProperty;
import net.katsstuff.danmakucore.lib.LibItemName;
import net.katsstuff.danmakucore.lib.data.LibDanmakuVariants;
import net.katsstuff.danmakucore.lib.data.LibItems;
import net.katsstuff.danmakucore.lib.data.LibSubEntities;
import net.katsstuff.danmakucore.registry.DanmakuRegistry;
import net.katsstuff.danmakucore.registry.RegistryValueShootable;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings({"unused", "WeakerAccess"})
public class ItemDanmaku extends ItemBase {

	public static final DoubleNBTProperty<ItemStack> GRAVITYX = DoubleNBTProperty.ofStack("gravityx");
	public static final DoubleNBTProperty<ItemStack> GRAVITYY = DoubleNBTProperty.ofStack("gravityy");
	public static final DoubleNBTProperty<ItemStack> GRAVITYZ = DoubleNBTProperty.ofStack("gravityz");
	public static final DoubleNBTProperty<ItemStack> SPEED = DoubleNBTProperty.ofStack("speed", 0.4D);
	public static final IntNBTProperty<ItemStack> PATTERN = IntNBTProperty.ofStack("pattern");
	public static final IntNBTProperty<ItemStack> AMOUNT = IntNBTProperty.ofStack("amount", 1);
	public static final BooleanNBTProperty<ItemStack> INFINITY = BooleanNBTProperty.ofStack("infinity");
	public static final BooleanNBTProperty<ItemStack> CUSTOM = BooleanNBTProperty.ofStack("custom");
	public static final StringNBTProperty<ItemStack> VARIANT = StringNBTProperty.ofStack("variant", () -> LibDanmakuVariants.DEFAULT_TYPE.getFullNameString());

	public ItemDanmaku() {
		super(LibItemName.DANMAKU);
		setMaxDamage(0);
		setCreativeTab(DanmakuCore.DANMAKU_CREATIVE_TAB);
	}

	@Override
	public void getSubItems(Item itemIn, CreativeTabs tab, NonNullList<ItemStack> subItems) {
		subItems.addAll(DanmakuRegistry.DANMAKU_VARIANT.getValues().stream()
				.sorted()
				.map(ItemDanmaku::createStack)
				.collect(Collectors.toList()));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean hasEffect(ItemStack stack) {
		return INFINITY.get(stack);
	}

	@Override
	public String getUnlocalizedName(ItemStack stack) {
		return this.getUnlocalizedName() + "." + getController(stack).getUnlocalizedName();
	}

	@Override
	public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
		ItemStack stack = player.getHeldItem(hand);
		if(!getController(stack).onRightClick(stack, world, player, hand)) return super.onItemRightClick(world, player, hand);

		boolean success = false;
		if(!world.isRemote) {
			if(player.capabilities.isCreativeMode) {
				INFINITY.set(true, stack);
			}
			ShotData shot = ShotData.fromNBTItemStack(stack);

			success = shootDanmaku(stack, player.world, player, player.isSneaking(),
					new Vector3(player.posX, player.posY + player.eyeHeight - shot.sizeY() / 2, player.posZ), new Vector3(player.getLookVec()),
					(shot.sizeZ() / 3) * 2);

			if(!INFINITY.get(stack) && success) {
				stack.shrink(1);
			}
		}

		DanmakuHelper.playShotSound(player);
		return new ActionResult<>(success || world.isRemote ? EnumActionResult.SUCCESS : EnumActionResult.FAIL, stack);
	}

	public static boolean shootDanmaku(ItemStack stack, World world, @Nullable EntityLivingBase player, boolean alternateMode, Vector3 pos,
			Vector3 direction, double offset) {
		if(!getController(stack).onShootDanmaku(player, alternateMode, pos, direction)) return false;
		int amount = AMOUNT.get(stack);
		double shotSpeed = SPEED.get(stack);
		Pattern danmakuPattern = getPattern(stack);
		ShotData shot = ShotData.fromNBTItemStack(stack);
		Vector3 gravity = getGravity(stack);

		float wide;
		DanmakuTemplate.Builder danmaku = DanmakuTemplate.builder();
		danmaku.setUser(player).setShot(shot).setWorld(world).setMovementData(shotSpeed, gravity).setPos(pos).setDirection(direction);
		DanmakuTemplate built = danmaku.build();
		Quat orientation = Quat.orientationOf(player);

		switch(danmakuPattern) {
			case LINE:
				danmaku.setPos(pos.offset(direction, offset));

				for(int i = 1; i <= amount; i++) {
					danmaku.setMovementData(shotSpeed / amount * i);
					built.world.spawnEntity(danmaku.build().asEntity());
				}
				break;
			case RANDOM_RING:
				wide = 120F;
				if(alternateMode) {
					wide *= 0.5F;
				}
				DanmakuCreationHelper.createRandomRingShot(orientation, built, amount, wide, offset);
				break;
			case WIDE:
				wide = amount * 8F;
				if(alternateMode) {
					wide = wide * 0.5F;
				}
				DanmakuCreationHelper.createWideShot(orientation, built, amount, wide, 0F, offset);
				break;
			case CIRCLE:
				DanmakuCreationHelper.createCircleShot(orientation, built, amount, 0F, offset);
				break;
			case STAR:
				danmaku.setMovementData(Vector3.GravityZero());
				DanmakuCreationHelper.createStarShot(orientation, danmaku.build(), amount, 0F, 0F, offset);
				break;
			case RING:
				wide = 15F;
				if(alternateMode) {
					wide *= 0.5F;
				}
				DanmakuCreationHelper.createRingShot(orientation, built, amount, wide, world.rand.nextFloat() * 360, offset);
				break;
			case SPHERE:
				DanmakuCreationHelper.createSphereShot(orientation, built, amount, amount / 2, 0F, offset);
				break;
			default:
				break;
		}

		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean bool) {
		super.addInformation(stack, player, list, bool);
		ShotData shot = ShotData.fromNBTItemStack(stack);
		int amount = AMOUNT.get(stack);
		double shotSpeed = SPEED.get(stack);
		Pattern danmakuPattern = getPattern(stack);
		Vector3 gravity = getGravity(stack);
		boolean isInfinity = INFINITY.get(stack);
		boolean custom = CUSTOM.get(stack);

		float powerDamage = BigDecimal.valueOf(DanmakuHelper.adjustDamageCoreData(player, shot.damage()) - shot.damage()).setScale(4,
				BigDecimal.ROUND_HALF_UP).floatValue();
		String item = "item.danmaku";

		list.add(I18n.format(item + ".damage") + " : " + shot.damage() + " + " + powerDamage);
		list.add(I18n.format(item + ".size") + " : " + shot.sizeX() + ", " + shot.sizeY() + " " + shot.sizeZ());
		list.add(I18n.format(item + ".amount") + " : " + amount);
		if(custom) {
			list.add(I18n.format(item + ".form") + " : " + I18n.format(shot.form().getUnlocalizedName()));
		}
		list.add(I18n.format(item + ".pattern") + " : " + I18n.format(item + ".pattern." + danmakuPattern));
		list.add(I18n.format(item + ".speed") + " : " + shotSpeed);
		if(!MathUtil.fuzzyEqual(gravity.x(), 0D) || !MathUtil.fuzzyEqual(gravity.y(), 0D) || !MathUtil.fuzzyEqual(gravity.z(), 0D)) {
			list.add(I18n.format(item + ".gravity") + " : " + gravity.x() + " " + gravity.y() + " " + gravity.z());
		}
		else {
			list.add(I18n.format(item + ".gravity") + " : " + I18n.format(item + ".noGravity"));
		}
		if(DanmakuHelper.isNormalColor(shot.color())) {
			list.add(I18n.format(item + ".color") + " : " + I18n.format(item + ".color." + shot.color()));
		}
		else {
			list.add(I18n.format(item + ".color") + " : " + I18n.format(item + ".color.custom"));
		}
		if(shot.subEntity() != LibSubEntities.DEFAULT_TYPE) {
			list.add(I18n.format(item + ".subentity") + " : " + I18n.format(shot.subEntity().getUnlocalizedName()));
		}
		if(isInfinity) {
			list.add(I18n.format(item + ".infinity"));
		}
		if(custom) {
			list.add(I18n.format(item + ".custom"));
		}
	}

	@SuppressWarnings("WeakerAccess")
	public static Vector3 getGravity(ItemStack stack) {
		double gravityX = GRAVITYX.get(stack);
		double gravityY = GRAVITYY.get(stack);
		double gravityZ = GRAVITYZ.get(stack);
		return new Vector3(gravityX, gravityY, gravityZ);
	}

	public static void setGravity(Vector3 gravity, ItemStack stack) {
		GRAVITYX.set(gravity.x(), stack);
		GRAVITYY.set(gravity.y(), stack);
		GRAVITYZ.set(gravity.z(), stack);
	}

	@SuppressWarnings("WeakerAccess")
	public static Pattern getPattern(ItemStack stack) {
		return Pattern.class.getEnumConstants()[PATTERN.get(stack)];
	}

	public static void setPattern(ItemStack stack, Pattern pattern) {
		PATTERN.set(pattern.ordinal(), stack);
	}

	public static DanmakuVariant getVariant(ItemStack stack) {
		DanmakuVariant variant = DanmakuRegistry.DANMAKU_VARIANT.getValue(new ResourceLocation(VARIANT.get(stack)));
		if(variant == null) {
			variant = LibDanmakuVariants.DEFAULT_TYPE;
			LogHelper.warn("Found null variant. Changing to default");
			VARIANT.set(variant.getFullNameString(), stack);
		}

		return variant;
	}

	public static Form getForm(ItemStack stack) {
		return ShotData.fromNBTItemStack(stack).getForm();
	}

	@SuppressWarnings("squid:S1452")
	public static RegistryValueShootable<?> getController(ItemStack stack) {
		return !CUSTOM.get(stack) ? getVariant(stack) : getForm(stack);
	}

	public static ItemStack createStack(DanmakuVariant variant) {
		ShotData shot = variant.getShotData().setColor(DanmakuHelper.randomSaturatedColor());
		ItemStack stack = new ItemStack(LibItems.DANMAKU, 1);
		VARIANT.set(variant.getFullNameString(), stack);
		setGravity(variant.getMovementData().gravity(), stack);
		SPEED.set(variant.getMovementData().speedOriginal(), stack);
		CUSTOM.set(false, stack);
		return ShotData.serializeNBTItemStack(stack, shot);
	}

	public enum Pattern {
		LINE,
		RANDOM_RING,
		WIDE,
		CIRCLE,
		STAR,
		RING,
		SPHERE
	}
}
