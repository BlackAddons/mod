#ifndef BLACKADDONS_SKY_NOISE_GLSL
#define BLACKADDONS_SKY_NOISE_GLSL

const float SKY_PI = 3.14159265359;
const float SKY_TAU = 6.28318530718;

uint sky_mix(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352du;
    x ^= x >> 15;
    x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}

float sky_unit(uint h) {
    return float(h >> 8) * (1.0 / 16777216.0);
}

float sky_hash(vec2 p) {
    uvec2 q = uvec2(ivec2(floor(p)));
    return sky_unit(sky_mix(q.x + sky_mix(q.y + 0x9e3779b9u)));
}

vec2 sky_hash2(vec2 p) {
    uvec2 q = uvec2(ivec2(floor(p)));
    uint h = sky_mix(q.x + sky_mix(q.y + 0x9e3779b9u));
    return vec2(sky_unit(h), sky_unit(sky_mix(h)));
}

float sky_hash3(vec3 p) {
    uvec3 q = uvec3(ivec3(floor(p)));
    return sky_unit(sky_mix(q.x + sky_mix(q.y + sky_mix(q.z + 0x9e3779b9u))));
}

float sky_value_noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(sky_hash(i), sky_hash(i + vec2(1, 0)), u.x), mix(sky_hash(i + vec2(0, 1)), sky_hash(i + vec2(1, 1)), u.x), u.y);
}

float sky_value_noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float lo = mix(mix(sky_hash3(i), sky_hash3(i + vec3(1, 0, 0)), u.x), mix(sky_hash3(i + vec3(0, 1, 0)), sky_hash3(i + vec3(1, 1, 0)), u.x), u.y);
    float hi = mix(mix(sky_hash3(i + vec3(0, 0, 1)), sky_hash3(i + vec3(1, 0, 1)), u.x), mix(sky_hash3(i + vec3(0, 1, 1)), sky_hash3(i + vec3(1, 1, 1)), u.x), u.y);
    return mix(lo, hi, u.z);
}

float sky_fbm3(vec3 p) {
    const mat3 turn = mat3(0.444, 0.578, 0.685, -0.795, 0.607, 0.003, -0.414, -0.546, 0.729);
    float sum = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amplitude * sky_value_noise3(p);
        p = turn * p * 2.07 + 3.1;
        amplitude *= 0.5;
    }
    return sum / 0.96875;
}

vec2 sky_sphere_uv(vec3 rd) {
    return vec2(atan(rd.z, rd.x) / SKY_TAU + 0.5, asin(clamp(rd.y, -1.0, 1.0)) / SKY_PI + 0.5);
}

vec3 sky_sphere_dir(vec2 uv) {
    float azimuth = (uv.x - 0.5) * SKY_TAU;
    float elevation = (uv.y - 0.5) * SKY_PI;
    return vec3(cos(elevation) * cos(azimuth), sin(elevation), cos(elevation) * sin(azimuth));
}

#endif
