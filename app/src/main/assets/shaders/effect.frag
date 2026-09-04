#version 300 es
// Reference wave/dots arithmetic ported from 3d7e98199b385358df6ddf65a9d20644754f22eb; see NOTICE.md.
precision highp float;
uniform vec2 uResolution;
uniform float uTime;
const vec4 uMouse = vec4(0.0);
const float uResolved = 1.0;
uniform float uLayerOpacity;
const float uUnresolvedScale = 0.14;
const float uEffectScale = 1.0;
const vec2 uAnchor = vec2(0.5);
uniform float uAmplitude;
const float uFreq = 1.1;
const float uAberrationFreq = 1.0;
uniform float uWavePhase;
const float uWaveSpeed = -1.0;
uniform float uWaveScale;
uniform float uAberration;
uniform float uThickness;
uniform float uIntensity;
const float uFalloff = 1.7;
const float uEdgeMask = 0.4;
const float uEdgeMaskInset = 0.0;
uniform float uBandFill;
uniform float uBandFillThickness;
uniform float uSoftness;
uniform float uLow;
uniform float uMid;
uniform float uHigh;
const float uLowAmplitude = 6.0;
const float uLowIntensity = 1.5;
const float uMidAberration = 0.8;
const float uMidAberrationAmplitude = 0.05;
const float uMidBandFill = 0.0;
const float uMidSoftness = 0.4;
const float uHighAberration = 0.5;
const float uHighAberrationAmplitude = 0.06;
uniform float uWhiteClip;
const float uDotsResolved = 1.0;
uniform float uRotation;
uniform float uRingRadius;
uniform float uDotRadius;
const float uPairOffset = 0.085;
const float uPairSmoothness = 0.2;
const float uSmoothness = 0.2;
const float uProgress0 = 0.0;
const float uProgress1 = 0.0;
const float uProgress2 = 0.0;
const float uProgress3 = 0.0;
const float uProgress4 = 0.0;
const float uProgress5 = 0.0;
const float uScaleDuration = 2.0;
const float uScaleStagger = 0.167;
const float uScaleMin = 0.001;
const float uScaleMax = 0.65;
uniform float uGlowIntensity;
const float uFalloffPower = 0.7;
const float uGlowFadeStart = 0.0;
const float uGlowFadeEnd = 0.7;
const float uDotsAberration = -0.05;
const float uCenterCore = 0.5;
const float uDotsScale = 1.0;
const float uAppear = 1.0;
uniform float uDotsOpacity;
uniform float uHueShift;
out vec4 outColor;


float saturate(float value) {
	return clamp(value, 0.0, 1.0);
}

vec3 spectrumTri(float t) {
	return clamp(vec3(abs(t - 3.0) - 1.0, 2.0 - abs(t - 2.0), 2.0 - abs(t - 4.0)), 0.0, 1.0);
}

float smoothUnit(float value) {
	return value * value * (3.0 - 2.0 * value);
}

