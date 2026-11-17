#ifndef BLACKADDONS_GALAXY_SKY_GLSL
#define BLACKADDONS_GALAXY_SKY_GLSL
#ifdef SKY_GALAXY

uniform sampler2D SkyBake1;

const vec3 GALAXY_CENTER = vec3(0.0, 0.7512, 1.4127);
const vec3 GALAXY_NORMAL = vec3(-0.3971, 0.5914, -0.7018);
const vec3 GALAXY_U = vec3(-0.9063, -0.3731, 0.1984);
const vec3 GALAXY_V = vec3(-0.1445, 0.7149, 0.6842);
const float GALAXY_CORE = 0.125;
const float GALAXY_KNEE = 0.6;

vec3 custom_sky(vec3 rd, vec2 frag, vec3 horizon) {
    float t = SkyClock.x;
    vec3 dust = mix(SkyGlow.rgb, vec3(1.0, 0.9, 0.75), 0.5);

    float drift = t * 0.004;
    vec3 nd = vec3(cos(drift) * rd.x - sin(drift) * rd.z, rd.y, sin(drift) * rd.x + cos(drift) * rd.z);
    vec4 nebula = textureLod(SkyBake1, sky_sphere_uv(nd), 0.0);
    vec3 space = SkyGround.rgb * 0.6;
    space += mix(SkyGround.rgb, SkyMain.rgb, 0.6) * smoothstep(0.4, 0.8, nebula.r) * 0.6;
    space += SkyPlum.rgb * nebula.g * 0.9;
    space += SkyGlow.rgb * nebula.b * 0.5;
    float stars = sky_stars(rd, 350.0, 0.999) + 0.6 * sky_stars(rd, 700.0, 0.9994);
    space += vec3(0.9, 0.92, 1.0) * stars * (0.6 + nebula.a);
    vec3 col = space;
    vec3 galaxy = vec3(0.0);

    float facing = dot(rd, GALAXY_NORMAL);
    float along = dot(GALAXY_CENTER, GALAXY_NORMAL) / (abs(facing) > 1e-4 ? facing : 1e-4);
    if (along > 0.0) {
        vec3 local = rd * along - GALAXY_CENTER;
        float spin = t * 0.05;
        vec2 p = mat2(cos(spin), sin(spin), -sin(spin), cos(spin)) * vec2(dot(local, GALAXY_U), dot(local, GALAXY_V));
        if (max(abs(p.x), abs(p.y)) < GALAXY_EXTENT) {
            vec4 g = textureLod(SkyBake0, galaxy_disc_uv(p), 0.0);
            float tl = tanh(length(p));
            vec3 disc = mix(SkyMain.rgb, vec3(1.0), 0.35) * g.r;
            disc += vec3(g.g);
            disc += mix(SkyGlow.rgb, SkyMain.rgb, clamp(1.25 * tl, 0.0, 1.0)) * g.b * 1.5;
            disc += vec3(1.0, 0.92, 0.95) * g.a;
            disc += 0.7 * dust * (1.0 - exp(-2.0 * g.r / max(abs(facing), 0.05)));
            galaxy += disc;
        }
    }

    vec3 m = -GALAXY_CENTER;
    float b = dot(m, rd);
    float c = dot(m, m) - GALAXY_CORE * GALAXY_CORE;
    float h = b * b - c;
    if (h > 0.0 && b < 0.0) {
        float s = sqrt(h);
        float near = -b - s;
        float chord = min(max((along > 0.0 ? along : 1e9) - near, 0.0), 2.0 * s);
        galaxy += 1.7 * dust * (1.0 - exp(-chord));
    }
    float peak = max(max(galaxy.r, galaxy.g), galaxy.b);
    float rolled = GALAXY_KNEE + (1.0 - GALAXY_KNEE) * (1.0 - exp((GALAXY_KNEE - peak) / (1.0 - GALAXY_KNEE)));
    col += peak > GALAXY_KNEE ? galaxy * (rolled / peak) : galaxy;
    return sky_dither(col, frag);
}

#endif
#endif
