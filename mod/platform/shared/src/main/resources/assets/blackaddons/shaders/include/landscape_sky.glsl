#ifndef BLACKADDONS_LANDSCAPE_SKY_GLSL
#define BLACKADDONS_LANDSCAPE_SKY_GLSL
#ifdef SKY_LANDSCAPE

float landscape_clouds(vec2 p) {
    return sky_value_noise(p) * 0.65 + sky_value_noise(p * 2.3 + 5.7) * 0.35;
}

vec3 custom_sky(vec3 rd, vec2 frag, vec3 horizon) {
    vec3 sunDir = normalize(SkySun.xyz);
    float fromSun = 1.0 - max(dot(rd, sunDir), 0.0);
    float sunUp = smoothstep(-0.12, 0.05, sunDir.y);
    float up = max(rd.y, 0.0);
    vec3 sky = mix(SkyMain.rgb, SkyMain.rgb * 0.3, smoothstep(0.0, 0.3, up));
    sky = mix(sky, SkyPlum.rgb, max(0.1 - rd.y, 0.0));
    sky += SkyGlow.rgb * sunUp * (1.2 * exp(-1800.0 * fromSun) + 0.3 * exp(-70.0 * fromSun) + 0.35 * exp(-4.0 * fromSun));

    vec2 sheet = rd.xz / max(rd.y, 0.02) * 0.8 + vec2(0.0, SkyClock.x * 0.1);
    float cloud = smoothstep(0.45, 0.85, landscape_clouds(sheet)) * smoothstep(0.02, 0.3, rd.y);
    float stars = sky_stars(rd, 300.0, 0.9995) * SkySun.w * smoothstep(0.0, 0.1, rd.y) * (1.0 - cloud);
    sky += vec3(0.91, 0.87, 1.0) * stars;
    sky = mix(sky, sky * 1.8 + SkyGlow.rgb * sunUp * 0.06, cloud);
    sky = sky_meet_horizon(sky, rd, horizon);

    vec2 hill = textureLod(SkyBake0, hill_uv(rd), 0.0).rg;
    vec3 ground = mix(SkyGround.rgb, mix(SkyGround.rgb, SkyMain.rgb, 0.3), hill.y * hill.y);
    return sky_dither(mix(sky, ground, hill.x), frag);
}

#endif
#endif
