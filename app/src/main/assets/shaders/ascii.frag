precision highp float;
uniform sampler2D uSource;
uniform sampler2D uAtlas;
uniform vec2 uGrid;
uniform float uGlyphCount;
uniform float uContrast;
uniform float uBrightness;
uniform float uGamma;
uniform float uRenderMode;
uniform float uMatrixSize;
uniform float uTones;
uniform float uDitherAmount;
uniform float uInkIsDark;
uniform vec3 uLinearForeground;
uniform vec3 uLinearBackground;
uniform float uInvert;
uniform float uColorMode;
uniform vec3 uForeground;
uniform vec3 uBackground;
varying vec2 vUv;

vec3 toLinear(vec3 value) {
    return mix(value / 12.92, pow((value + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), value));
}
vec3 toSrgb(vec3 value) {
    value = clamp(value, 0.0, 1.0);
    return mix(value * 12.92, 1.055 * pow(value, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), value));
}
vec3 quantize(vec3 value, float threshold) {
    float steps = uTones - 1.0;
    return clamp(floor(value * steps + 0.5 + (0.5 - threshold) * uDitherAmount) / steps, 0.0, 1.0);
}

void main() {
    if (uRenderMode > 0.5) {
        // A top-left logical pixel grid matches the CPU renderer and does not vary with time.
        vec2 pixel = clamp(floor(vec2(vUv.x, 1.0 - vUv.y) * uGrid), vec2(0.0), uGrid - 1.0);
        vec2 sampleUv = vec2((pixel.x + 0.5) / uGrid.x, 1.0 - (pixel.y + 0.5) / uGrid.y);
        vec3 source = texture2D(uSource, sampleUv).rgb;
        source = clamp((source - 0.5) * uContrast + 0.5 + uBrightness, 0.0, 1.0);
        if (abs(uGamma - 1.0) > 0.00001) source = pow(source, vec3(1.0 / uGamma));
        source = toLinear(source);
        source = mix(source, 1.0 - source, uInvert);
        vec2 rankUv = (mod(pixel, uMatrixSize) + 0.5) / uMatrixSize;
        float rank = floor(texture2D(uAtlas, rankUv).r * 255.0 + 0.5);
        float threshold = (rank + 0.5) / (uMatrixSize * uMatrixSize);
        if (uColorMode > 0.5 && uColorMode < 1.5) {
            vec3 color = quantize(source, threshold);
            gl_FragColor = vec4(max(color.r, max(color.g, color.b)) < 0.00001 ? uBackground : toSrgb(color), 1.0);
        } else {
            float light = dot(source, vec3(0.2126, 0.7152, 0.0722));
            float coverage = mix(light, 1.0 - light, uInkIsDark);
            float level = quantize(vec3(coverage), threshold).r;
            gl_FragColor = vec4(toSrgb(mix(uLinearBackground, uLinearForeground, level)), 1.0);
        }
        return;
    }
    vec2 cell = floor(vUv * uGrid);
    vec3 source = texture2D(uSource, (cell + 0.5) / uGrid).rgb;
    float light = clamp((dot(source, vec3(0.2126, 0.7152, 0.0722)) - 0.5) * uContrast + 0.5 + uBrightness, 0.0, 1.0);
    if (abs(uGamma - 1.0) > 0.00001) light = pow(light, 1.0 / uGamma);
    float value = mix(light, 1.0 - light, uInvert);
    float glyph = floor(value * (uGlyphCount - 1.0) + 0.5);
    vec2 inside = fract(vUv * uGrid);
    // GL's video coordinates run bottom-up; Android's bitmap atlas runs top-down.
    vec2 atlasUv = vec2((glyph + inside.x) / uGlyphCount, 1.0 - inside.y);
    float mask = texture2D(uAtlas, atlasUv).a;
    vec3 ink = uForeground;
    if (uColorMode > 1.5) ink = mix(vec3(234.0,150.0,250.0) / 255.0, vec3(90.0,235.0,225.0) / 255.0, light);
    else if (uColorMode > 0.5) ink = source;
    gl_FragColor = vec4(mix(uBackground, ink, mask), 1.0);
}
