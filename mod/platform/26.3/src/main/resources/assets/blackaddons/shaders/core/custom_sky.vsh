#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) out vec3 rayDir;

void main() {
    vec2 ndc = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2) * 2.0 - 1.0;
    mat4 unproject = inverse(ProjMat);
    vec4 a = unproject * vec4(ndc, 0.5, 1.0);
    vec4 b = unproject * vec4(ndc, 0.75, 1.0);
    vec3 ray = b.xyz / b.w - a.xyz / a.w;
    rayDir = transpose(mat3(ModelViewMat)) * (ray.z > 0.0 ? -ray : ray);
    gl_Position = vec4(ndc, 0.0, 1.0);
}
