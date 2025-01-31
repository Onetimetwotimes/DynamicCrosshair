package mod.crend.dynamiccrosshair.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mod.crend.dynamiccrosshair.DynamicCrosshair;
import mod.crend.dynamiccrosshair.component.Crosshair;
import mod.crend.dynamiccrosshair.component.CrosshairHandler;
import mod.crend.dynamiccrosshair.config.CrosshairColor;
import mod.crend.dynamiccrosshair.config.CrosshairModifier;
import mod.crend.dynamiccrosshair.config.CrosshairStyle;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.math.MatrixStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=InGameHud.class, priority=1010)
public class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At(value = "HEAD"), cancellable = true)
    private void dynamiccrosshair$preCrosshair(final MatrixStack matrixStack, final CallbackInfo ci) {
        if (!CrosshairHandler.shouldShowCrosshair()) ci.cancel();
    }

    private void dynamiccrosshair$setColor(final CrosshairColor color) {
        int argb = color.getColor();
        // convert ARGB hex to r, g, b, a floats
        RenderSystem.setShaderColor(((argb >> 16) & 0xFF) / 255.0f, ((argb >> 8) & 0xFF) / 255.0f, (argb & 0xFF) / 255.0f, ((argb >> 24) & 0xFF) / 255.0f);
        if (color.forced()) {
            RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        } else {
            RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE_MINUS_DST_COLOR, GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        }
    }

    @Redirect(method = "renderCrosshair", at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;debugEnabled:Z", opcode = Opcodes.GETFIELD), require = 0)
    private boolean dynamiccrosshair$debugCrosshair(GameOptions instance) {
        if (DynamicCrosshair.config.isDisableDebugCrosshair()) return false;
        return instance.debugEnabled;
    }

    @Redirect(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;drawTexture(Lnet/minecraft/client/util/math/MatrixStack;IIIIII)V", ordinal = 0), require = 0)
    private void dynamiccrosshair$drawCrosshair(InGameHud instance, MatrixStack matrixStack, int x, int y, int u, int v, int width, int height) {
        dynamiccrosshair$setColor(DynamicCrosshair.config.getColor());
        if (DynamicCrosshair.config.isDynamicCrosshairStyle()) {
            Crosshair crosshair = CrosshairHandler.getActiveCrosshair();
            RenderSystem.setShaderTexture(0, CrosshairHandler.crosshairTexture);
            if (crosshair.hasStyle()) {
                CrosshairStyle crosshairStyle = crosshair.getCrosshairStyle();
                dynamiccrosshair$setColor(crosshairStyle.getColor());
                instance.drawTexture(matrixStack, x, y, crosshairStyle.getStyle().getX(), crosshairStyle.getStyle().getY(), 15, 15);
            }
            for (CrosshairModifier modifier : crosshair.getModifiers()) {
                dynamiccrosshair$setColor(modifier.getColor());
                instance.drawTexture(matrixStack, x, y, modifier.getStyle().getX(), modifier.getStyle().getY(), 15, 15);
            }
            RenderSystem.setShaderTexture(0, InGameHud.GUI_ICONS_TEXTURE);
        } else {
            instance.drawTexture(matrixStack, x, y, u, v, width, height);
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE_MINUS_DST_COLOR, GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
    }

    @Redirect(method = "renderCrosshair", at = @At(value = "INVOKE", target="Lnet/minecraft/client/option/GameOptions;getPerspective()Lnet/minecraft/client/option/Perspective;"), require = 0)
    private Perspective dynamiccrosshair$thirdPersonCrosshair(GameOptions gameOptions) {
        Perspective originalPerspective = gameOptions.getPerspective();
        if (originalPerspective == Perspective.THIRD_PERSON_BACK && DynamicCrosshair.config.isThirdPersonCrosshair()) {
            return Perspective.FIRST_PERSON;
        }
        return originalPerspective;
    }

    @Inject(method = "tick()V", at = @At(value = "TAIL"))
    private void dynamiccrosshair$tickDynamicCrosshair(CallbackInfo ci) {
        CrosshairHandler.tick();
    }

}
