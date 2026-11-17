#version 330
#extension GL_ARB_separate_shader_objects : require

#include <blackaddons:sky_noise.glsl>
#include <blackaddons:landscape_hills.glsl>
#include <blackaddons:synthwave.glsl>
#include <blackaddons:galaxy.glsl>

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = sky_bake(texCoord);
}
