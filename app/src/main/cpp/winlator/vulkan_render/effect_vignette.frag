#version 450

layout(binding = 0) uniform sampler2D screenTexture;
layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform PushConstants {
    vec2 resolution;
    float params[8];
} pc;

void main() {
    vec4 texelColor = texture(screenTexture, vUV);
    vec2 coord = vUV - 0.5;
    float dist = length(coord * 2.0);
    float intensity = clamp(pc.params[0], 0.0, 1.0);
    float radius = clamp(pc.params[1], 0.0, 1.0);
    float vignette = smoothstep(radius, radius - intensity, dist);
    vec3 color = texelColor.rgb * vignette;
    fragColor = vec4(color, texelColor.a);
}
