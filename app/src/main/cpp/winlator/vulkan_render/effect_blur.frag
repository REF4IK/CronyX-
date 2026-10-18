#version 450

layout(binding = 0) uniform sampler2D screenTexture;
layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform PushConstants {
    vec2 resolution;
    float params[8];
} pc;

void main() {
    vec4 color = vec4(0.0);
    float total = 0.0;
    float intensity = clamp(pc.params[0], 0.0, 10.0);
    int samples = int(clamp(pc.params[1], 1.0, 20.0));
    float offset = intensity / pc.resolution.x;
    for (int i = 0; i < samples; i++) {
        float weight = 1.0 - abs(float(i) - float(samples - 1) / 2.0) / float(samples - 1);
        vec2 offsetCoord = vec2(offset * float(i - samples / 2), 0.0);
        color += texture(screenTexture, vUV + offsetCoord) * weight;
        total += weight;
    }
    vec4 vertColor = vec4(0.0);
    float vertTotal = 0.0;
    for (int j = 0; j < samples; j++) {
        float weight = 1.0 - abs(float(j) - float(samples - 1) / 2.0) / float(samples - 1);
        vec2 offsetCoord = vec2(0.0, offset * float(j - samples / 2));
        vertColor += texture(screenTexture, vUV + offsetCoord) * weight;
        vertTotal += weight;
    }
    vec4 finalColor = (color / total + vertColor / vertTotal) / 2.0;
    fragColor = vec4(finalColor.rgb, texture(screenTexture, vUV).a);
}
