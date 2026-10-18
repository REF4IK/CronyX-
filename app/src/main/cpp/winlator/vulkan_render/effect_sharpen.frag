#version 450

layout(binding = 0) uniform sampler2D screenTexture;
layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform PushConstants {
    vec2 resolution;
    float params[8];
} pc;

void main() {
    vec2 texelSize = 1.0 / pc.resolution;
    vec4 color = texture(screenTexture, vUV);
    float intensity = clamp(pc.params[0], 0.0, 5.0);
    vec4 north = texture(screenTexture, vUV + vec2(0.0, texelSize.y));
    vec4 south = texture(screenTexture, vUV - vec2(0.0, texelSize.y));
    vec4 east  = texture(screenTexture, vUV + vec2(texelSize.x, 0.0));
    vec4 west  = texture(screenTexture, vUV - vec2(texelSize.x, 0.0));
    vec4 sharpened = color * (1.0 + 4.0 * intensity) - (north + south + east + west) * intensity;
    fragColor = sharpened;
}
