#version 150

uniform vec4 LineStartEnd;
uniform vec4 LineMetrics;
uniform vec4 LineColor;

in vec2 fragPos;

out vec4 fragColor;

float segmentDistance(vec2 p, vec2 a, vec2 b) {
    vec2 ab = b - a;
    float denom = dot(ab, ab);
    float t = denom > 1.0e-6 ? clamp(dot(p - a, ab) / denom, 0.0, 1.0) : 0.0;
    vec2 nearest = a + ab * t;
    return length(p - nearest);
}

void main() {
    vec2 start = LineStartEnd.xy;
    vec2 end = LineStartEnd.zw;
    float coreHalf = LineMetrics.x;
    float glowHalf = LineMetrics.y;
    float feather = LineMetrics.z;
    float glowAlpha = LineMetrics.w;

    float dist = segmentDistance(fragPos, start, end);
    float core = 1.0 - smoothstep(coreHalf - feather, coreHalf + feather, dist);
    float glow = 1.0 - smoothstep(glowHalf - feather, glowHalf + feather, dist);
    float alpha = core * LineColor.a + max(glow - core, 0.0) * glowAlpha;
    fragColor = vec4(LineColor.rgb * alpha, alpha);
}
