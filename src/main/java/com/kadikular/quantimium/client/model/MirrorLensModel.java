package com.kadikular.quantimium.client.model;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * The Mirror Lens as worn: a leather strap round the head at eye level, over the hat layer, and two
 * rimmed lenses standing a pixel proud of the face. In head space, so it is drawn after the head's
 * own pose: the head spans -8 to 0 in y and -4 to 4 across, and its face is at z -4.
 */
public final class MirrorLensModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "mirror_lens"), "main");

    private final ModelPart root;

    public MirrorLensModel(ModelPart root) {
        this.root = root;
    }

    public ModelPart root() {
        return root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Over the hat layer (which is inflated by half a pixel), so the strap shows on any skin.
        root.addOrReplaceChild("strap", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0f, -5.5f, -4.0f, 8, 2, 8, new CubeDeformation(0.65f)), PartPose.ZERO);
        root.addOrReplaceChild("left_lens", CubeListBuilder.create().texOffs(0, 10)
                .addBox(-3.75f, -6.0f, -5.6f, 3, 3, 1), PartPose.ZERO);
        root.addOrReplaceChild("right_lens", CubeListBuilder.create().texOffs(8, 10)
                .addBox(0.75f, -6.0f, -5.6f, 3, 3, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 16);
    }
}
