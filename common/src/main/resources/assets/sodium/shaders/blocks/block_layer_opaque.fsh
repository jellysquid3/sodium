#version 330 core

#import <sodium:include/fog.glsl>

in vec4 v_Color; // The interpolated vertex color
in vec2 v_TexCoord;
flat in vec2 v_TexOrigin;

in float v_FragDistance; // The fragment's distance from the camera

uniform sampler2D u_BlockTex; // The block texture

uniform vec4 u_FogColor; // The color of the shader fog
uniform float u_FogStart; // The starting position of the shader fog
uniform float u_FogEnd; // The ending position of the shader fog

out vec4 fragColor; // The output fragment for the color framebuffer

vec4 sample(sampler2D tex, vec2 texCoords, vec2 spriteOrigin, vec2 spriteSize, float bias) {
    vec2 uv = spriteOrigin + (clamp(texCoords, vec2(1.0 / 256.0), vec2(255.0 / 256.0)) * spriteSize);
    uv = uv / textureSize(tex, 0);

    return texture(tex, uv, bias);
}

vec4 sample4x(sampler2D tex, vec2 texCoords, vec2 spriteOrigin, vec2 spriteSize, float bias) {
    vec2 dx = dFdx(texCoords);
    vec2 dy = dFdy(texCoords);

    vec4 acc = vec4(0.0);
    acc += sample(tex, texCoords + (0.125 * dx) + (0.375 * dy), spriteOrigin, spriteSize, bias) * 0.25;
    acc += sample(tex, texCoords - (0.125 * dx) - (0.375 * dy), spriteOrigin, spriteSize, bias) * 0.25;
    acc += sample(tex, texCoords + (0.375 * dx) - (0.125 * dy), spriteOrigin, spriteSize, bias) * 0.25;
    acc += sample(tex, texCoords - (0.375 * dx) + (0.125 * dy), spriteOrigin, spriteSize, bias) * 0.25;

    return acc;
}

void main() {
    vec4 diffuseColor = sample4x(u_BlockTex, v_TexCoord, v_TexOrigin, vec2(16.0), -2.0);

    // Apply per-vertex color
    diffuseColor *= v_Color;

#ifdef USE_FRAGMENT_DISCARD
    if (diffuseColor.a < 0.5) {
        discard;
    }
#endif

    fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
}