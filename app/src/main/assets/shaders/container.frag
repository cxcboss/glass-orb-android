#version 300 es
precision highp float;

uniform sampler2D uEffectTexture;
uniform vec2 uResolution;
uniform vec2 uEffectOrigin;
uniform vec2 uEffectSize;
uniform float uContainerStrength;
uniform float uContainerFade;
uniform float uContainerGauss;

in vec2 vUv;
out vec4 outColor;

void main() {
    vec2 pixel = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);
    vec2 effectUv = (pixel - uEffectOrigin) / uEffectSize;
    if (any(lessThan(effectUv, vec2(0.0))) || any(greaterThan(effectUv, vec2(1.0)))) {
        outColor = vec4(0.0);
        return;
    }
    vec4 effect = texture(uEffectTexture, vec2(effectUv.x, 1.0 - effectUv.y));
    float yFromTop = effectUv.y;
    float fadeSpan = max(uContainerFade, 0.001);
    // Keep only a continuous dark-field fade. There is deliberately no
    // hard-coded top band or opaque "pure black" area inside the orb.
    float t = clamp(yFromTop / fadeSpan, 0.0, 1.0);
    float verticalFade = exp(-uContainerGauss * t * t);
    float edge = smoothstep(0.0, 0.14, min(effectUv.x, 1.0 - effectUv.x));
    float containerAlpha = clamp(uContainerStrength, 0.0, 1.0) * verticalFade * edge;
    float inverseEffectAlpha = 1.0 - clamp(effect.a, 0.0, 1.0);
    // Keep the generated effect RGB intact and put the dark field behind it
    // through alpha (effect OVER container). This preserves the smooth upper
    // shading without introducing a black rectangle or a black cap in the orb.
    float sceneAlpha = clamp(effect.a + containerAlpha * inverseEffectAlpha, 0.0, 1.0);
    outColor = vec4(effect.rgb, sceneAlpha);
}
