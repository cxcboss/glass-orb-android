// Reproducible, narrow source port. Does not modify or vendor the reference repository.
// Source-Available Non-Commercial Study License; see ../NOTICE.md and ../LICENSE.
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';

const reference = resolve(process.argv[2] || '/tmp/glass-orb-study.NE1O6Z');
const revision = execFileSync('git', ['-C', reference, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
if (revision !== '3d7e98199b385358df6ddf65a9d20644754f22eb') throw new Error('Unexpected reference revision');
const source = name => readFileSync(resolve(reference, 'src/shaders', name + '.frag.glsl'), 'utf8');
const constants = {
  uMouse: 'vec4(0.0)', uResolved: '1.0', uUnresolvedScale: '0.14', uEffectScale: '1.0',
  uAnchor: 'vec2(0.5)', uFreq: '1.1', uAberrationFreq: '1.0', uWaveSpeed: '-1.0',
  uFalloff: '1.7', uEdgeMask: '0.4', uEdgeMaskInset: '0.0', uLowAmplitude: '6.0',
  uLowIntensity: '1.5', uMidAberration: '0.8', uMidAberrationAmplitude: '0.05',
  uMidBandFill: '0.0', uMidSoftness: '0.4', uHighAberration: '0.5', uHighAberrationAmplitude: '0.06',
  uDotsResolved: '1.0', uPairOffset: '0.085', uPairSmoothness: '0.2', uSmoothness: '0.2',
  uProgress0: '0.0', uProgress1: '0.0', uProgress2: '0.0', uProgress3: '0.0', uProgress4: '0.0', uProgress5: '0.0',
  uScaleDuration: '2.0', uScaleStagger: '0.167', uScaleMin: '0.001', uScaleMax: '0.65',
  uFalloffPower: '0.7', uGlowFadeStart: '0.0', uGlowFadeEnd: '0.7', uDotsAberration: '-0.05',
  uCenterCore: '0.5', uDotsScale: '1.0', uAppear: '1.0',
};
const declarations = new Set();
function effectFunction(name) {
  let text = source(name).replace(/^#version.*\n/, '').replace('precision highp float;', '').replace('out vec4 outColor;', '');
  text = text.replace(/uniform (\w+) (\w+);/g, (_, type, key) => {
    declarations.add(constants[key] === undefined ? `uniform ${type} ${key};` : `const ${type} ${key} = ${constants[key]};`);
    return '';
  });
  if (name === 'dots') text = text.replace(/float saturate\(float value\) \{[\s\S]*?\n\}/, '').replace(/vec3 spectrumTri\(float t\) \{[\s\S]*?\n\}/, '');
  return text.replace('void main()', `vec4 render${name === 'wave' ? 'Wave' : 'Dots'}()`)
    .replace('outColor = vec4(col, alpha);', 'return vec4(col, alpha);').replace(/\n{3,}/g, '\n\n');
}
const wave = effectFunction('wave');
const dots = effectFunction('dots');
const effect = `#version 300 es
// Reference wave/dots arithmetic ported from ${revision}; see NOTICE.md.
precision highp float;
${[...declarations].join('\n')}
uniform float uDotsOpacity;
uniform float uHueShift;
out vec4 outColor;
${wave}
${dots}
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
`;

// Keep the original SDF, gradient, lens profile and highlight equations verbatim.
let glass = source('glass');
glass = glass.slice(0, glass.indexOf('vec4 glassFragment'));
glass = glass.replace(/uniform sampler2D uBackground;\n/, '')
  .replace(/uniform vec2 uTextureSize;\n/, '').replace(/uniform float uBackgroundReady;\n/, '')
  .replace(/vec2 coverUv[\s\S]*?(?=float supercircleDistance)/, '');
glass = glass.replace('vec3 outsideProjection', 'vec4 outsideProjection')
  .replace('return glow * under * uCausticAmount - vec3(shadow * uShadowAmount);', `vec3 light = clamp(glow * under * uCausticAmount, 0.0, 1.0);
    float lightAlpha = max(light.r, max(light.g, light.b));
    float shadowAlpha = saturate(shadow * uShadowAmount);
    return vec4(light, lightAlpha + shadowAlpha * (1.0 - lightAlpha));`);
glass = glass.replace('out vec4 outColor;', `uniform float uGlassVisibility;
out vec4 outColor;`);
glass += `
void main() {
    vec2 pixel = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);
    vec2 center = uPanelOrigin + uPanelSize * 0.5;
    vec2 halfSize = uPanelSize * 0.5 - vec2(uMarginPx);
    vec2 p = pixel - center;
    float d = shapeDistance(p, halfSize, uCornerRadius);
    float shapeAlpha = 1.0 - smoothstep(-1.0, 1.0, d);
    // A collapsed Dynamic Island is ink black: no wave, tint, rim or projection.
    if (uGlassVisibility <= 0.0001) {
        outColor = vec4(0.0, 0.0, 0.0, shapeAlpha);
        return;
    }
    vec2 grad = shapeGradient(p, halfSize, uCornerRadius, uGradRadialMix);
    vec2 uv = clamp(refractedUv(pixel / uCanvasSize, d, grad), 0.0, 1.0);
    // Only the generated scene is warped. No screen capture/background texture.
    vec4 scene = texture(uSceneTexture, vec2(uv.x, 1.0 - uv.y));
    float highlight = saturate(highlightBand(d, grad) * uHlAmount);
    vec3 glassRgb = clamp(scene.rgb + vec3(highlight), 0.0, 1.0);
    float glassAlpha = max(scene.a, max(glassRgb.r, max(glassRgb.g, glassRgb.b)));
    vec4 inside = vec4(glassRgb * uGlassVisibility,
        mix(1.0, glassAlpha, uGlassVisibility)) * shapeAlpha;
    vec4 projection = outsideProjection(p, halfSize, uCornerRadius);
    // Clip projection only at the shape, not by scene alpha: it never clouds the clear face.
    projection *= (1.0 - shapeAlpha) * uGlassVisibility;
    vec2 room = vec2(min(center.x, uResolution.x - center.x),
        p.y < 0.0 ? center.y : uResolution.y - center.y);
    float radialEdge = length(p / max(room, vec2(1.0)));
    projection *= 1.0 - smoothstep(0.45, 1.0, radialEdge);
    float edgePx = min(min(pixel.x, pixel.y), min(uResolution.x - pixel.x, uResolution.y - pixel.y));
    projection *= smoothstep(0.0, max(uProjectionSoftness * 2.0, 2.0), edgePx);
    outColor = inside + projection;
}
`;
glass = glass.replace('precision highp float;', `// Native transparent port of ${revision}; see NOTICE.md.
precision highp float;`);

function patchFile(path, content) {
  const old = readFileSync(path, 'utf8');
  const patch = '*** Begin Patch\n*** Update File: ' + path + '\n@@\n'
    + old.trimEnd().split('\n').map(line => '-' + line).join('\n') + '\n'
    + content.trimEnd().split('\n').map(line => '+' + line).join('\n') + '\n*** End Patch';
  execFileSync('apply_patch', [patch], { stdio: 'inherit' });
}
patchFile('app/src/main/assets/shaders/effect.frag', effect);
patchFile('app/src/main/assets/shaders/glass.frag', glass);
