package com.kadikular.quantimium.entity;

import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.core.HolderLookup;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.kadikular.quantimium.item.SophonItem;
import com.kadikular.quantimium.superposition.Recovery;
import com.kadikular.quantimium.superposition.SophonRegistry;
import com.kadikular.quantimium.superposition.Superposition;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * A field double: the body left standing where its owner tethered away from. It holds still, cannot be
 * pushed or knocked about, and has no pod to guard it, so hostile mobs come for it (and other players, if
 * PvP is on). Hurt enough, it is knocked out: it comes apart into its Sophon, which lies where it stood,
 * safe from fire and time but free for anyone to pick up. A Recovery module can pull either back home.
 */
public class FieldDouble extends Mob {

    public static final float MAX_HEALTH = 20.0f;
    /** How far hostile mobs notice it from. */
    public static final double PROWL_RANGE = 12.0;
    private static final int WARN_COOLDOWN = 60;

    /** The owner's UUID as text, empty for none: the synced data has no UUID serializer any more. */
    private static final EntityDataAccessor<String> OWNER =
            SynchedEntityData.defineId(FieldDouble.class, EntityDataSerializers.STRING);

    @Nullable
    private UUID sophon;
    private String ownerName = "";
    private int warned;

    public FieldDouble(EntityType<? extends FieldDouble> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, "");
    }

    /** Stands {@code owner}'s double {@code sophon} here, facing the way they faced. */
    public void become(ServerPlayer owner, UUID sophon) {
        this.sophon = sophon;
        this.ownerName = owner.getGameProfile().name();
        entityData.set(OWNER, owner.getUUID().toString());
        setYRot(owner.getYRot());
        setYHeadRot(owner.getYRot());
        setYBodyRot(owner.getYRot());
    }

    @Nullable
    public UUID owner() {
        String text = entityData.get(OWNER);
        if (text.isEmpty()) return null;
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    public UUID sophon() {
        return sophon;
    }

    // ---- holding still ----

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {}

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // ---- being hunted ----

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || tickCount % 20 != 0) return;
        // Nothing guards it: whatever prowls nearby comes for it. A mite still in the mirror is not in this world.
        for (Monster monster : server.getEntitiesOfClass(Monster.class, new AABB(blockPosition()).inflate(PROWL_RANGE, 4, PROWL_RANGE),
                monster -> monster.isAlive() && monster.getTarget() == null
                        && !(monster instanceof MirrorEndermite mite && !mite.isBreached()))) {
            monster.setTarget(this);
        }
        if (warned > 0) warned -= 20;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Other players only when the server allows them to hurt each other.
        if (source.getEntity() instanceof Player && !level.getGameRules().get(GameRules.PVP)) {
            return false;
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && warned <= 0 && level() instanceof ServerLevel server && owner() != null) {
            warned = WARN_COOLDOWN;
            ServerPlayer owning = server.getServer().getPlayerList().getPlayer(owner());
            if (owning != null) owning.sendOverlayMessage(Component.translatable("message.quantimium.field.under_attack",
                    blockPosition().getX(), blockPosition().getZ()).withStyle(ChatFormatting.GOLD));
        }
        return hurt;
    }

    /** Knocked out: it comes apart into its Sophon, lying where it stood. */
    @Override
    public void die(DamageSource source) {
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        UUID owning = owner();
        if (sophon != null && owning != null) {
            ItemEntity item = new ItemEntity(server, getX(), getY() + 0.5, getZ(), SophonItem.of(sophon, owning, ownerName));
            item.setDefaultPickUpDelay();
            server.addFreshEntity(item);
            GlobalPos where = GlobalPos.of(server.dimension(), blockPosition());
            SophonRegistry.get(server.getServer()).dropped(sophon, where);
            Superposition.podEffect(server, blockPosition(), Superposition.EFFECT_ECHO);
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(owning);
            if (player != null) player.sendSystemMessage(Component.translatable("message.quantimium.field.knocked_out",
                    blockPosition().getX(), blockPosition().getZ()).withStyle(ChatFormatting.RED));
            Recovery.afterKnockOut(server.getServer(), owning, sophon, null);
        }
        discard();
    }

    /** Its owner, sneaking, folds it back into its Sophon and takes it. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive() || !player.getUUID().equals(owner())) return InteractionResult.PASS;
        if (level() instanceof ServerLevel server && sophon != null) {
            ItemStack folded = SophonItem.of(sophon, player.getUUID(), player.getGameProfile().name());
            if (!player.getInventory().add(folded)) player.drop(folded, false);
            SophonRegistry.get(server.getServer()).folded(sophon);
            Superposition.podEffect(server, blockPosition(), Superposition.EFFECT_FOLD);
            discard();
        }
        return InteractionResult.SUCCESS;
    }

    // ---- saving ----

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag);
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag) {
        if (sophon != null) tag.store("Sophon", net.minecraft.core.UUIDUtil.CODEC, sophon);
        if (owner() != null) tag.store("Owner", net.minecraft.core.UUIDUtil.CODEC, owner());
        tag.putString("OwnerName", ownerName);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        loadLegacy(NbtCompat.read(in));
    }

    private void loadLegacy(CompoundTag tag) {
        sophon = com.kadikular.quantimium.util.NbtCompat.hasUUID(tag, "Sophon") ? com.kadikular.quantimium.util.NbtCompat.getUUID(tag, "Sophon") : null;
        UUID savedOwner = NbtCompat.getUUID(tag, "Owner");
        entityData.set(OWNER, savedOwner == null ? "" : savedOwner.toString());
        ownerName = tag.getStringOr("OwnerName", "");
    }
}
