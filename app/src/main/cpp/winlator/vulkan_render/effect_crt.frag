#version 450

layout(binding = 0) uniform sampler2D screenTexture;
layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform PushConstants {
    vec2 resolution;
    float params[8];
} pc;

#define CA_AMOUNT 1.0025
#define SCANLINE_INTENSITY_X 0.125
#define SCANLINE_INTENSITY_Y 0.375
#define SCANLINE_SIZE 1024.0

void main() {
    vec4 finalColor = texture(screenTexture, vUV);
    finalColor.rgb = vec3(
        texture(screenTexture, (vUV - 0.5) * CA_AMOUNT + 0.5).r,
        finalColor.g,
        texture(screenTexture, (vUV - 0.5) / CA_AMOUNT + 0.5).b
    );
    float scanlineX = abs(sin(vUV.x * SCANLINE_SIZE) * 0.5 * SCANLINE_INTENSITY_X);
    float scanlineY = abs(sin(vUV.y * SCANLINE_SIZE) * 0.5 * SCANLINE_INTENSITY_Y);
    fragColor = vec4(mix(finalColor.rgb, vec3(0.0), scanlineX + scanlineY), finalColor.a);
}
