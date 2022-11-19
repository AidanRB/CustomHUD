package com.minenash.customhud.HudElements.icon;

import com.minenash.customhud.Flags;
import com.minenash.customhud.HudElements.HudElement;
import com.minenash.customhud.mixin.MinecraftClientAccess;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

public abstract class IconElement implements HudElement {
    private static final MinecraftClient client = MinecraftClient.getInstance();

    protected final float scale;
    protected final int shiftX;
    protected final int shiftY;
    protected final boolean referenceCorner;

    protected IconElement(Flags flags) {
        scale = (float) flags.scale;
        shiftX = flags.iconShiftX;
        shiftY = flags.iconShiftY;
        referenceCorner = flags.iconReferenceCorner;
    }

    public abstract int render(MatrixStack stack, int x, int y);
    public abstract int getTextWidth();

    @Override
    public String getString() {
        return "\uFFFE";
    }

    public void renderItemStack(int x, int y, ItemStack stack) {
        BakedModel model = client.getItemRenderer().getModel(stack, null, null, 0);
        client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).setFilter(false, false);
        RenderSystem.setShaderTexture(0, SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        MatrixStack matrixStack = RenderSystem.getModelViewStack();
        matrixStack.push();
        matrixStack.translate(x+5.5, y+5.5, 100.0f); //+ client.getItemRenderer().zOffset

        if (referenceCorner)
            matrixStack.translate(0, (11*scale-11)/2, 0);

        matrixStack.scale(11, -11, 1);
        if (scale != 1)
            matrixStack.scale(scale, scale, 1);

        if (!model.isSideLit())
            DiffuseLighting.disableGuiDepthLighting();

        RenderSystem.applyModelViewMatrix();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        client.getItemRenderer().renderItem(stack, ModelTransformation.Mode.GUI, false, new MatrixStack(), immediate, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, model);
        immediate.draw();
        RenderSystem.enableDepthTest();

        if (!model.isSideLit())
            DiffuseLighting.enableGuiDepthLighting();

        matrixStack.pop();
        RenderSystem.applyModelViewMatrix();
    }
}