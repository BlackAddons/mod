#ifndef BLACKADDONS_SYNTHWAVE_GLSL
#define BLACKADDONS_SYNTHWAVE_GLSL

const float CANYON_FLOOR = -0.35;
const float CANYON_DEPTH = 12.0;
const float CANYON_RIB = 0.5;
const float CANYON_TEXEL = SKY_PI / 2048.0;

vec2 canyon_uv(vec3 rd) {
    vec3 m = vec3(rd.x, rd.y, abs(rd.z));
    return vec2(atan(m.x, m.z) / SKY_PI + 0.5, asin(clamp(m.y, -1.0, 1.0)) / SKY_PI + 0.5);
}

vec3 canyon_dir(vec2 uv) {
    float azimuth = (uv.x - 0.5) * SKY_PI;
    float elevation = (uv.y - 0.5) * SKY_PI;
    return vec3(cos(elevation) * sin(azimuth), sin(elevation), cos(elevation) * cos(azimuth));
}

float canyon_height(float x, float z) {
    float across = x - 0.3 * sin(0.27 * z + 0.8);
    float open = 1.05 + 0.3 * sin(0.45 * z + 1.3);
    float rise = smoothstep(open, open + 1.5, abs(across));
    vec2 p = vec2(across * 1.6, z * 0.8);
    float rock = sky_value_noise(p) + 0.4 * sky_value_noise(p * 2.4 + 7.3) + 0.16 * sky_value_noise(p * 5.1 + 2.9);
    return CANYON_FLOOR + rise * rock * 1.1;
}

vec4 synthwave_canyon(vec3 rd) {
    float dist = 0.05;
    float glow = 0.0;
    float glowZ = 0.0;
    float last = 1e9;
    float low = 1e9;
    float lowZ = 0.0;
    float lowWeight = 0.0;
    bool hit = false;
    vec3 p = vec3(0.0);
    for (int i = 0; i < 400; i++) {
        p = rd * dist;
        if (p.y < CANYON_FLOOR + 0.01 || p.z > CANYON_DEPTH || (rd.y > 0.0 && p.y > 1.8)) break;
        float height = canyon_height(p.x, p.z);
        float d = p.y - height;
        if (d < 0.0015 * dist) {
            hit = true;
            break;
        }
        float clearance = d / dist;
        if (clearance < last) {
            low = clearance;
            lowZ = p.z;
            lowWeight = smoothstep(0.0, 0.1, height - CANYON_FLOOR) * smoothstep(CANYON_DEPTH, CANYON_DEPTH - 2.0, p.z);
        } else if (low < 1e8) {
            float g = exp(-low / (2.5 * CANYON_TEXEL)) * lowWeight;
            glow += g;
            glowZ += g * lowZ;
            low = 1e9;
        }
        last = clearance;
        dist += max(d * 0.35, 0.002 + 0.002 * dist);
    }
    float lines = min(glow * 0.7, 1.0);
    if (!hit) return vec4(0.0, glow > 1e-4 ? glowZ / glow / CANYON_DEPTH : 0.0, 0.0, sqrt(lines));

    float fade = smoothstep(CANYON_DEPTH, CANYON_DEPTH - 2.0, p.z);
    float above = p.y - CANYON_FLOOR;
    /* Ribs fade out where they would crowd closer together than a few texels. */
    float spacing = CANYON_RIB * sqrt(max(1.0 - rd.z * rd.z, 0.0)) / dist;
    float rib = exp(-abs(fract(p.z / CANYON_RIB + 0.5) - 0.5) * CANYON_RIB * 70.0)
        * smoothstep(4.0 * CANYON_TEXEL, 12.0 * CANYON_TEXEL, spacing) * smoothstep(0.0, 0.15, above) * fade * 0.6;
    float lit = (0.1 + 0.35 * smoothstep(0.0, 1.4, above)) * fade;
    float z = (glowZ + p.z * (rib + 1e-4)) / (glow + rib + 1e-4);
    return vec4(sqrt(lit), z / CANYON_DEPTH, fade, sqrt(clamp(rib + lines, 0.0, 1.0)));
}

#ifdef SYNTHWAVE_CANYON
vec4 sky_bake(vec2 uv) {
    return synthwave_canyon(canyon_dir(uv));
}
#endif

#endif
