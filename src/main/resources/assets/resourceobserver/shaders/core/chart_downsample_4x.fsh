#version 150

uniform sampler2D Sampler0;
uniform vec2 InvSourceSize;
uniform float SampleScale;
uniform vec4 ResolveParams;

in vec2 fragPos;

out vec4 fragColor;

void main() {
    float scale = max(SampleScale, 1.0);
    vec2 base = fragPos * scale - vec2((scale - 1.0) * 0.5);
    vec4 accum = vec4(0.0);
    float weightSum = 0.0;
    int scaleInt = int(floor(scale + 0.5));
    for (int y = 0; y < 32; y++) {
        if (y >= scaleInt) {
            continue;
        }
        for (int x = 0; x < 32; x++) {
            if (x >= scaleInt) {
                continue;
            }
            vec2 samplePos = base + vec2(x, y);
            vec2 uv = vec2(samplePos.x * InvSourceSize.x, 1.0 - samplePos.y * InvSourceSize.y);
            float wx = (x == 0 || x == scaleInt - 1) ? ResolveParams.x : ResolveParams.y;
            float wy = (y == 0 || y == scaleInt - 1) ? ResolveParams.x : ResolveParams.y;
            float weight = wx * wy;
            accum += texture(Sampler0, uv) * weight;
            weightSum += weight;
        }
    }

    vec4 premult = accum / max(weightSum, 1.0e-5);
    if (premult.a <= 1.0e-5) {
        fragColor = vec4(0.0);
        return;
    }

    float alpha = clamp(premult.a + ResolveParams.z * (premult.a - premult.a * premult.a), 0.0, 1.0);
    fragColor = vec4(premult.rgb / premult.a, alpha);
}
