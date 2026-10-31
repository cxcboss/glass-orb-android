#version 300 es
// Fixed-grid reduction of the already-rendered effect texture.
// The RGBA8 target stores weighted linear RGB in rgb and mean coverage in a.
// Luminance is recovered from the weighted RGB in glass.frag, avoiding a
// second texture or any CPU/GLES readback.
precision highp float;

uniform sampler2D uEffectTexture;
uniform vec2 uEffectResolution;
out vec4 outColor;

const int kPaletteGridSize = 8;
const float kPaletteSampleCount = 64.0;

void main() {
    vec3 weightedRgb = vec3(0.0);
    float totalCoverage = 0.0;
    vec2 sourceTexel = 0.5 / max(uEffectResolution, vec2(1.0));
    for (int y = 0; y < kPaletteGridSize; y += 1) {
        for (int x = 0; x < kPaletteGridSize; x += 1) {
            vec2 uv = (vec2(float(x), float(y)) + vec2(0.5)) / float(kPaletteGridSize);
            uv = clamp(uv, sourceTexel, vec2(1.0) - sourceTexel);
            vec4 effect = texture(uEffectTexture, uv);
            float coverage = clamp(effect.a, 0.0, 1.0);
            weightedRgb += clamp(effect.rgb, 0.0, 1.0) * coverage;
            totalCoverage += coverage;
        }
    }

    float safeWeight = max(totalCoverage, 0.0001);
    vec3 averageLinearRgb = weightedRgb / safeWeight;
    float meanCoverage = clamp(totalCoverage / kPaletteSampleCount, 0.0, 1.0);
    outColor = vec4(clamp(averageLinearRgb, 0.0, 1.0), meanCoverage);
}
