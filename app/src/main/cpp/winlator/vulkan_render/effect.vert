#version 450

layout(location = 0) out vec2 vUV;

void main() {
    // Generate fullscreen quad from vertex index (0..3)
    vec2 pos[4] = vec2[4](
        vec2(0.0, 0.0),
        vec2(1.0, 0.0),
        vec2(0.0, 1.0),
        vec2(1.0, 1.0)
    );
    vUV = pos[gl_VertexIndex];
    gl_Position = vec4(pos[gl_VertexIndex] * 2.0 - 1.0, 0.0, 1.0);
}
