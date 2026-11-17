#ifndef BLACKADDONS_SYNTHWAVE_SKY_GLSL
#define BLACKADDONS_SYNTHWAVE_SKY_GLSL
#ifdef SKY_SYNTHWAVE

const float SYNTHWAVE_PACE = 0.3;

float synthwave_sun(vec2 p, float t) {
    float aa = fwidth(p.y) * 1.5;
    float disc = smoothstep(aa, -aa, length(p) - 1.0);
    float band = abs(fract(p.y * 4.5 + t * 0.25) - 0.5);
    float gap = clamp(-p.y * 0.45, 0.0, 0.45);
    float bandAa = aa * 4.5;
    float open = (1.0 - smoothstep(gap - bandAa, gap + bandAa, band)) * smoothstep(-0.05, -0.2, p.y);
    return disc * (1.0 - open);
}

vec3 synthwave_floor(vec3 rd, float t) {
    float below = max(-rd.y, 1e-3);
    vec2 q = rd.xz * (-CANYON_FLOOR / below) * 1.5;
    q.y -= t * SYNTHWAVE_PACE * 1.5;
    vec2 w = fwidth(q);
    vec2 toLine = 0.5 - abs(fract(q) - 0.5);
    vec2 line = 1.0 - smoothstep(w * 0.5, w * 1.5, toLine);
    float grid = max(line.x, line.y) * (1.0 - smoothstep(0.15, 0.6, max(w.x, w.y)));
    float halo = max(exp(-toLine.x * 9.0), exp(-toLine.y * 9.0)) * (1.0 - smoothstep(0.05, 0.3, max(w.x, w.y)));
    vec3 col = SkyGround.rgb * 0.8 + SkyGlow.rgb * (grid * 1.3 + halo * 0.15);
    float streak = exp(-abs(rd.x) / max(rd.z, 0.05) * 10.0) * smoothstep(0.0, 0.3, rd.z) * exp(-below * 8.0);
    return col + mix(SkyPlum.rgb, SkyGlow.rgb, 0.5) * streak * 0.5;
}

vec3 custom_sky(vec3 rd, vec2 frag, vec3 horizon) {
    float t = SkyClock.x;
    vec3 sky = mix(SkyPlum.rgb * 0.3, SkyGround.rgb, smoothstep(0.0, 0.7, rd.y));
    sky += vec3(0.92, 0.88, 1.0) * sky_stars(rd, 400.0, 0.9993) * smoothstep(0.15, 0.45, rd.y) * 0.8;

    float front = smoothstep(0.0, 0.25, rd.z);
    vec2 p = vec2(rd.x, rd.y - 0.18 * rd.z) / max(rd.z, 0.05) / 0.3;
    vec3 sunColor = mix(mix(SkyGlow.rgb, vec3(1.0), 0.45), SkyPlum.rgb * 1.3, clamp((0.7 - p.y) * 0.6, 0.0, 1.0));
    sky += 0.6 * mix(SkyPlum.rgb, sunColor, 0.6) * exp(-2.5 * max(length(p) - 1.0, 0.0)) * front;
    sky = mix(sky, sunColor, synthwave_sun(p, t) * front);

    vec3 ground = synthwave_floor(rd, t);
    vec3 col = sky_meet_horizon(mix(ground, sky, step(0.0, rd.y)), rd, horizon);

    vec4 canyon = textureLod(SkyBake0, canyon_uv(rd), 0.0);
    float depth = canyon.g * CANYON_DEPTH;
    vec3 rock = mix(SkyMain.rgb, SkyGlow.rgb, 0.5 + 0.5 * sin(0.6 * depth)) * canyon.r * canyon.r * 1.6;
    float phase = fract((depth * sign(rd.z) - t * SYNTHWAVE_PACE) / 6.0);
    float pulse = phase * phase * phase * phase * phase * phase;
    vec3 edges = mix(SkyGlow.rgb, vec3(1.0), 0.5) * canyon.a * canyon.a * (0.45 + 1.4 * pulse);
    return sky_dither(col * (1.0 - canyon.b) + rock + edges, frag);
}

#endif
#endif
