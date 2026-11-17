#version 330

#moj_import <blackaddons:sky_noise.glsl>
#moj_import <blackaddons:landscape_hills.glsl>
#moj_import <blackaddons:synthwave.glsl>
#moj_import <blackaddons:galaxy.glsl>

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = sky_bake(texCoord);
}
