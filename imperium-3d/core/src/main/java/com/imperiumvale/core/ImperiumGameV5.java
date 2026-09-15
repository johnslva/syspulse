package com.imperiumvale.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelCache;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.Array;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Imperium Vale V5 visual art-direction layer.
 *
 * Keeps the tested V4 simulation and touch controls, while replacing the most
 * visible prototype artifacts with a continuous terrain skin, mixed woodland,
 * village props and architectural accents. The overlay deliberately remains
 * procedural and cache-friendly for the Galaxy S25 FE target.
 */
public final class ImperiumGameV5 extends ApplicationAdapter {
    private static final long ATTR = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
    private static final long ATTR_TEX = ATTR | VertexAttributes.Usage.TextureCoordinates;

    private final ImperiumGameV4 game = new ImperiumGameV4();
    private final Array<Model> models = new Array<>();
    private final Array<Texture> textures = new Array<>();
    private final Array<ModelInstance> scenery = new Array<>();
    private final RandomXS128 rng = new RandomXS128(8807311L);

    private ModelBatch batch;
    private ModelCache cache;
    private PerspectiveCamera camera;
    private Environment environment;
    private Field modeField;
    private Field lowQualityField;
    private Method drawWorldFeedback;
    private Method drawUi;
    private String lastMode = "MENU";
    private float hdGrace;

    @Override
    public void create() {
        game.create();
        batch = new ModelBatch();
        bindDelegate();
        tuneLighting();
        buildScenery();
        buildCache();
    }

    private void bindDelegate() {
        try {
            Class<?> c = ImperiumGameV4.class;
            Field cameraField = c.getDeclaredField("camera");
            cameraField.setAccessible(true);
            camera = (PerspectiveCamera) cameraField.get(game);

            Field environmentField = c.getDeclaredField("environment");
            environmentField.setAccessible(true);
            environment = (Environment) environmentField.get(game);

            modeField = c.getDeclaredField("mode");
            modeField.setAccessible(true);
            lowQualityField = c.getDeclaredField("lowQuality");
            lowQualityField.setAccessible(true);

            drawWorldFeedback = c.getDeclaredMethod("drawWorldFeedback");
            drawWorldFeedback.setAccessible(true);
            drawUi = c.getDeclaredMethod("drawUi");
            drawUi.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to bind V5 art layer to V4 core", e);
        }
    }

