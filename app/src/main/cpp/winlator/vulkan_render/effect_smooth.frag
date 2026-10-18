#version 450

layout(binding = 0) uniform sampler2D screenTexture;
layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform PushConstants {
    vec2 resolution;
    float params[8];
} pc;

void main() {
    vec2 texelSize = vec2(1.0) / pc.resolution;
    float smoothness = clamp(pc.params[0], 0.0, 2.0);
    vec4 center     = texture(screenTexture, vUV);
    vec4 topLeft    = texture(screenTexture, vUV + vec2(-texelSize.x, -texelSize.y) * smoothness);
    vec4 topRight   = texture(screenTexture, vUV + vec2( texelSize.x, -texelSize.y) * smoothness);
    vec4 bottomLeft = texture(screenTexture, vUV + vec2(-texelSize.x,  texelSize.y) * smoothness);
    vec4 bottomRight= texture(screenTexture, vUV + vec2( texelSize.x,  texelSize.y) * smoothness);
    vec4 top    = texture(screenTexture, vUV + vec2(0.0, -texelSize.y) * smoothness);
    vec4 bottom = texture(screenTexture, vUV + vec2(0.0,  texelSize.y) * smoothness);
    vec4 left   = texture(screenTexture, vUV + vec2(-texelSize.x, 0.0) * smoothness);
    vec4 right  = texture(screenTexture, vUV + vec2( texelSize.x, 0.0) * smoothness);
    vec4 corners = (topLeft + topRight + bottomLeft + bottomRight) * 0.125;
    vec4 sides   = (top + bottom + left + right) * 0.125;
    vec4 smoothed = center * 0.5 + corners + sides;
    fragColor = smoothed;
}
