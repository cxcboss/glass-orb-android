#version 300 es
precision highp float;

uniform sampler2D uEffectTexture;
uniform vec2 uResolution;
uniform vec2 uEffectOrigin;
uniform vec2 uEffectSize;
uniform float uContainerStrength;
uniform float uContainerBlack;
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
    float t = clamp((yFromTop - uContainerBlack) / fadeSpan, 0.0, 1.0);
    float verticalFade = yFromTop <= uContainerBlack ? 1.0 : exp(-uContainerGauss * t * t);
    float edge = smoothstep(0.0, 0.14, min(effectUv.x, 1.0 - effectUv.x));
    float containerAlpha = clamp(uContainerStrength, 0.0, 1.0) * verticalFade * edge;
    // The top band must be an opaque black continuation of the capsule. The
    // previous port only increased alpha, leaving the wave RGB in the scene
    // texture; the glass pass then exposed that RGB as a grey/coloured upper
    // half. Compose the black field into both RGB and alpha before sampling it
    // in the glass pass. Below the band the same gaussian mask preserves the
    // configured soft fade into the transparent scene.
    float topBlack = (yFromTop <= uContainerBlack) ? 1.0 : 0.0;
    float blackMask = max(containerAlpha, topBlack * edge);
    vec3 sceneRgb = effect.rgb * (1.0 - blackMask);
    float sceneAlpha = max(effect.a, blackMask);
    outColor = vec4(sceneRgb, sceneAlpha);
}