vec4 renderWave() {
	vec2 gid = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);
	float tw = mod(uWavePhase, 62.831848) * uWaveSpeed;
	float lo = saturate(uLow);
	float md = saturate(uMid);
	float hi = saturate(uHigh);
	float res = saturate(uResolved);

	float c52 = uThickness * 0.01;
	float c55 = (lo * uLowIntensity + uIntensity) * 0.01;
	float c58 = max(0.0, md * uMidSoftness + uSoftness);
	float c61 = (md * uMidBandFill + uBandFill) * 0.0001;
	float c64 = (uLowAmplitude * 0.01) * lo + uAmplitude;
	float c68 = c64 + md * uMidAberrationAmplitude + hi * uHighAberrationAmplitude;
	float c72 = (md * uMidAberration + uAberration) + hi * uHighAberration;
	float c73 = c72 * res;
	float c76 = lo * 14.0;
	float c75 = md * 10.0 + 4.0;
	float n77 = mix(0.1, c52, res);
	float n78 = mix(0.1, c55, res);
	float n80 = (res * 0.01) * c58;
	float n81 = mix(c75, 1.0, res);
	float omr = 1.0 - res;

	vec2 uv = (gid + 0.5) * 2.0 / uResolution - 1.0;
	float aspect = uResolution.x / uResolution.y;
	uv.x *= aspect;
	vec2 q = uv - vec2(aspect, 1.0) * (uAnchor * 2.0 - 1.0);
	float ws = max(uWaveScale * uEffectScale, 0.01);
	vec2 p = q / ws;
	float base = mix(0.14, uUnresolvedScale, res);
	float r = length(p);
	float edge = max(r - base, 0.0);
	float aC = max(aspect, 1.0);
	float px = p.x / aC;
	float cw = min(abs(px * 0.9), 1.0);
	float cw2 = pow(cos(cw * 1.5707964), 2.0);
	float eps = 0.0001;
	float atArg = atan(px * eps) * aC / eps;
	float waveBase = (cw2 * res * c68) * sin(atArg * uFreq + tw);
	float negBase = -c73;
	float atArg2 = atArg * uAberrationFreq + tw;
	float py = p.y;
	float n80sq = n80 * n80;
	float bft = max(uBandFillThickness, 0.0001);
	float n139 = (c61 * res) * n78;
	float env68 = cw2 * c68;
	vec2 mouseUv = uMouse.xy / max(uResolution, vec2(1.0));
	float mouseLift = uMouse.z * 0.035 * exp(-pow((mouseUv.x * 2.0 - 1.0) * 2.4, 2.0));

	vec3 colAcc = vec3(0.0);
	vec3 wSum = vec3(0.0);
	for (int i = 0; i < 4; i += 1) {
		float fi = float(i);
		float t13 = fi * 0.33333334;
		vec3 hue = mix(vec3(1.0), spectrumTri(fi), vec3(res));
		wSum += hue;
		float ph = atArg2 + mix(negBase, c73, t13);
		float w2 = env68 * sin(ph) + mouseLift;
		float dist = mix(edge, abs(py - w2), res);
		float rad = sqrt(dist * dist + n80sq) + n77;
		float k = dist * 0.02;
		float soft = mix(1.0 / (k * k + 1.0), 1.0, res);
		float glowL = (soft * n78) / rad;
		float band = max(0.0, max(py - max(waveBase, w2), min(waveBase, w2) - py));
		float fill = n139 / (band + bft);
		colAcc += (hue * n81) * (fill + glowL);
	}
	vec3 col = colAcc / max(wSum, vec3(0.0001));

	float tail = omr * (c76 + 4.0);
	float dC = mix(edge, abs(py - waveBase), res);
	float radC = dC + n77;
	float kC = dC * 0.02;
	float softC = mix(1.0 / (kC * kC + 1.0), 1.0, res);
	float cg = (n78 * 0.5 * (softC + tail)) / radC;
	vec3 cgl = pow(vec3(cg) + col, vec3(1.5));

	float ndcY = gid.y * 2.0 / uResolution.y - 1.0;
	float emC = max(clamp(uEdgeMask, 0.0, 1.0), 0.0001);
	float emMask = clamp((abs(ndcY) - 1.0 + clamp(uEdgeMaskInset, 0.0, 1.0)) / (-emC), 0.0, 1.0);
	emMask = smoothUnit(emMask);
	float fall = exp(-pow(px * uFalloff, 2.0));
	col = cgl * mix(1.0, emMask * fall, res) * res * saturate(uLayerOpacity);

	float m = max(max(col.r, col.g), col.b);
	vec3 huePreserved = col * ((m > 1.0) ? (1.0 / m) : 1.0);
	col = mix(huePreserved, min(col, vec3(1.0)), clamp(uWhiteClip, 0.0, 1.0));

	float alpha = saturate(max(max(col.r, col.g), col.b) * 1.15);
	return vec4(col, alpha);
}



float progressAt(int index) {
	if (index == 0) return uProgress0;
	if (index == 1) return uProgress1;
	if (index == 2) return uProgress2;
	if (index == 3) return uProgress3;
	if (index == 4) return uProgress4;
	return uProgress5;
}

float dotsField(
	vec2 P,
	vec2 aberOff,
	vec2 centersA[6],
	vec2 centersB[6],
	vec2 dirs[6],
	float radii[6],
	bool psOn,
	bool smOn,
	float pairSmooth,
	float smoothness,
	float pairK,
	float smK
) {
	float field = 1.0e9;
	for (int j = 0; j < 6; j += 1) {
		vec2 ofs = aberOff * dirs[j];
		float lenA = length(P + ofs - centersA[j]);
		float lenB = length(P + ofs - centersB[j]);
		float dA = lenA - radii[j];
		float dB = lenB - radii[j];
		float dPair = min(dA, dB);
		if (psOn) {
			float h = max(pairSmooth - abs(lenA - lenB), 0.0) / pairSmooth;
			dPair = min(dA, dB) - h * h * pairK;
		}
		if (smOn) {
			float h2 = max(smoothness - abs(field - dPair), 0.0) / smoothness;
			field = min(field, dPair) - h2 * h2 * smK;
		} else {
			field = min(field, dPair);
		}
	}
	return field;
}

