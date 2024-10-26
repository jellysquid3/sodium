#version 150 core

uniform sampler2D mainColorRT;
uniform sampler2D entityColorRT;

uniform sampler2D vignetteTexture;
uniform vec4 vignetteColorModulator;

uniform int blendOptions;

in vec2 v_TexCoord;

out vec4 fragColor;

#define readRT(rt, coord) texelFetch(rt, coord, 0)
#define readTex(tex, coord) texture(tex, coord)
#define blend(dst, src) mix(dst, src, src.a)

// dst.rgb *= 1.0 - (src.rgb * src.a)
vec3 sampleVignette(vec2 texCoord) {
    vec4 color = readTex(vignetteTexture, texCoord) * vignetteColorModulator;
    color.rgb * color.a;

    return vec3(1.0) - color.rgb;
}

const int USE_ENTITY_GLOW = 1 << 0;
const int USE_VIGNETTE = 1 << 1;

void main() {
    ivec2 pixelCoord = ivec2(gl_FragCoord.xy);

    vec4 result = readRT(mainColorRT, pixelCoord);

    if ((blendOptions & USE_ENTITY_GLOW) != 0) {
        result = blend(result, readRT(entityColorRT, pixelCoord));
    }

    if ((blendOptions & USE_VIGNETTE) != 0) {
        result.rgb *= sampleVignette(v_TexCoord);
    }

    fragColor = result;
}