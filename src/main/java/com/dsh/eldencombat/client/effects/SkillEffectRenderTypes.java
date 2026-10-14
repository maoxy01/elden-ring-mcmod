package com.dsh.eldencombat.client.effects;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;

/** Untextured fullbright mesh with normal transparency, depth testing and no depth writes. */
final class SkillEffectRenderTypes extends RenderType {
    static final RenderType SLASH_SPRITE = create("eldencombat:slash_sprite", DefaultVertexFormat.PARTICLE,
            VertexFormat.Mode.QUADS, 4096, false, true,
            CompositeState.builder().setShaderState(new ShaderStateShard(GameRenderer::getParticleShader))
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_PARTICLES, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setLightmapState(LIGHTMAP)
                    .setOutputState(ITEM_ENTITY_TARGET).createCompositeState(false));
    static final RenderType MESH = create("eldencombat:skill_mesh", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, true,
            CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setOutputState(ITEM_ENTITY_TARGET).createCompositeState(false));

    private SkillEffectRenderTypes() { super(null, null, null, -1, false, false, null, null); }
}
