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
    vec3 color = texelColor.rgb;
    float brightness = pc.params[0];
    float contrast = pc.params[1];
    float gamma = pc.params[2];
    color = clamp(color + brightness, 0.0, 1.0);
    color = (color - 0.5) * clamp(contrast + 1.0, 0.5, 2.0) + 0.5;
    color = pow(color, vec3(1.0 / gamma));
    fragColor = vec4(color, texelColor.a);
}
