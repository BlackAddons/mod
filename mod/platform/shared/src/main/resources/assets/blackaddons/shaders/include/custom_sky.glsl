#ifndef BLACKADDONS_CUSTOM_SKY_GLSL
#define BLACKADDONS_CUSTOM_SKY_GLSL

layout(std140) uniform CustomSky {
    vec4 SkyGround;
    vec4 SkyMain;
    vec4 SkyPlum;
    vec4 SkyGlow;
    vec4 SkySun;
    vec4 SkyClock;
};

uniform sampler2D SkyBake0;

float sky_stars(vec3 rd, float density, float rarity) {
    vec3 p = rd * density;
    vec3 cell = floor(p);
    float h = sky_hash3(cell);
    float twinkle = 0.7 + 0.3 * sin(SkyClock.x * (1.0 + 2.0 * h) + h * 50.0);
    return step(rarity, h) * smoothstep(0.35, 0.0, length(fract(p) - 0.5)) * twinkle;
}

vec3 sky_meet_horizon(vec3 sky, vec3 rd, vec3 horizon) {
    return mix(horizon, sky, smoothstep(0.0, 0.12, abs(rd.y)));
}

vec3 sky_dither(vec3 color, vec2 frag) {
    uvec2 q = uvec2(frag);
    uint h = sky_mix(q.x + sky_mix(q.y + sky_mix(uint(SkyClock.x * 240.0))));
    return color + (sky_unit(h) + sky_unit(sky_mix(h)) - 1.0) / 255.0 * 1.5;
}

#endif
