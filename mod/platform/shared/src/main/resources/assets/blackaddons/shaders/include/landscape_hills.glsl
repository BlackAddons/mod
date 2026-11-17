#ifndef BLACKADDONS_LANDSCAPE_HILLS_GLSL
#define BLACKADDONS_LANDSCAPE_HILLS_GLSL

const float HILL_FAR = 25.0;
const vec3 HILL_EYE = vec3(0.0, 0.15, 0.0);
const float HILL_LOW = -0.703;

float hill_terrain(vec2 p) {
    const mat2 turn = mat2(1.262, 1.590, -1.590, 1.262);
    float h = 0.0;
    float a = 1.0;
    for (int i = 0; i < 4; i++) {
        h += a * sky_value_noise(p);
        a *= 0.5;
        p = turn * p;
    }
    return h;
}

float hill_map(vec3 p) {
    return (p.y + hill_terrain(p.xz) * 0.375) * 0.5;
}

vec2 hill_uv(vec3 rd) {
    return vec2(atan(rd.z, rd.x) / SKY_TAU + 0.5, sqrt(clamp(-rd.y, 0.0, 1.0)));
}

vec3 hill_dir(vec2 uv) {
    float y = -uv.y * uv.y;
    float azimuth = (uv.x - 0.5) * SKY_TAU;
    float r = sqrt(max(1.0 - y * y, 0.0));
    return vec3(cos(azimuth) * r, y, sin(azimuth) * r);
}

vec2 hill_hit(vec3 rd) {
    if (rd.y >= 0.0) return vec2(0.0);
    float dist = 0.0;
    for (int i = 0; i < 256; i++) {
        vec3 p = HILL_EYE + dist * rd;
        float d = hill_map(p);
        if (d < 0.003 * dist) return vec2(exp(-0.12 * dist), clamp(1.0 - p.y / HILL_LOW, 0.0, 1.0));
        dist += d;
        if (dist >= HILL_FAR) break;
    }
    return vec2(0.0);
}

#ifdef LANDSCAPE_HILLS
vec4 sky_bake(vec2 uv) {
    return vec4(hill_hit(hill_dir(uv)), 0.0, 1.0);
}
#endif

#endif
