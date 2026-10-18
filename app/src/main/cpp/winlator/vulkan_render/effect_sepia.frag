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
    float sepiaR = (color.r * 0.393) + (color.g * 0.769) + (color.b * 0.189);
    float sepiaG = (color.r * 0.349) + (color.g * 0.686) + (color.b * 0.168);
    float sepiaB = (color.r * 0.272) + (color.g * 0.534) + (color.b * 0.131);
    vec3 sepiaColor = vec3(sepiaR, sepiaG, sepiaB);
    vec3 finalColor = mix(color, sepiaColor, intensity);
    fragColor = vec4(finalColor, texelColor.a);
}
