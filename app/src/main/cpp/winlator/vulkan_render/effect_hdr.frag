#version 450

layout(binding = 0) uniform sampler2D screenTexture;
layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform PushConstants {
    vec2 resolution;
    float params[8];
} pc;

void main() {
    vec4 color = texture(screenTexture, vUV);
    float contrast = clamp(pc.params[0], 0.0, 2.0);
    color.rgb = (color.rgb - 0.5) * clamp(contrast + 1.0, 0.5, 2.0) + 0.5;
    fragColor = color;
}
