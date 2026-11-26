package org.blackaddons.platform.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.blackaddons.core.log.Log;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Discovers every mixin under {@code org.blackaddons.platform.mixin} at load time, so {@code blackaddons.mixins.json}
 * doesn't have to list them. Names are relative to the config's {@code "package"}. Client only.
 *
 * <p>Mixins under a {@code sodium} package are only listed with Sodium loaded, under {@code vanilla} only without it,
 * and under {@code compat.<modid>} only with that mod. Listing is the gate, not {@link #shouldApplyMixin}: Mixin looks
 * up every listed target first, and a missing one logs a warning even for {@code @Pseudo}.
 */
public class AutoMixinDiscovery implements IMixinConfigPlugin {

    private static final String BASE_PACKAGE = "org.blackaddons.platform.mixin";
    private static final String BASE_PATH = BASE_PACKAGE.replace('.', '/');
    private static final String SODIUM_ONLY = ".sodium.";
    private static final String VANILLA_ONLY = ".vanilla.";
    private static final String COMPAT = "compat.";

    private List<String> mixins = List.of();

    @Override
    public void onLoad(String mixinPackage) {
        mixins = discover();
    }

    private List<String> discover() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return List.of();
        Set<String> found = new TreeSet<>();
        try {
            Enumeration<URL> resources = getClass().getClassLoader().getResources(BASE_PATH);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                switch (url.getProtocol()) {
                    case "jar" -> collectFromJar(url, found);
                    case "file" -> collectFromDir(url, found);
                    default -> {
                    }
                }
            }
        } catch (Exception e) {
            scanFailed(e);
        }
        return new ArrayList<>(found);
    }

    private void collectFromJar(URL url, Set<String> out) throws Exception {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        try (JarFile jar = new JarFile(connection.getJarFile().getName())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                add(entries.nextElement().getName(), out);
            }
        }
    }

    private void collectFromDir(URL url, Set<String> out) {
        String rootPath = URLDecoder.decode(url.getPath(), StandardCharsets.UTF_8);
        File root = new File(rootPath);
        if (!root.exists()) return;
        Path rootDir = root.toPath();
        try (var stream = Files.walk(rootDir)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                String rel = rootDir.relativize(file).toString().replace(File.separatorChar, '/');
                add(BASE_PATH + "/" + rel, out);
            });
        } catch (Exception e) {
            scanFailed(e);
        }
    }

    // A failed scan leaves events silently dead: log loudly, and fail in dev where it's a build problem.
    private static void scanFailed(Exception e) {
        Log.main.error("Mixin discovery failed; mixins may be missing", e);
        if (FabricLoader.getInstance().isDevelopmentEnvironment())
            throw new IllegalStateException("Mixin discovery failed", e);
    }

    private void add(String resourceName, Set<String> out) {
        if (!resourceName.startsWith(BASE_PATH) || !resourceName.endsWith(".class")) return;
        if (resourceName.contains("$") || resourceName.endsWith("/module-info.class")) return;
        String className = resourceName.substring(0, resourceName.length() - ".class".length()).replace('/', '.');
        if (!className.startsWith(BASE_PACKAGE + ".")) return;
        String mixin = className.substring(BASE_PACKAGE.length() + 1);
        if (targetsPresent(mixin)) out.add(mixin);
    }

    private static boolean targetsPresent(String mixin) {
        if (mixin.contains(SODIUM_ONLY)) return isLoaded("sodium");
        if (mixin.contains(VANILLA_ONLY)) return !isLoaded("sodium");
        if (!mixin.startsWith(COMPAT)) return true;
        int end = mixin.indexOf('.', COMPAT.length());
        return end < 0 || isLoaded(mixin.substring(COMPAT.length(), end));
    }

    private static boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public List<String> getMixins() {
        return new ArrayList<>(mixins);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {
    }
}
