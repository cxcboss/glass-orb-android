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
    float inverseEffectAlpha = 1.0 - effect.a;
    outColor = vec4(effect.rgb, effect.a + containerAlpha * inverseEffectAlpha);
}
