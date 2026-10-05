package com.kadikular.quantimium.client.model;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.EnumSet;
import java.util.Set;

/**
 * The Veiled: hood, two-part robe and long arms, three blocks tall, floating. Texture is
 * 128×64; see {@code textures/entity/veiled.png} for where each box's faces sit.
 *
 * <p>Motion is kept small on purpose. It never walks with swinging limbs: it floats on a slow bob,
 * the hood turns to stare, the arms hang with a slow sway, and the robe trails the drift.
 */
public class VeiledModel extends EntityModel<VeiledModel.State> {

    /** What the model reads: the head turn and age every living state has, and how faded it is. */
    public static class State extends LivingEntityRenderState {
        /** 1 whole; it fades as the Lance wears it down. Sightings and the hall leave it whole. */
        public float opacity = 1.0f;
    }

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "veiled"), "main");

    private final ModelPart hood;
    private final ModelPart robe;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    /** Set from the entity each frame: it fades as the Lance wears it down. Sightings and the hall pass none. */
    private float opacity = 1.0f;

    public VeiledModel(ModelPart root) {
        super(root);
        this.hood = root.getChild("hood");
        this.robe = root.getChild("robe");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
    }

    private static final Set<Direction> SIDES_AND_TOP = EnumSet.complementOf(EnumSet.of(Direction.UP));

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Model space runs downwards with the ground at y = 24. Hood top at y = -24, hem at y = 22:
        // 46 px of figure with no feet, which setupAnim lifts clear of the ground.
        root.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-5.0F, -11.0F, -5.0F, 10.0F, 11.0F, 10.0F), PartPose.offset(0.0F, -13.0F, 0.0F));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 21)
                .addBox(-6.0F, 0.0F, -4.0F, 12.0F, 19.0F, 8.0F), PartPose.offset(0.0F, -13.0F, 0.0F));
        // The lower robe has no bottom face of its own. Its floor is a separate plane 4px up, so the
        // ragged hem hangs below it as open cloth: the holes cut into the hem show air, not the
        // robe's underside. Model Y runs downwards, so the face seen from below is UP.
        PartDefinition robe = root.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(40, 0)
                .addBox(-7.0F, 0.0F, -5.0F, 14.0F, 16.0F, 10.0F, SIDES_AND_TOP), PartPose.offset(0.0F, 6.0F, 0.0F));
        robe.addOrReplaceChild("robe_floor", CubeListBuilder.create().texOffs(40, 0)
                .addBox(-7.0F, 12.0F, -5.0F, 14.0F, 0.0F, 10.0F, EnumSet.of(Direction.UP)), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(88, 0)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 26.0F, 3.0F), PartPose.offset(-7.5F, -12.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(100, 0).mirror()
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 26.0F, 3.0F), PartPose.offset(7.5F, -12.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    /** For the face, which is drawn in the hood's own space. */
    public ModelPart hood() {
        return hood;
    }

    /** The fade set in the last setupAnim. */
    public float opacity() {
        return opacity;
    }

    /** {@code color} with its alpha scaled by the fade, as every pass over this model should draw. */
    public int tint(int color) {
        return ARGB.multiplyAlpha(color, opacity);
    }

    @Override
    public void setupAnim(State state) {
        super.setupAnim(state);
        pose(state.ageInTicks, state.yRot, state.xRot, state.opacity);
    }

    /** Poses it without an entity, for the hall's occupant and the sightings, which draw it directly. */
    public void pose(float ageInTicks, float netHeadYaw, float headPitch, float opacity) {
        resetPose();
        this.opacity = opacity;
        hood.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        hood.xRot = headPitch * Mth.DEG_TO_RAD;

        float sway = Mth.sin(ageInTicks * 0.045F);
        rightArm.zRot = 0.04F + sway * 0.03F;
        leftArm.zRot = -0.04F - sway * 0.03F;
        rightArm.xRot = Mth.sin(ageInTicks * 0.031F + 1.0F) * 0.04F;
        leftArm.xRot = Mth.sin(ageInTicks * 0.029F) * 0.04F;

        // It floats: 3 to 5 px off the ground on a slow bob, the lower robe trailing the motion a
        // little so the cloth reads as hanging rather than rigid.
        float bob = Mth.sin(ageInTicks * 0.06F);
        root().y = -2.0F + bob;
        robe.xRot = Mth.sin(ageInTicks * 0.06F - 0.8F) * 0.035F;
        robe.zRot = Mth.sin(ageInTicks * 0.041F) * 0.02F;
    }
}
