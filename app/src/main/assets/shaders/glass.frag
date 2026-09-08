#version 300 es
// Native transparent port of 3d7e98199b385358df6ddf65a9d20644754f22eb; see NOTICE.md.
precision highp float;

uniform vec2 uResolution;
uniform sampler2D uSceneTexture;

uniform vec2 uPanelSize;
uniform vec2 uCanvasSize;
uniform vec2 uPanelOrigin;
uniform float uMarginPx;
uniform float uCornerRadius;

uniform float uHeight;
uniform float uCurvature;
uniform float uRefractAmount;
uniform float uAngle;
uniform float uGradRadialMix;

uniform float uKeyAngle;
uniform float uFillAngle;
uniform float uHlHeight;
uniform float uHlCut;
uniform float uHlNorm;
uniform float uHlAmount;
uniform float uHlCurv;

uniform float uShadowAmount;
uniform float uCausticAmount;
uniform float uShadowOffsetY;
uniform float uCausticOffsetY;
uniform float uProjectionSoftness;

uniform float uGlassVisibility;
uniform float uCollapsed;
uniform float uCapsuleOutline;
out vec4 outColor;

float saturate(float x) {
	return clamp(x, 0.0, 1.0);
}

vec2 rotate2d(vec2 v, float a) {
	float c = cos(a);
	float s = sin(a);
	return vec2(v.x * c - v.y * s, v.x * s + v.y * c);
}

float supercircleDistance(vec2 p, vec2 b, float n, vec2 param) {
	const float c = 1.528665;
	float an = abs(n);
	float ac = an * c;
	float m10 = mix(ac, an, max(param.x, param.y));
	vec2 v14 = (p - b) + vec2(m10);
	vec2 q = abs(max(vec2(0.0), (p - b) / max(ac, 0.0001) + vec2(1.0)));
	float l = length(q);
	float qmax = max(q.x, q.y);
	float qmin = min(q.x, q.y);
	float ratio = (qmax == 0.0) ? 0.0 : saturate(qmin / qmax);
	float poly = ((((-0.926054 * ratio + 3.15601) * ratio - 3.64122) * ratio + 1.26803) * ratio + 0.268531);
	float dCorner = (l + 1.0) - 1.0 / (1.0 - ratio * ratio * saturate(l) * poly);
	float dFar = length(max(vec2(0.0), q * c - vec2(0.528665))) * 0.654166 + 0.345834;
	float d57 = mix(dCorner, dFar, param.x);
	float d58 = mix(dCorner, dFar, param.y);
	float s = (q.y > q.x) ? 1.0 : -1.0;
	float t65 = saturate((0.5 - s) + s * ratio);
	float dist = mix(d57, d58, t65) - 1.0;
	float emin = min(max(v14.x, v14.y), 0.0);
	return emin + ac * dist;
}

vec2 cornerParam(vec2 halfSize, float r) {
	if (r < 0.0001) return vec2(0.0);
	return clamp((vec2(1.528665) - halfSize / r) / 0.528665, vec2(0.0), vec2(1.0));
}

float shapeDistance(vec2 p, vec2 halfSize, float cornerRadius) {
	float r = min(cornerRadius, min(halfSize.x, halfSize.y));
	if (r < 0.5) {
		vec2 dd = abs(p) - halfSize;
		return length(max(dd, vec2(0.0))) + min(max(dd.x, dd.y), 0.0);
	}
	return supercircleDistance(abs(p), halfSize, r, cornerParam(halfSize, r));
}

