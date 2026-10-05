package com.kadikular.quantimium.block.entity.simulation;

import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/** Client-facing description of one tank on the contained machine. */
public record MachineTankInfo(int tankIndex, @Nullable Direction face, FluidStack contents,
                              boolean acceptsInput, boolean producesOutput, int capacity) {

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineTankInfo> STREAM_CODEC = StreamCodec.of(
            (buf, info) -> {
                buf.writeVarInt(info.tankIndex);
                buf.writeByte(info.face == null ? -1 : info.face.get3DDataValue());
                FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, info.contents);
                buf.writeBoolean(info.acceptsInput);
                buf.writeBoolean(info.producesOutput);
                buf.writeVarInt(info.capacity);
            },
            buf -> {
                int tankIndex = buf.readVarInt();
                int faceId = buf.readByte();
                FluidStack contents = FluidStack.OPTIONAL_STREAM_CODEC.decode(buf);
                boolean acceptsInput = buf.readBoolean();
                boolean producesOutput = buf.readBoolean();
                int capacity = buf.readVarInt();
                return new MachineTankInfo(tankIndex, faceId < 0 ? null : Direction.from3DDataValue(faceId),
                        contents, acceptsInput, producesOutput, capacity);
            });
}