vec4 renderDots() {
	vec2 gid = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);
	float mn = min(uResolution.x, uResolution.y);
	float halfMn = mn * 0.5;
	vec2 anchorC = uAnchor - 0.5;
	float aspect2 = uResolution.x / halfMn;
	vec2 anchorShift = vec2(aspect2, 2.0) * anchorC;
	float pr = max(uDotsScale * uEffectScale, 0.001);
	float drive = mod(uTime, 62.831848) * uRotation;
	float scaleDur = max(uScaleDuration, 0.001);
	float appear = saturate(uAppear) * saturate(uDotsResolved);
	float ringAmp = appear * uRingRadius;
	float pairAmp = appear * uPairOffset;

	vec2 centersA[6];
	vec2 centersB[6];
	vec2 dirs[6];
	float radii[6];

	for (int i = 0; i < 6; i += 1) {
		float fi = float(i);
		float angle = fi * 1.0471976 + drive;
		float ca = cos(angle);
		float sa = sin(angle);
		vec2 perp = vec2(-sa, ca);
		float fr = fract((fi * uScaleStagger + uTime) / scaleDur);
		float tri = 1.0 - abs(fr * 2.0 - 1.0);
		float x = saturate(tri);
		for (int k = 0; k < 8; k += 1) {
			float omx = 1.0 - x;
			float a3 = omx * 3.0;
			float c126 = (omx * 0.42) * a3;
			float x2 = x * x;
			float deriv = (x2 * 1.26) + (x * 0.96) * omx + c126;
			if (abs(deriv) < 0.000001) break;
			float num = ((x2 * 0.58) * a3 - tri) + (c126 + x2) * x;
			x = saturate(x - num / deriv);
		}
		float ss = x * x * (3.0 - 2.0 * x);
		float amp = mix(uScaleMin, uScaleMax, ss);
		vec2 dir = vec2(ca, sa);
		vec2 base = (ringAmp * dir) * (1.0 - 2.0 * progressAt(i));
		float ph2 = pairAmp * amp;
		centersA[i] = base - ph2 * perp;
		centersB[i] = base + ph2 * perp;
		dirs[i] = dir;
		radii[i] = amp * uDotRadius;
	}

	vec2 uvPix = (gid + 0.5 - 0.5 * uResolution) / halfMn;
	vec2 P = (uvPix - anchorShift) / pr;
	bool psOn = uPairSmoothness > 0.0001;
	bool smOn = uSmoothness > 0.0001;
	float fadeRange = max(uGlowFadeEnd - uGlowFadeStart, 0.0001);
	float aberStep = uDotsAberration * 0.0909090936;
	vec3 colAcc = vec3(0.0);
	vec3 wSum = vec3(0.0);

	for (int i = 0; i < 12; i += 1) {
		float ti = float(i) * 0.363636374;
		vec3 hue = spectrumTri(ti);
		vec2 aberOff = vec2(-(aberStep * float(i)));
		float field = dotsField(
			P,
			aberOff,
			centersA,
			centersB,
			dirs,
			radii,
			psOn,
			smOn,
			uPairSmoothness,
			uSmoothness,
			uPairSmoothness * 0.25,
			uSmoothness * 0.25
		);
		float fm = max(field, 0.0);
		float glow = saturate(uGlowIntensity / pow(fm + 0.0001, uFalloffPower));
		float fadeT = clamp((fm - uGlowFadeStart) / fadeRange, 0.0, 1.0);
		float fade = 1.0 - fadeT * fadeT * (3.0 - 2.0 * fadeT);
		colAcc += hue * (fade * glow);
		wSum += hue;
	}

	float cfield = dotsField(
		P,
		vec2(0.0),
		centersA,
		centersB,
		dirs,
		radii,
		psOn,
		smOn,
		uPairSmoothness,
		uSmoothness,
		uPairSmoothness * 0.25,
		uSmoothness * 0.25
	);
	vec3 col = colAcc / max(wSum, vec3(0.0001));
	float cfm = max(cfield, 0.0);
	float cglow = saturate(uGlowIntensity / pow(cfm + 0.0001, uFalloffPower));
	float cfadeT = clamp((cfm - uGlowFadeStart) / fadeRange, 0.0, 1.0);
	float cfade = 1.0 - cfadeT * cfadeT * (3.0 - 2.0 * cfadeT);
	vec2 mouseUv = uMouse.xy / max(uResolution, vec2(1.0));
	float mouseBoost = 1.0 + uMouse.z * 0.35 + uMouse.w * 0.2 + smoothstep(0.0, 0.16, 1.0 - distance(mouseUv, vec2(0.5))) * 0.05;

	col = (col + (cglow * uCenterCore) * cfade) * appear * mouseBoost;
	// hue-preserving clip guard: scale down only when over 1 (max→1), keeping the dot's hue.
	float m = max(max(col.r, col.g), col.b);
	col *= (m > 1.0) ? (1.0 / m) : 1.0;
	float alpha = saturate(max(max(col.r, col.g), col.b));
	return vec4(col, alpha);
}

void main() {
    vec4 wave = uLayerOpacity > 0.0001 ? renderWave() : vec4(0.0);
    vec4 dots = uDotsOpacity > 0.0001 ? renderDots() * uDotsOpacity : vec4(0.0);
    vec4 effect = wave + dots;
    // Optional setting; zero leaves the reference spectrum untouched.
    vec3 axis = normalize(vec3(1.0));
    float angle = radians(uHueShift);
    effect.rgb = clamp(effect.rgb * cos(angle) + cross(axis, effect.rgb) * sin(angle)
        + axis * dot(axis, effect.rgb) * (1.0 - cos(angle)), 0.0, 1.0);
    effect.a = max(clamp(effect.a, 0.0, 1.0), max(effect.r, max(effect.g, effect.b)));
    outColor = effect;
}
