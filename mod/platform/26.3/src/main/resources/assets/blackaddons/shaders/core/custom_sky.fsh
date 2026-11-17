#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <blackaddons:sky_noise.glsl>
#include <blackaddons:custom_sky.glsl>
#include <blackaddons:landscape_hills.glsl>
#include <blackaddons:synthwave.glsl>
#include <blackaddons:galaxy.glsl>
#include <blackaddons:landscape_sky.glsl>
#include <blackaddons:synthwave_sky.glsl>
#include <blackaddons:galaxy_sky.glsl>

layout(location = 0) in vec3 rayDir;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(custom_sky(normalize(rayDir), gl_FragCoord.xy, FogColor.rgb), 1.0);
}
