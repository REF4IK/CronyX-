#version 450

layout(binding = 0) uniform sampler2D screenTexture;
layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform PushConstants {
    vec2 resolution;
    float params[8];
} pc;

void main() {
    float pixelSize = clamp(pc.params[0], 1.0, 50.0);
    vec2 pixelatedUV = floor(vUV * pc.resolution / pixelSize) * pixelSize / pc.resolution;
    vec4 texelColor = texture(screenTexture, pixelatedUV);
    fragColor = texelColor;
}
