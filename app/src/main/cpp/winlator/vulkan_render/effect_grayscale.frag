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
    float intensity = clamp(pc.params[0], 0.0, 1.0);
    float gray = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 grayscale = vec3(gray);
    vec3 finalColor = mix(color, grayscale, intensity);
    fragColor = vec4(finalColor, texelColor.a);
}
