package com.ether.mirrors.client;

import com.ether.mirrors.block.entity.MirrorBlockEntity;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class MirrorColorProvider implements BlockColor {

    public static final MirrorColorProvider INSTANCE = new MirrorColorProvider();

    @Override
    public int getColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        if (level == null || pos == null) return -1;
        if (level.getBlockEntity(pos) instanceof MirrorBlockEntity mirrorBE) {
            int dyeColor = mirrorBE.getDyeColor();
            if (dyeColor >= 0) {
                DyeColor color = DyeColor.byId(dyeColor);
                if (color != null) {
                    float[] rgb = color.getTextureDiffuseColors();
                    int r = (int)(rgb[0] * 255);
                    int g = (int)(rgb[1] * 255);
                    int b = (int)(rgb[2] * 255);
                    return 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }
        }
        return -1;
    }
}
