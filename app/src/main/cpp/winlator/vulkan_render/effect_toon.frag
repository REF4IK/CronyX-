#version 450

layout(binding = 0) uniform sampler2D screenTexture;
layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform PushConstants {
    vec2 resolution;
    float params[8];
} pc;

void main() {
    vec2 uv = gl_FragCoord.xy / pc.resolution;
    float edgeThreshold = 0.2;
    vec2 offset = vec2(1.0) / pc.resolution;
    vec3 colorCenter = texture(screenTexture, uv).rgb;
    vec3 colorLeft   = texture(screenTexture, uv - vec2(offset.x, 0.0)).rgb;
    vec3 colorRight  = texture(screenTexture, uv + vec2(offset.x, 0.0)).rgb;
    vec3 colorUp     = texture(screenTexture, uv - vec2(0.0, offset.y)).rgb;
    vec3 colorDown   = texture(screenTexture, uv + vec2(0.0, offset.y)).rgb;
    float diffHorizontal = length(colorRight - colorLeft);
    float diffVertical   = length(colorUp - colorDown);
    float edgeFactor = step(edgeThreshold, diffHorizontal + diffVertical);
    vec3 outlineColor = mix(colorCenter, vec3(0.0), edgeFactor);
    fragColor = vec4(outlineColor, 1.0);
}
