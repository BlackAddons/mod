#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <blackaddons:sky_noise.glsl>
#moj_import <blackaddons:custom_sky.glsl>
#moj_import <blackaddons:landscape_hills.glsl>
#moj_import <blackaddons:synthwave.glsl>
#moj_import <blackaddons:galaxy.glsl>
#moj_import <blackaddons:landscape_sky.glsl>
#moj_import <blackaddons:synthwave_sky.glsl>
#moj_import <blackaddons:galaxy_sky.glsl>

in vec3 rayDir;

out vec4 fragColor;

void main() {
    fragColor = vec4(custom_sky(normalize(rayDir), gl_FragCoord.xy, FogColor.rgb), 1.0);
}