// The collapsed capsule is intentionally a hard, opaque ink shape. Keep its
// mask independent from the superellipse approximation used by the glass orb;
// this prevents a translucent/stale GL edge from leaking the app underneath.
float roundedRectDistance(vec2 p, vec2 halfSize, float radius) {

    float r = min(radius, min(halfSize.x, halfSize.y));
    vec2 q = abs(p) - (halfSize - vec2(r));
    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

vec2 shapeGradient(vec2 p, vec2 halfSize, float cornerRadius, float radialMix) {
	float r = min(cornerRadius, min(halfSize.x, halfSize.y));
	vec2 param = cornerParam(halfSize, r);
	float ac = mix(r * 1.528665, r, max(param.x, param.y));
	vec2 pf = abs(p);
	vec2 v = max(vec2(0.0), (pf - halfSize) + vec2(ac));
	vec2 g = (v.x + v.y > 0.00001)
		? normalize(v)
		: ((pf.x - halfSize.x > pf.y - halfSize.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0));
	vec2 cornerGrad = g * sign(p);
	vec2 centerRadial = normalize(vec2(p.x, halfSize.x * p.y / max(halfSize.y, 0.001)) + vec2(0.00001));
	return normalize(mix(cornerGrad, centerRadial, radialMix));
}

float refractionProfile(float t, float curvature) {
	float flatProfile = 1.0 - 0.2929 * (t < 1.0 ? 1.0 : 0.0);
	float circular = sqrt(max(1.0 - (1.0 - t) * (1.0 - t), 0.0));
	return mix(flatProfile, circular, curvature);
}

vec2 refractedUv(vec2 baseUv, float d, vec2 grad) {
	float t = clamp(-d / max(uHeight, 0.001), 0.0, 1.0);
	float mag = 1.0 - refractionProfile(t, uCurvature);
	vec2 dir = rotate2d(grad, uAngle);
	return baseUv + (uRefractAmount * mag * dir) / uCanvasSize;
}

float highlightLobe(float dist, float aa, vec2 n, float h, vec2 dir, float cut, float curv) {
	if (dist < -5.0) return 0.0;
	float t = saturate(dist / max(h, 0.001));
	float profile = mix(t < 1.0 ? 1.0 : 0.0, 1.0 - t, curv);
	float band = saturate(dist / aa + 0.5) * saturate((h - dist) / aa + 0.5) * profile;
	float angular = saturate((dot(dir, n) - cut) / max(1.0 - cut, 0.001));
	return band * angular;
}

float highlightBand(float d, vec2 grad) {
	float glen = max(length(grad), 0.0001);
	float dist = -d / glen;
	vec2 n = grad / glen;
	float aa = max(max(fwidth(dist), 0.0001), uProjectionSoftness * 0.32);
	vec2 kdir = vec2(cos(uKeyAngle), sin(uKeyAngle));
	vec2 fdir = vec2(cos(uFillAngle), sin(uFillAngle));
	float key = highlightLobe(dist, aa, n, uHlHeight, kdir, uHlCut, uHlCurv);
	float fill = highlightLobe(dist, aa, n, uHlHeight, fdir, uHlCut, uHlCurv);
	float keyN = key / (1.0 + (1.0 - key) * uHlNorm);
	float fillN = fill / (1.0 + (1.0 - fill) * uHlNorm);
	return keyN + fillN;
}

vec4 outsideProjection(vec2 p, vec2 halfSize, float cornerRadius) {
	float r = max(min(halfSize.x, halfSize.y), 1.0);
	float aspect = max(halfSize.x / max(halfSize.y, 1.0), 1.0);
	vec2 shadowP = p - vec2(0.0, uShadowOffsetY * r);
	float shadowD = shapeDistance(shadowP, halfSize, cornerRadius);
	float seamSoftness = max(uProjectionSoftness, 1.0);
	float shadowOutside = smoothstep(-seamSoftness, 14.0 + seamSoftness, shadowD);
	float shadowBlur = exp(-max(shadowD, 0.0) * max(shadowD, 0.0) / max(r * r * 0.78, 1.0));
	vec2 sn = shadowP / max(halfSize, vec2(1.0));
	float topBias = smoothstep(0.45, -0.85, sn.y);
	float sideBias = smoothstep(0.18, 1.0, abs(sn.x));
	float shadow = shadowOutside * shadowBlur * (0.24 + 0.14 * topBias + 0.05 * sideBias);

	float morphStretch = min(sqrt(aspect), 1.85);
	vec2 c = vec2(0.0, halfSize.y + r * (0.34 + uCausticOffsetY));
	vec2 q = vec2((p.x - c.x) / max(r * 0.92 * morphStretch, 1.0), (p.y - c.y) / max(r * 0.82, 1.0));
	float caustic = exp(-(q.x * q.x * 1.65 + q.y * q.y * 1.95));
	float core = exp(-(q.x * q.x * 5.0 + q.y * q.y * 5.5));
	vec2 n = p / max(halfSize, vec2(1.0));
	float seam = seamSoftness / r;
	float under = smoothstep(0.02 - seam * 0.7, 0.9 + seam * 0.9, n.y);
	vec3 glow = vec3(1.0, 0.82, 0.46) * caustic + vec3(0.78, 0.9, 1.0) * core * 0.34;
	vec3 light = clamp(glow * under * uCausticAmount, 0.0, 1.0);
    float lightAlpha = max(light.r, max(light.g, light.b));
    float shadowAlpha = saturate(shadow * uShadowAmount);
    return vec4(light, lightAlpha + shadowAlpha * (1.0 - lightAlpha));
}


void main() {
    vec2 pixel = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);
    vec2 center = uPanelOrigin + uPanelSize * 0.5;
    vec2 halfSize = uPanelSize * 0.5 - vec2(uMarginPx);
    vec2 p = pixel - center;
    float d = shapeDistance(p, halfSize, uCornerRadius);
    float shapeAlpha = 1.0 - smoothstep(-1.0, 1.0, d);
    // A collapsed Dynamic Island is ink black: no wave, tint or projection.
    // The optional rim is the short-lived dark-theme launch outline only.
    if (uCollapsed > 0.5 || uGlassVisibility <= 0.0001) {
        float capsuleD = roundedRectDistance(p, halfSize, uCornerRadius);
        float capsuleAlpha = 1.0 - smoothstep(-1.0, 1.0, capsuleD);
        float rim = (1.0 - smoothstep(0.0, 2.0, abs(capsuleD))) * uCapsuleOutline * 0.42;
        float alpha = max(capsuleAlpha, rim);
        // Premultiplied output: the capsule interior is always RGB=0, A=1.
        outColor = vec4(vec3(rim) * alpha, alpha);
		return;
    }
    vec2 grad = shapeGradient(p, halfSize, uCornerRadius, uGradRadialMix);
    vec2 uv = clamp(refractedUv(pixel / uCanvasSize, d, grad), 0.0, 1.0);
    // Only the generated scene is warped. No screen capture/background texture.
    vec4 scene = texture(uSceneTexture, vec2(uv.x, 1.0 - uv.y));
    float highlight = saturate(highlightBand(d, grad) * uHlAmount);
    vec3 glassRgb = clamp(scene.rgb + vec3(highlight), 0.0, 1.0);
    float glassAlpha = max(scene.a, max(glassRgb.r, max(glassRgb.g, glassRgb.b)));
    // Keep the morphing body optically dense until it is almost a sphere. Letting
    // the alpha follow the early morph progress exposes a light wallpaper through
    // a half-formed pill and reads as a white flash. The settled glass (1.0) is unchanged.
    float transparencyProgress = smoothstep(0.55, 1.0, uGlassVisibility);
    vec4 inside = vec4(glassRgb * uGlassVisibility,
        mix(1.0, glassAlpha, transparencyProgress)) * shapeAlpha;
    vec4 projection = outsideProjection(p, halfSize, uCornerRadius);
    // Clip projection only at the shape, not by scene alpha: it never clouds the clear face.
    projection *= (1.0 - shapeAlpha) * uGlassVisibility;
    vec2 room = vec2(min(center.x, uResolution.x - center.x),
        p.y < 0.0 ? center.y : uResolution.y - center.y);
    float radialEdge = length(p / max(room, vec2(1.0)));
    projection *= 1.0 - smoothstep(0.45, 1.0, radialEdge);
    float edgePx = min(min(pixel.x, pixel.y), min(uResolution.x - pixel.x, uResolution.y - pixel.y));
    projection *= smoothstep(0.0, max(uProjectionSoftness * 2.0, 2.0), edgePx);
    // TextureView's translucent surface is composited as premultiplied RGBA.
    // Writing straight RGB with a low alpha leaks bright/dull pixels from the
    // generated scene and is the source of the intermittent milky capsule and
    // glass appearance on different compositors.
    vec4 composed = inside + projection;
    float composedAlpha = saturate(composed.a);
    outColor = vec4(clamp(composed.rgb, 0.0, 1.0) * composedAlpha, composedAlpha);
}