    private void tuneLighting() {
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.32f, 0.35f, 0.32f, 1f));
        environment.set(new ColorAttribute(ColorAttribute.Fog, 0.50f, 0.58f, 0.56f, 1f));
        environment.add(new DirectionalLight().set(0.24f, 0.29f, 0.34f, 0.35f, -0.45f, 0.55f));
    }

    private Texture terrainTexture() {
        int size = 192;
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int h = x * 73856093 ^ y * 19349663 ^ (x * y * 83492791);
                h ^= h << 13;
                h ^= h >>> 17;
                h ^= h << 5;
                float fine = ((h & 2047) / 2047f - 0.5f);
                float broad = (float)(Math.sin(x * 0.10) * Math.cos(y * 0.075)) * 0.5f;
                float r = 0.17f + fine * 0.045f + broad * 0.018f;
                float g = 0.34f + fine * 0.070f + broad * 0.030f;
                float b = 0.105f + fine * 0.035f + broad * 0.012f;
                if ((x + y * 3) % 61 == 0) {
                    r *= 0.80f;
                    g *= 0.84f;
                    b *= 0.78f;
                }
                p.setColor(clamp(r), clamp(g), clamp(b), 1f);
                p.drawPixel(x, y);
            }
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        p.dispose();
        textures.add(t);
        return t;
    }

    private Material mat(float r, float g, float b) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
    }

    private Material tex(Texture t, float r, float g, float b) {
        return new Material(TextureAttribute.createDiffuse(t), ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
    }

    private Model own(Model m) {
        models.add(m);
        return m;
    }

    private void buildScenery() {
        ModelBuilder mb = new ModelBuilder();
        Texture groundTexture = terrainTexture();

        // One continuous terrain skin hides the old tile seams while remaining
        // below roads, water, buildings and gameplay markers.
        Model ground = own(mb.createBox(84f, 0.14f, 84f, tex(groundTexture, 0.92f, 0.96f, 0.88f), ATTR_TEX));
        add(ground, 0f, -0.11f, 0f, 0f, 1f, 1f, 1f);

        // Subtle irregular-looking color breaks. These are long, low ellipses
        // rather than the large circular patches used by earlier builds.
        Model moss = own(mb.createSphere(5.6f, 0.10f, 3.8f, 14, 5, mat(0.14f, 0.29f, 0.085f), ATTR));
        Model dry = own(mb.createSphere(4.8f, 0.08f, 3.2f, 14, 5, mat(0.27f, 0.34f, 0.13f), ATTR));
        for (int i = 0; i < 24; i++) {
            float x = rng.nextFloat() * 72f - 36f;
            float z = rng.nextFloat() * 72f - 36f;
            if (nearRoad(x, z) || Math.abs(x - 4.2f) < 6f || nearCastle(x, z)) continue;
            float sx = 0.45f + rng.nextFloat() * 0.65f;
            float sz = 0.42f + rng.nextFloat() * 0.70f;
            add((i & 1) == 0 ? moss : dry, x, -0.025f, z, rng.nextFloat() * 180f, sx, 1f, sz);
        }

        buildMixedTrees(mb);
        buildVillageAccents(mb, -25f, -18f, true);
        buildVillageAccents(mb, 25f, 18f, false);
        buildRoadsideProps(mb);
        buildCastleAccents(mb, -25f, -18f, true);
        buildCastleAccents(mb, 25f, 18f, false);
    }

    private void buildMixedTrees(ModelBuilder mb) {
        Model trunk = own(mb.createCylinder(0.50f, 2.7f, 0.50f, 9, mat(0.25f, 0.13f, 0.055f), ATTR));
        Model broadA = own(mb.createSphere(2.8f, 2.25f, 2.7f, 11, 8, mat(0.10f, 0.30f, 0.075f), ATTR));
        Model broadB = own(mb.createSphere(2.2f, 1.85f, 2.1f, 10, 7, mat(0.16f, 0.38f, 0.10f), ATTR));
        Model broadC = own(mb.createSphere(1.75f, 1.55f, 1.70f, 10, 7, mat(0.22f, 0.43f, 0.13f), ATTR));

        for (int i = 0; i < 36; i++) {
            float x;
            float z;
            do {
                x = rng.nextFloat() * 74f - 37f;
                z = rng.nextFloat() * 74f - 37f;
            } while (nearRoad(x, z) || Math.abs(x - 4.2f) < 6f || nearCastle(x, z));
            float s = 0.72f + rng.nextFloat() * 0.55f;
            add(trunk, x, 1.32f * s, z, 0f, s, s, s);
            add(broadA, x, 3.15f * s, z, rng.nextFloat() * 180f, s, s, s);
            add(broadB, x + 0.55f * s, 4.05f * s, z - 0.25f * s, rng.nextFloat() * 180f, s, s, s);
            if ((i & 1) == 0) add(broadC, x - 0.55f * s, 3.95f * s, z + 0.45f * s, 0f, s, s, s);
        }
    }

    private void buildVillageAccents(ModelBuilder mb, float cx, float cz, boolean blue) {
        Model beamV = own(mb.createBox(0.22f, 2.15f, 0.14f, mat(0.17f, 0.085f, 0.03f), ATTR));
        Model beamH = own(mb.createBox(2.65f, 0.20f, 0.14f, mat(0.17f, 0.085f, 0.03f), ATTR));
        Model window = own(mb.createBox(0.50f, 0.62f, 0.10f, mat(0.055f, 0.085f, 0.10f), ATTR));
        Model fence = own(mb.createBox(3.2f, 0.55f, 0.16f, mat(0.32f, 0.18f, 0.065f), ATTR));
        Model barrel = own(mb.createCylinder(0.60f, 0.82f, 0.60f, 10, mat(0.36f, 0.20f, 0.075f), ATTR));
        Model hay = own(mb.createCone(1.55f, 1.55f, 1.55f, 12, mat(0.62f, 0.49f, 0.18f), ATTR));
        float side = blue ? 1f : -1f;

        for (int i = 0; i < 5; i++) {
            float hx = cx + side * (6.7f + (i % 2) * 4.4f);
            float hz = cz + (i - 2) * 2.8f;
            add(beamV, hx - 1.25f, 1.25f, hz - 1.69f, 0f, 1f, 1f, 1f);
            add(beamV, hx + 1.25f, 1.25f, hz - 1.69f, 0f, 1f, 1f, 1f);
            add(beamH, hx, 1.72f, hz - 1.70f, 0f, 1f, 1f, 1f);
            add(window, hx, 1.18f, hz - 1.73f, 0f, 1f, 1f, 1f);
        }

        add(fence, cx + side * 11.8f, 0.33f, cz - 8.2f, 0f, 1f, 1f, 1f);
        add(fence, cx + side * 9.0f, 0.33f, cz - 8.2f, 0f, 0.75f, 1f, 1f);
        add(barrel, cx + side * 8.4f, 0.42f, cz + 7.0f, 0f, 1f, 1f, 1f);
        add(barrel, cx + side * 9.2f, 0.42f, cz + 7.4f, 0f, 0.82f, 0.92f, 0.82f);
        add(hay, cx + side * 11.0f, 0.78f, cz + 6.6f, 0f, 1f, 1f, 1f);
    }

    private void buildRoadsideProps(ModelBuilder mb) {
        Model crate = own(mb.createBox(0.90f, 0.90f, 0.90f, mat(0.39f, 0.23f, 0.085f), ATTR));
        Model cartBody = own(mb.createBox(2.1f, 0.70f, 1.15f, mat(0.34f, 0.18f, 0.06f), ATTR));
        Model wheel = own(mb.createCylinder(0.82f, 0.16f, 0.82f, 12, mat(0.15f, 0.085f, 0.03f), ATTR));
        float[][] points = {{-9f,-7f},{9f,7f},{-4f,10f},{15f,-7f}};
        for (int i = 0; i < points.length; i++) {
            float x = points[i][0];
            float z = points[i][1];
            add(crate, x + 1.2f, 0.46f, z + 1.0f, i * 17f, 1f, 1f, 1f);
            if (i < 2) {
                add(cartBody, x, 0.48f, z, -35f, 1f, 1f, 1f);
                add(wheel, x - 0.85f, 0.43f, z - 0.55f, 90f, 1f, 1f, 1f);
                add(wheel, x + 0.85f, 0.43f, z + 0.55f, 90f, 1f, 1f, 1f);
            }
        }
    }

    private void buildCastleAccents(ModelBuilder mb, float x, float z, boolean blue) {
        Model slit = own(mb.createBox(0.24f, 0.86f, 0.11f, mat(0.035f, 0.045f, 0.05f), ATTR));
        Model merlon = own(mb.createBox(0.72f, 0.72f, 0.78f, mat(0.64f, 0.64f, 0.60f), ATTR));
        Model banner = own(mb.createBox(0.70f, 1.75f, 0.08f,
            blue ? mat(0.06f, 0.24f, 0.72f) : mat(0.72f, 0.10f, 0.055f), ATTR));

        add(slit, x - 1.9f, 2.8f, z - 3.62f, 0f, 1f, 1f, 1f);
        add(slit, x + 1.9f, 2.8f, z - 3.62f, 0f, 1f, 1f, 1f);
        add(banner, x, 3.1f, z - 3.67f, 0f, 1f, 1f, 1f);
        for (int i = -3; i <= 3; i++) {
            add(merlon, x + i * 1.05f, 2.62f, z - 7.35f, 0f, 1f, 1f, 1f);
            add(merlon, x + i * 1.05f, 2.62f, z + 7.35f, 0f, 1f, 1f, 1f);
        }
    }

    private void buildCache() {
        cache = new ModelCache();
        cache.begin();
        for (ModelInstance i : scenery) cache.add(i);
        cache.end();
    }

    private void add(Model model, float x, float y, float z, float yaw,
                     float sx, float sy, float sz) {
        ModelInstance i = new ModelInstance(model);
        i.transform.setToTranslation(x, y, z).rotate(Vector3.Y, yaw).scale(sx, sy, sz);
        scenery.add(i);
    }

    private boolean nearRoad(float x, float z) {
        return Math.abs(z - x * (36f / 50f)) < 4.2f;
    }

    private boolean nearCastle(float x, float z) {
        return Vector3.dst(x, 0f, z, -25f, 0f, -18f) < 14f
            || Vector3.dst(x, 0f, z, 25f, 0f, 18f) < 14f;
    }

    @Override
    public void render() {
        float dt = Math.min(0.05f, Math.max(0f, Gdx.graphics.getDeltaTime()));
        updateHdGrace(dt);
        game.render();

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        batch.begin(camera);
        batch.render(cache, environment);
        batch.end();

        // V4 already painted HUD/selection once. Repaint them after the art
        // overlay so the world can never cover touch controls or selection UI.
        try {
            drawWorldFeedback.invoke(game);
            drawUi.invoke(game);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to repaint V4 UI", e);
        }
    }

    private void updateHdGrace(float dt) {
        try {
            String mode = String.valueOf(modeField.get(game));
            if (!mode.equals(lastMode)) {
                if ("GAME".equals(mode)) hdGrace = 7.0f;
                lastMode = mode;
            }
            if (hdGrace > 0f) {
                hdGrace -= dt;
                lowQualityField.setBoolean(game, false);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to control adaptive LOD", e);
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    @Override
    public void resize(int width, int height) {
        game.resize(width, height);
    }

    @Override
    public void pause() {
        game.pause();
    }

    @Override
    public void resume() {
        game.resume();
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (cache != null) cache.dispose();
        for (Model m : models) m.dispose();
        for (Texture t : textures) t.dispose();
        game.dispose();
    }
}
