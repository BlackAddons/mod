#ifndef BLACKADDONS_GALAXY_GLSL
#define BLACKADDONS_GALAXY_GLSL

const float GALAXY_EXTENT = 1.1;
const float GALAXY_TWIRL = 2.5;

vec2 galaxy_disc_point(vec2 uv) {
    return (uv * 2.0 - 1.0) * GALAXY_EXTENT;
}

vec2 galaxy_disc_uv(vec2 p) {
    return p / (2.0 * GALAXY_EXTENT) + 0.5;
}

float galaxy_noise(vec2 p) {
    p *= tanh(0.1 * length(p));
    return cos(p.x) * cos(p.y) * cos(p.x * sqrt(3.5)) * cos(p.y * sqrt(1.5));
}

float galaxy_soft_abs(float x, float k) {
    return abs(x) > k ? abs(x) : 0.5 / k * x * x + 0.5 * k;
}

float galaxy_height(vec2 p) {
    float l = length(p);
    float angle = atan(p.y, p.x);
    float arms = mix(1.0, pow(0.75 + 0.25 * sin(2.0 * (angle + l * GALAXY_TWIRL)), 3.0), tanh(6.0 * l));
    float twirled = angle + l * GALAXY_TWIRL;
    vec2 q = l * vec2(cos(twirled), sin(twirled));
    float sum = 0.0;
    float amplitude = 1.0;
    float frequency = 15.0;
    float total = 0.0;
    for (int i = 0; i < 11; i++) {
        sum += amplitude * galaxy_noise(q * frequency);
        amplitude *= sqrt(0.45);
        frequency *= sqrt(2.0);
        total += amplitude;
    }
    return galaxy_soft_abs(-0.25 + sum * arms / total, 0.5) * exp(-5.5 * l * l);
}

vec3 galaxy_normal(vec2 p) {
    vec2 e = vec2(0.000125, 0.0);
    return normalize(vec3(
        galaxy_height(p - e.xy) - galaxy_height(p + e.xy),
        2.0 * e.x,
        galaxy_height(p - e.yx) - galaxy_height(p + e.yx)
    ));
}

vec3 galaxy_stars(vec2 p) {
    float r = length(p);
    float angle = atan(p.y, p.x);
    r /= (1.0 + r) * 0.5;
    p = r * vec2(cos(angle), sin(angle));
    const float size = 0.0075;
    const mat2 turn = mat2(0.87758, -0.47943, 0.47943, 0.87758);
    vec3 nearest = vec3(1e4);
    for (int i = 0; i < 3; i++) {
        p = turn * p;
        vec2 cell = floor((p + size * 0.5) / size);
        vec2 local = mod(p + size * 0.5, size) - size * 0.5;
        /* Each turned grid hashes its own cells. */
        cell.x += float(i) * 4096.0;
        float d = length(local - 0.25 * size * (sky_hash2(cell) * 2.0 - 1.0));
        if (d < nearest.x) nearest = vec3(d, cell);
    }
    return nearest;
}

vec4 galaxy_disc(vec2 p) {
    float h = galaxy_height(p);
    vec3 n = galaxy_normal(p);
    float th = tanh(h);
    vec3 toLight = normalize(vec3(0.0, 0.5, 0.0) - vec3(p.x, th, p.y));
    float highlight = 0.25 * pow(max(dot(toLight, n), 0.0), 4.0);
    float arms = pow(max(0.5 * h, 1e-6), n.y * 1.75);
    float l = length(p);
    vec3 star = galaxy_stars(p);
    float brightness = sky_hash(star.yz);
    float size = pow(max(th * brightness, 0.0), 0.25) * 0.001;
    float stars = clamp(brightness * 5.0 * exp(-2.5 * l * l) * tanh(pow(size / star.x, 2.5)), 0.0, 1.0);
    stars *= smoothstep(0.0, 0.35, 1.0 - n.y);
    return clamp(vec4(h, highlight, arms, stars), 0.0, 1.0);
}

vec4 galaxy_nebula(vec3 rd) {
    vec3 q = rd * 2.2 + 1.4 * vec3(sky_fbm3(rd * 1.9 + 4.1), sky_fbm3(rd * 1.9 + 8.7), sky_fbm3(rd * 1.9 + 13.3));
    float cloud = sky_fbm3(q);
    float fold = 1.0 - abs(2.0 * sky_fbm3(q * 1.7 + 11.3) - 1.0);
    float filaments = pow(fold, 12.0) * smoothstep(0.5, 0.75, cloud);
    float knots = smoothstep(0.66, 0.86, sky_fbm3(rd * 14.0 + 5.9)) * cloud;
    float cluster = smoothstep(0.3, 0.75, sky_fbm3(rd * 2.4 + 7.7));
    return clamp(vec4(cloud, filaments, knots, cluster), 0.0, 1.0);
}

#ifdef GALAXY_DISC
vec4 sky_bake(vec2 uv) {
    return galaxy_disc(galaxy_disc_point(uv));
}
#endif

#ifdef GALAXY_NEBULA
vec4 sky_bake(vec2 uv) {
    return galaxy_nebula(sky_sphere_dir(uv));
}
#endif

#endif
