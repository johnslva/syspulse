package com.imperiumvale.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelCache;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Imperium Vale 3D V4.
 *
 * Visual-first mobile RTS pass: richer procedural terrain, denser medieval
 * architecture, improved unit silhouettes, touch-first controls and adaptive
 * LOD. All art is generated at runtime so the APK remains compact.
 */
public final class ImperiumGameV4 extends ApplicationAdapter {
    private static final long ATTR = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
    private static final long ATTR_TEX = ATTR | VertexAttributes.Usage.TextureCoordinates;
    private static final int PLAYER = 0;
    private static final int ENEMY = 1;
    private static final float WORLD = 40f;
    private static final float MAX_DT = 1f / 20f;
    private static final float DEG = 0.017453292f;
    private static final int CHUNK_COUNT = 6;
    private static final String VERSION = "v0.7.0-alpha";

    private enum Mode { MENU, GAME }
    private enum UnitType { VILLAGER, SWORD, ARCHER, CAVALRY }
    private enum ResourceType { FOOD, WOOD, GOLD }
    private enum BuildingType { HOUSE, BARRACKS, TOWER }

    private static final class Stronghold {
        final int team;
        final Vector3 pos = new Vector3();
        float hp = 2400f;
        final float maxHp = 2400f;
        float cooldown;

        Stronghold(int team, float x, float z) {
            this.team = team;
            pos.set(x, 0f, z);
        }
    }

    private static final class ResourceNode {
        final ResourceType type;
        final Vector3 pos = new Vector3();
        final float maxAmount;
        float amount;
        ModelInstance primary;
        ModelInstance detail;

        ResourceNode(ResourceType type, float x, float z, float amount) {
            this.type = type;
            pos.set(x, 0f, z);
            this.amount = amount;
            maxAmount = amount;
        }
    }

    private static final class Unit {
        int id;
        int team;
        UnitType type;
        final Vector3 pos = new Vector3();
        final Vector3 target = new Vector3();
        float hp;
        float maxHp;
        float speed;
        float damage;
        float range;
        float cooldown;
        float attackPeriod;
        float facingYaw;
        float thinkTimer;
        float anim;
        boolean moving;
        int targetUnitId = -1;
        int targetStronghold = -1;
        ResourceNode gatherNode;
        int gatherState;
        float gatherTimer;
        float carried;
        ModelInstance body;
        ModelInstance head;
        ModelInstance weapon;
        ModelInstance offhand;
        ModelInstance helmet;
        ModelInstance mount;
        ModelInstance mountHead;
        ModelInstance shadow;

        Unit(int id, int team, UnitType type, float x, float z) {
            this.id = id;
            this.team = team;
            this.type = type;
            pos.set(x, 0f, z);
            target.set(pos);
            switch (type) {
                case VILLAGER -> {
                    maxHp = 72f; speed = 4.5f; damage = 5.5f; range = 0.95f; attackPeriod = 1.0f;
                }
                case SWORD -> {
                    maxHp = 136f; speed = 4.0f; damage = 22f; range = 1.05f; attackPeriod = 0.86f;
                }
                case ARCHER -> {
                    maxHp = 86f; speed = 4.15f; damage = 15f; range = 6.8f; attackPeriod = 1.08f;
                }
                case CAVALRY -> {
                    maxHp = 188f; speed = 6.4f; damage = 30f; range = 1.42f; attackPeriod = 0.96f;
                }
            }
            hp = maxHp;
            thinkTimer = 0.1f + (id % 7) * 0.08f;
            anim = (id % 11) * 0.45f;
        }
    }

    private static final class Building {
        final int id;
        final int team;
        final BuildingType type;
        final Vector3 pos = new Vector3();
        float hp;
        float maxHp;
        float cooldown;
        ModelInstance body;
        ModelInstance roof;
        ModelInstance detail;

        Building(int id, int team, BuildingType type, float x, float z) {
            this.id = id;
            this.team = team;
            this.type = type;
            pos.set(x, 0f, z);
            switch (type) {
                case HOUSE -> maxHp = 560f;
                case BARRACKS -> maxHp = 900f;
                case TOWER -> maxHp = 780f;
            }
            hp = maxHp;
        }
    }

    private static final class TrainOrder {
        final UnitType type;
        float remaining;
        TrainOrder(UnitType type, float remaining) {
            this.type = type;
            this.remaining = remaining;
        }
    }

    private static final class Projectile {
        final Vector3 start = new Vector3();
        final Vector3 end = new Vector3();
        final ModelInstance model;
        float life;
        final float duration;

        Projectile(ModelInstance model, Vector3 start, Vector3 end, float duration) {
            this.model = model;
            this.start.set(start);
            this.end.set(end);
            this.duration = duration;
            life = duration;
        }
    }

    private static final class StaticPiece {
        final ModelInstance instance;
        final float x;
        final float z;
        final boolean transparent;

        StaticPiece(ModelInstance instance, float x, float z, boolean transparent) {
            this.instance = instance;
            this.x = x;
            this.z = z;
            this.transparent = transparent;
        }
    }

    private static final class Chunk {
        final Array<ModelInstance> instances = new Array<>();
        final Vector3 center = new Vector3();
        ModelCache cache;
        float radius;
    }

    private ModelBatch modelBatch;
    private ShapeRenderer shapes;
    private SpriteBatch sprites;
    private BitmapFont font;
    private PerspectiveCamera camera;
    private Environment environment;

    private final Plane groundPlane = new Plane(Vector3.Y, 0f);
    private final Vector3 cameraTarget = new Vector3();
    private final Vector3 tmp3 = new Vector3();
    private final Vector3 tmpScreen = new Vector3();
    private final Matrix4 uiMatrix = new Matrix4();
    private final RandomXS128 rng = new RandomXS128(44190217L);

    private final Array<Model> ownedModels = new Array<>();
    private final Array<Texture> ownedTextures = new Array<>();
    private final Array<StaticPiece> staticPieces = new Array<>();
    private final Array<StaticPiece> transparentPieces = new Array<>();
    private final Array<Chunk> chunks = new Array<>();
    private final List<Unit> units = new ArrayList<>();
    private final List<ResourceNode> resources = new ArrayList<>();
    private final List<Building> buildings = new ArrayList<>();
    private final List<TrainOrder> trainQueue = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final Set<Integer> selected = new HashSet<>();

    private Texture grassTexture;
    private Texture dirtTexture;
    private Texture stoneTexture;
    private Texture timberTexture;
    private Texture roofTexture;

    private Model playerBody;
    private Model enemyBody;
    private Model playerCavalryBody;
    private Model enemyCavalryBody;
    private Model headModel;
    private Model helmetModel;
    private Model hoodModel;
    private Model villagerHatModel;
    private Model swordModel;
    private Model bowModel;
    private Model toolModel;
    private Model blueShieldModel;
    private Model redShieldModel;
    private Model horseModel;
    private Model horseHeadModel;
    private Model shadowModel;
    private Model projectileModel;
    private Model foodNodeModel;
    private Model foodDetailModel;
    private Model woodNodeModel;
    private Model woodDetailModel;
    private Model goldNodeModel;
    private Model goldDetailModel;
    private Model houseBodyModel;
    private Model houseRoofModel;
    private Model houseDetailModel;
    private Model barracksBodyModel;
    private Model barracksRoofModel;
    private Model barracksDetailModel;
    private Model towerBodyModel;
    private Model towerRoofModel;
    private Model towerDetailModel;

    private Stronghold playerStronghold;
    private Stronghold enemyStronghold;

    private Mode mode = Mode.MENU;
    private int difficulty = 1;
    private boolean gameOver;
    private String endText = "";
    private int nextId = 1;
    private int nextBuildingId = 1;
    private int populationCap = 40;
    private int barracksCount = 1;
    private float food = 660f;
    private float wood = 650f;
    private float gold = 430f;
    private float aiAccumulator;
    private float menuOrbit;
    private float cameraDistance = 42f;
    private float cameraYaw = 42f;
    private float cameraPitch = 50f;
    private float fpsSmooth = 60f;
    private float lowFpsTimer;
    private float highFpsTimer;
    private boolean lowQuality;
    private final Vector3 commandMarker = new Vector3();
    private float commandMarkerTime;
    private BuildingType pendingBuild;

    private final Rectangle easyButton = new Rectangle();
    private final Rectangle normalButton = new Rectangle();
    private final Rectangle hardButton = new Rectangle();
    private final Rectangle playButton = new Rectangle();
    private final Rectangle villagerButton = new Rectangle();
    private final Rectangle swordButton = new Rectangle();
    private final Rectangle archerButton = new Rectangle();
    private final Rectangle cavalryButton = new Rectangle();
    private final Rectangle houseButton = new Rectangle();
    private final Rectangle barracksButton = new Rectangle();
    private final Rectangle towerButton = new Rectangle();

    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private float pinchDistance;
    private float pinchCameraDistance;
    private boolean dragged;
    private long lastTapMs;
    private int lastTapUnit = -1;

    @Override
    public void create() {
        modelBatch = new ModelBatch();
        shapes = new ShapeRenderer();
        sprites = new SpriteBatch();
        font = new BitmapFont();
        font.getData().markupEnabled = false;

        camera = new PerspectiveCamera(37f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.25f;
        camera.far = 170f;

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.36f, 0.39f, 0.36f, 1f));
        environment.set(new ColorAttribute(ColorAttribute.Fog, 0.58f, 0.65f, 0.63f, 1f));
        environment.add(new DirectionalLight().set(1.10f, 0.95f, 0.76f, -0.58f, -0.82f, -0.33f));
        environment.add(new DirectionalLight().set(0.16f, 0.25f, 0.36f, 0.40f, -0.38f, 0.48f));

        buildTextures();
        buildSharedModels();
        buildWorld();
        rebuildChunks();
        resetMenuCamera();
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        Gdx.input.setInputProcessor(new GameInput());
    }

    private Texture makeTexture(int size, float br, float bg, float bb, float variation, int style) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int h = x * 928371 + y * 364479 + style * 71837;
                h ^= h << 13; h ^= h >>> 17; h ^= h << 5;
                float n = ((h & 2047) / 2047f - 0.5f) * variation;
                float r = clamp(br + n, 0f, 1f);
                float g = clamp(bg + n, 0f, 1f);
                float b = clamp(bb + n, 0f, 1f);
                if (style == 1 && ((x + y * 2) % 23 == 0)) { r *= 0.78f; g *= 0.76f; b *= 0.72f; }
                if (style == 2 && (y % 14 == 0 || (x + ((y / 14) & 1) * 7) % 14 == 0)) { r *= 0.62f; g *= 0.62f; b *= 0.62f; }
                if (style == 3 && (x % 10 == 0)) { r *= 0.70f; g *= 0.68f; b *= 0.66f; }
                if (style == 4 && ((x + y) % 15 == 0)) { r *= 0.76f; g *= 0.74f; b *= 0.72f; }
                p.setColor(r, g, b, 1f);
                p.drawPixel(x, y);
            }
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        p.dispose();
        ownedTextures.add(t);
        return t;
    }

    private void buildTextures() {
        grassTexture = makeTexture(96, 0.26f, 0.46f, 0.18f, 0.16f, 0);
        dirtTexture = makeTexture(96, 0.47f, 0.34f, 0.19f, 0.18f, 1);
        stoneTexture = makeTexture(96, 0.56f, 0.56f, 0.53f, 0.12f, 2);
        timberTexture = makeTexture(96, 0.48f, 0.31f, 0.15f, 0.14f, 3);
        roofTexture = makeTexture(96, 0.38f, 0.12f, 0.07f, 0.12f, 4);
    }

    private Material material(float r, float g, float b) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
    }

    private Material textured(Texture t, float r, float g, float b) {
        return new Material(TextureAttribute.createDiffuse(t), ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
    }

    private Material alphaMaterial(float r, float g, float b, float a) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, a)),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, a));
    }

    private Model own(Model m) {
        ownedModels.add(m);
        return m;
    }

    private void buildSharedModels() {
        ModelBuilder mb = new ModelBuilder();
        playerBody = own(mb.createCapsule(0.39f, 1.44f, 12, material(0.08f, 0.30f, 0.72f), ATTR));
        enemyBody = own(mb.createCapsule(0.39f, 1.44f, 12, material(0.70f, 0.10f, 0.065f), ATTR));
        playerCavalryBody = own(mb.createCapsule(0.39f, 1.48f, 12, material(0.07f, 0.27f, 0.67f), ATTR));
        enemyCavalryBody = own(mb.createCapsule(0.39f, 1.48f, 12, material(0.65f, 0.085f, 0.055f), ATTR));
        headModel = own(mb.createSphere(0.45f, 0.45f, 0.45f, 12, 8, material(0.80f, 0.66f, 0.49f), ATTR));
        helmetModel = own(mb.createSphere(0.52f, 0.28f, 0.52f, 12, 6, material(0.31f, 0.33f, 0.34f), ATTR));
        hoodModel = own(mb.createCone(0.92f, 0.75f, 0.92f, 10, material(0.25f, 0.18f, 0.10f), ATTR));
        villagerHatModel = own(mb.createCone(1.02f, 0.42f, 1.02f, 10, material(0.54f, 0.39f, 0.18f), ATTR));
        swordModel = own(mb.createBox(0.10f, 1.12f, 0.10f, material(0.78f, 0.80f, 0.82f), ATTR));
        bowModel = own(mb.createBox(0.08f, 1.08f, 0.20f, material(0.38f, 0.20f, 0.07f), ATTR));
        toolModel = own(mb.createBox(0.10f, 0.94f, 0.10f, material(0.34f, 0.19f, 0.07f), ATTR));
        blueShieldModel = own(mb.createCylinder(0.78f, 0.13f, 0.78f, 12, material(0.10f, 0.33f, 0.76f), ATTR));
        redShieldModel = own(mb.createCylinder(0.78f, 0.13f, 0.78f, 12, material(0.74f, 0.13f, 0.08f), ATTR));
        horseModel = own(mb.createBox(1.72f, 0.82f, 0.72f, material(0.28f, 0.15f, 0.065f), ATTR));
        horseHeadModel = own(mb.createBox(0.55f, 0.70f, 0.52f, material(0.30f, 0.16f, 0.07f), ATTR));
        shadowModel = own(mb.createCylinder(1.45f, 0.025f, 0.88f, 14, alphaMaterial(0.015f, 0.02f, 0.015f, 0.33f), ATTR));
        projectileModel = own(mb.createBox(0.07f, 0.07f, 0.62f, material(0.27f, 0.16f, 0.06f), ATTR));

        foodNodeModel = own(mb.createSphere(2.35f, 1.20f, 2.0f, 12, 8, material(0.20f, 0.45f, 0.11f), ATTR));
        foodDetailModel = own(mb.createSphere(0.38f, 0.38f, 0.38f, 8, 6, material(0.70f, 0.16f, 0.10f), ATTR));
        woodNodeModel = own(mb.createCylinder(1.30f, 2.20f, 1.30f, 9, textured(timberTexture, 0.90f, 0.84f, 0.72f), ATTR_TEX));
        woodDetailModel = own(mb.createCone(3.0f, 4.1f, 3.0f, 9, material(0.10f, 0.31f, 0.08f), ATTR));
        goldNodeModel = own(mb.createSphere(2.25f, 1.45f, 1.95f, 10, 7, material(0.45f, 0.43f, 0.36f), ATTR));
        goldDetailModel = own(mb.createSphere(0.58f, 0.42f, 0.48f, 8, 5, material(0.91f, 0.66f, 0.10f), ATTR));

        houseBodyModel = own(mb.createBox(3.9f, 2.40f, 3.35f, textured(timberTexture, 0.96f, 0.84f, 0.69f), ATTR_TEX));
        houseRoofModel = own(mb.createCone(4.85f, 2.45f, 4.15f, 4, textured(roofTexture, 0.80f, 0.82f, 0.82f), ATTR_TEX));
        houseDetailModel = own(mb.createBox(0.58f, 1.15f, 0.16f, material(0.14f, 0.09f, 0.04f), ATTR));
        barracksBodyModel = own(mb.createBox(5.8f, 2.75f, 4.35f, textured(stoneTexture, 0.85f, 0.84f, 0.78f), ATTR_TEX));
        barracksRoofModel = own(mb.createCone(6.55f, 2.55f, 5.05f, 4, textured(roofTexture, 0.78f, 0.78f, 0.78f), ATTR_TEX));
        barracksDetailModel = own(mb.createBox(1.45f, 2.05f, 0.18f, textured(timberTexture, 0.65f, 0.58f, 0.48f), ATTR_TEX));
        towerBodyModel = own(mb.createCylinder(2.75f, 6.2f, 2.75f, 14, textured(stoneTexture, 0.88f, 0.87f, 0.82f), ATTR_TEX));
        towerRoofModel = own(mb.createCone(3.35f, 2.55f, 3.35f, 14, textured(roofTexture, 0.78f, 0.80f, 0.82f), ATTR_TEX));
        towerDetailModel = own(mb.createBox(0.52f, 1.05f, 0.16f, material(0.08f, 0.09f, 0.09f), ATTR));
    }

    private void buildWorld() {
        staticPieces.clear();
        transparentPieces.clear();
        ModelBuilder mb = new ModelBuilder();

        Model[] ground = new Model[] {
            own(mb.createBox(8.05f, 0.34f, 8.05f, textured(grassTexture, 0.90f, 0.98f, 0.87f), ATTR_TEX)),
            own(mb.createBox(8.05f, 0.34f, 8.05f, textured(grassTexture, 0.80f, 0.93f, 0.76f), ATTR_TEX)),
            own(mb.createBox(8.05f, 0.34f, 8.05f, textured(grassTexture, 0.98f, 1.00f, 0.91f), ATTR_TEX))
        };
        for (int gx = -5; gx <= 5; gx++) {
            for (int gz = -5; gz <= 5; gz++) {
                int idx = Math.abs(gx * 19 + gz * 31 + gx * gz * 7) % ground.length;
                float y = -0.25f + (float)Math.sin(gx * 0.68f) * 0.09f + (float)Math.cos(gz * 0.61f) * 0.07f;
                addStatic(ground[idx], gx * 8f, y, gz * 8f, 0f, 1f, 1f, 1f, false);
            }
        }

        createGroundDetail(mb);
        createRoad(mb);
        createRiver(mb);
        createBase(mb, PLAYER, -25f, -18f);
        createBase(mb, ENEMY, 25f, 18f);
        createForest(mb);
        createRockFields(mb);
    }

    private void createGroundDetail(ModelBuilder mb) {
        Model patch = own(mb.createCylinder(5.8f, 0.035f, 4.8f, 18, material(0.25f, 0.40f, 0.15f), ATTR));
        Model patch2 = own(mb.createCylinder(4.2f, 0.028f, 3.6f, 16, material(0.36f, 0.48f, 0.20f), ATTR));
        for (int i = 0; i < 44; i++) {
            float x = rng.nextFloat() * 74f - 37f;
            float z = rng.nextFloat() * 74f - 37f;
            if (nearMainRoad(x, z) || Math.abs(x - 4f) < 5.5f) continue;
            float s = 0.55f + rng.nextFloat() * 0.75f;
            addStatic((i & 1) == 0 ? patch : patch2, x, 0.005f, z, 0f, s, 1f, s, false);
        }
    }

    private void createRoad(ModelBuilder mb) {
        Model road = own(mb.createBox(7.2f, 0.07f, 4.85f, textured(dirtTexture, 1f, 0.96f, 0.88f), ATTR_TEX));
        Model edge = own(mb.createBox(7.2f, 0.045f, 0.42f, material(0.33f, 0.31f, 0.19f), ATTR));
        float ax = -25f, az = -18f;
        float bx = 25f, bz = 18f;
        float dx = bx - ax, dz = bz - az;
        float yaw = (float)Math.toDegrees(Math.atan2(dx, dz));
        for (int i = 0; i <= 12; i++) {
            float t = i / 12f;
            float x = ax + dx * t;
            float z = az + dz * t + (float)Math.sin(t * 6.283f) * 0.75f;
            addStatic(road, x, 0.018f, z, yaw, 1f, 1f, 1f, false);
            if ((i & 1) == 0) {
                addStatic(edge, x - 1.2f, 0.052f, z - 2.15f, yaw, 0.85f, 1f, 1f, false);
                addStatic(edge, x + 1.2f, 0.052f, z + 2.15f, yaw, 0.85f, 1f, 1f, false);
            }
        }
    }

    private void createRiver(ModelBuilder mb) {
        Model water = own(mb.createBox(7.2f, 0.045f, 8.2f, alphaMaterial(0.055f, 0.30f, 0.46f, 0.86f), ATTR));
        Model shallow = own(mb.createBox(8.6f, 0.025f, 8.2f, alphaMaterial(0.18f, 0.47f, 0.55f, 0.54f), ATTR));
        Model bank = own(mb.createBox(0.95f, 0.20f, 8.2f, textured(dirtTexture, 0.84f, 0.82f, 0.70f), ATTR_TEX));
        for (int i = -5; i <= 5; i++) {
            float z = i * 7.8f;
            float x = 4.2f + (float)Math.sin(i * 0.74f) * 1.55f;
            addStatic(shallow, x, -0.02f, z, -4f, 1f, 1f, 1f, true);
            addStatic(water, x, 0.008f, z, -4f, 1f, 1f, 1f, true);
            addStatic(bank, x - 4.25f, 0.02f, z, -4f, 1f, 1f, 1f, false);
            addStatic(bank, x + 4.25f, 0.02f, z, -4f, 1f, 1f, 1f, false);
        }

        Model deck = own(mb.createBox(11.2f, 0.45f, 5.4f, textured(timberTexture, 0.96f, 0.90f, 0.80f), ATTR_TEX));
        Model rail = own(mb.createBox(11.2f, 0.60f, 0.24f, material(0.24f, 0.13f, 0.05f), ATTR));
        Model post = own(mb.createBox(0.30f, 1.05f, 0.30f, material(0.25f, 0.14f, 0.055f), ATTR));
        addStatic(deck, 4.2f, 0.30f, 0.3f, -32f, 1f, 1f, 1f, false);
        addStatic(rail, 4.2f, 0.80f, -2.3f, -32f, 1f, 1f, 1f, false);
        addStatic(rail, 4.2f, 0.80f, 2.9f, -32f, 1f, 1f, 1f, false);
        for (int i = -2; i <= 2; i++) {
            addStatic(post, 4.2f + i * 2.1f, 0.83f, -2.3f, -32f, 1f, 1f, 1f, false);
            addStatic(post, 4.2f + i * 2.1f, 0.83f, 2.9f, -32f, 1f, 1f, 1f, false);
        }
    }

    private void createBase(ModelBuilder mb, int team, float x, float z) {
        Color teamColor = team == PLAYER ? new Color(0.08f, 0.27f, 0.72f, 1f) : new Color(0.70f, 0.12f, 0.075f, 1f);
        Model keep = own(mb.createBox(8.2f, 4.2f, 7.0f, textured(stoneTexture, 0.90f, 0.90f, 0.86f), ATTR_TEX));
        Model tower = own(mb.createCylinder(2.45f, 6.6f, 2.45f, 14, textured(stoneTexture, 0.78f, 0.78f, 0.74f), ATTR_TEX));
        Model cone = own(mb.createCone(3.0f, 2.6f, 3.0f, 14, new Material(ColorAttribute.createDiffuse(teamColor)), ATTR));
        Model battlement = own(mb.createBox(0.82f, 0.72f, 0.92f, textured(stoneTexture, 0.98f, 0.98f, 0.94f), ATTR_TEX));
        Model door = own(mb.createBox(1.55f, 2.55f, 0.20f, textured(timberTexture, 0.62f, 0.55f, 0.46f), ATTR_TEX));
        Model window = own(mb.createBox(0.46f, 0.90f, 0.16f, material(0.055f, 0.075f, 0.085f), ATTR));
        Model flagPole = own(mb.createCylinder(0.10f, 4.6f, 0.10f, 6, material(0.22f, 0.19f, 0.14f), ATTR));
        Model flag = own(mb.createBox(1.65f, 0.82f, 0.08f, new Material(ColorAttribute.createDiffuse(teamColor)), ATTR));

        addStatic(keep, x, 2.1f, z, 0f, 1f, 1f, 1f, false);
        float[][] offsets = {{-3.6f,-3.0f},{3.6f,-3.0f},{-3.6f,3.0f},{3.6f,3.0f}};
        for (float[] o : offsets) {
            addStatic(tower, x + o[0], 3.3f, z + o[1], 0f, 1f, 1f, 1f, false);
            addStatic(cone, x + o[0], 7.9f, z + o[1], 0f, 1f, 1f, 1f, false);
        }
        for (int i = -3; i <= 3; i++) {
            addStatic(battlement, x + i * 1.05f, 4.55f, z - 3.15f, 0f, 1f, 1f, 1f, false);
            addStatic(battlement, x + i * 1.05f, 4.55f, z + 3.15f, 0f, 1f, 1f, 1f, false);
        }
        addStatic(door, x, 1.3f, z - 3.58f, 0f, 1f, 1f, 1f, false);
        addStatic(window, x - 2.1f, 2.75f, z - 3.59f, 0f, 1f, 1f, 1f, false);
        addStatic(window, x + 2.1f, 2.75f, z - 3.59f, 0f, 1f, 1f, 1f, false);
        addStatic(flagPole, x, 6.1f, z, 0f, 1f, 1f, 1f, false);
        addStatic(flag, x + 0.82f, 7.55f, z, 0f, 1f, 1f, 1f, false);

        Model wall = own(mb.createBox(7.2f, 2.25f, 0.78f, textured(stoneTexture, 0.75f, 0.75f, 0.72f), ATTR_TEX));
        for (int side = -1; side <= 1; side += 2) {
            addStatic(wall, x - 6.8f, 1.13f, z + side * 7.3f, 0f, 1f, 1f, 1f, false);
            addStatic(wall, x + 6.8f, 1.13f, z + side * 7.3f, 0f, 1f, 1f, 1f, false);
        }

        createVillage(mb, team, x, z, teamColor);
    }

    private void createVillage(ModelBuilder mb, int team, float x, float z, Color teamColor) {
        Model house = own(mb.createBox(3.8f, 2.30f, 3.2f, textured(timberTexture, 0.95f, 0.85f, 0.72f), ATTR_TEX));
        Model roof = own(mb.createCone(4.75f, 2.35f, 4.05f, 4, textured(roofTexture,
            team == PLAYER ? 0.58f : 0.92f, team == PLAYER ? 0.72f : 0.68f, team == PLAYER ? 1.0f : 0.65f), ATTR_TEX));
        Model beam = own(mb.createBox(0.24f, 2.20f, 0.18f, material(0.20f, 0.11f, 0.045f), ATTR));
        Model chimney = own(mb.createBox(0.48f, 1.35f, 0.48f, textured(stoneTexture, 0.76f, 0.72f, 0.68f), ATTR_TEX));
        float side = team == PLAYER ? 1f : -1f;
        for (int i = 0; i < 5; i++) {
            float sx = x + side * (6.7f + (i % 2) * 4.4f);
            float sz = z + (i - 2) * 2.8f;
            addStatic(house, sx, 1.15f, sz, 0f, 1f, 1f, 1f, false);
            addStatic(roof, sx, 3.35f, sz, 45f, 1f, 1f, 1f, false);
            addStatic(beam, sx - 1.25f, 1.25f, sz - 1.62f, 0f, 1f, 1f, 1f, false);
            addStatic(beam, sx + 1.25f, 1.25f, sz - 1.62f, 0f, 1f, 1f, 1f, false);
            if ((i & 1) == 0) addStatic(chimney, sx + 0.85f, 3.65f, sz + 0.55f, 0f, 1f, 1f, 1f, false);
        }

        Model barracks = own(mb.createBox(5.4f, 2.55f, 4.0f, textured(stoneTexture, 0.81f, 0.79f, 0.72f), ATTR_TEX));
        Model barracksRoof = own(mb.createCone(6.2f, 2.4f, 4.8f, 4, new Material(ColorAttribute.createDiffuse(teamColor.cpy().mul(0.72f))), ATTR));
        addStatic(barracks, x - side * 6.5f, 1.28f, z + side * 4.9f, 0f, 1f, 1f, 1f, false);
        addStatic(barracksRoof, x - side * 6.5f, 3.58f, z + side * 4.9f, 45f, 1f, 1f, 1f, false);
    }

    private void createForest(ModelBuilder mb) {
        Model trunk = own(mb.createCylinder(0.48f, 2.6f, 0.48f, 8, textured(timberTexture, 0.72f, 0.66f, 0.56f), ATTR_TEX));
        Model crownA = own(mb.createCone(2.8f, 3.8f, 2.8f, 10, material(0.085f, 0.29f, 0.075f), ATTR));
        Model crownB = own(mb.createCone(2.15f, 3.2f, 2.15f, 10, material(0.13f, 0.38f, 0.10f), ATTR));
        Model crownC = own(mb.createCone(1.55f, 2.6f, 1.55f, 10, material(0.20f, 0.44f, 0.14f), ATTR));
        for (int i = 0; i < 82; i++) {
            float x;
            float z;
            do {
                x = rng.nextFloat() * 76f - 38f;
                z = rng.nextFloat() * 76f - 38f;
            } while (nearBase(x, z) || nearMainRoad(x, z) || Math.abs(x - 4.2f) < 5.5f);
            float s = 0.68f + rng.nextFloat() * 0.62f;
            addStatic(trunk, x, 1.28f * s, z, 0f, s, s, s, false);
            addStatic(crownA, x, 3.20f * s, z, rng.nextFloat() * 360f, s, s, s, false);
            addStatic(crownB, x, 4.35f * s, z, rng.nextFloat() * 360f, s, s, s, false);
            if ((i % 3) != 0) addStatic(crownC, x, 5.25f * s, z, rng.nextFloat() * 360f, s, s, s, false);
        }
    }

    private void createRockFields(ModelBuilder mb) {
        Model rockA = own(mb.createSphere(1.15f, 0.72f, 0.98f, 8, 5, material(0.40f, 0.41f, 0.39f), ATTR));
        Model rockB = own(mb.createSphere(0.72f, 0.52f, 0.66f, 8, 5, material(0.52f, 0.51f, 0.47f), ATTR));
        for (int i = 0; i < 36; i++) {
            float x = rng.nextFloat() * 72f - 36f;
            float z = rng.nextFloat() * 72f - 36f;
            if (nearBase(x, z) || nearMainRoad(x, z) || Math.abs(x - 4.2f) < 5.8f) continue;
            float s = 0.42f + rng.nextFloat() * 0.78f;
            addStatic((i & 1) == 0 ? rockA : rockB, x, 0.30f * s, z, rng.nextFloat() * 180f, s, s, s, false);
        }
    }

    private boolean nearBase(float x, float z) {
        return Vector2.dst(x, z, -25f, -18f) < 13.5f || Vector2.dst(x, z, 25f, 18f) < 13.5f;
    }

    private boolean nearMainRoad(float x, float z) {
        return Math.abs(z - x * (36f / 50f)) < 3.5f;
    }

    private void addStatic(Model model, float x, float y, float z, float yaw,
                           float sx, float sy, float sz, boolean transparent) {
        ModelInstance instance = new ModelInstance(model);
        instance.transform.setToTranslation(x, y, z).rotate(Vector3.Y, yaw).scale(sx, sy, sz);
        StaticPiece p = new StaticPiece(instance, x, z, transparent);
        if (transparent) transparentPieces.add(p); else staticPieces.add(p);
    }

    private void rebuildChunks() {
        for (Chunk c : chunks) if (c.cache != null) c.cache.dispose();
        chunks.clear();
        float size = WORLD * 2f / CHUNK_COUNT;
        for (int z = 0; z < CHUNK_COUNT; z++) {
            for (int x = 0; x < CHUNK_COUNT; x++) {
                Chunk c = new Chunk();
                c.center.set(-WORLD + (x + 0.5f) * size, 2.0f, -WORLD + (z + 0.5f) * size);
                c.radius = size * 1.18f;
                chunks.add(c);
            }
        }
        for (StaticPiece p : staticPieces) {
            int cx = (int)((p.x + WORLD) / (WORLD * 2f) * CHUNK_COUNT);
            int cz = (int)((p.z + WORLD) / (WORLD * 2f) * CHUNK_COUNT);
            cx = Math.max(0, Math.min(CHUNK_COUNT - 1, cx));
            cz = Math.max(0, Math.min(CHUNK_COUNT - 1, cz));
            chunks.get(cz * CHUNK_COUNT + cx).instances.add(p.instance);
        }
        for (Chunk c : chunks) {
            if (c.instances.size == 0) continue;
            c.cache = new ModelCache();
            c.cache.begin();
            for (ModelInstance i : c.instances) c.cache.add(i);
            c.cache.end();
        }
    }

    private void resetMenuCamera() {
        cameraTarget.set(0f, 1f, 0f);
        cameraDistance = 51f;
        cameraYaw = 42f;
        cameraPitch = 51f;
        updateCamera();
    }

    private void resetGame() {
        mode = Mode.GAME;
        units.clear(); resources.clear(); buildings.clear(); trainQueue.clear(); projectiles.clear(); selected.clear();
        nextId = 1; nextBuildingId = 1; populationCap = 40; barracksCount = 1;
        food = 660f; wood = 650f; gold = 430f; aiAccumulator = 0f;
        gameOver = false; endText = ""; pendingBuild = null; commandMarkerTime = 0f;
        playerStronghold = new Stronghold(PLAYER, -25f, -18f);
        enemyStronghold = new Stronghold(ENEMY, 25f, 18f);
        cameraTarget.set(-17f, 0f, -11f);
        cameraDistance = 34f; cameraYaw = 43f; cameraPitch = 52f;

        createResourceNodes();
        List<Unit> villagers = new ArrayList<>();
        for (int i = 0; i < 7; i++) villagers.add(spawn(PLAYER, UnitType.VILLAGER, -27f + i * 1.05f, -11.8f + (i & 1) * 1.0f));
        for (int i = 0; i < 6; i++) spawn(PLAYER, UnitType.SWORD, -19f + i * 1.05f, -13.4f + (i & 1) * 1.0f);
        for (int i = 0; i < 4; i++) spawn(PLAYER, UnitType.ARCHER, -19f + i * 1.15f, -9.8f);
        spawn(PLAYER, UnitType.CAVALRY, -21f, -6.8f);
        spawn(PLAYER, UnitType.CAVALRY, -19.4f, -6.3f);

        if (resources.size() >= 3) {
            for (int i = 0; i < villagers.size(); i++) assignGather(villagers.get(i), resources.get(i < 3 ? 0 : i < 5 ? 1 : 2));
        }

        int extra = difficulty * 2;
        for (int i = 0; i < 8 + extra; i++) spawn(ENEMY, UnitType.SWORD, 18.5f + (i % 5) * 1.1f, 12.5f + (i / 5) * 1.1f);
        for (int i = 0; i < 5 + difficulty; i++) spawn(ENEMY, UnitType.ARCHER, 17.5f + i * 1.1f, 17.4f);
        for (int i = 0; i < 2 + difficulty; i++) spawn(ENEMY, UnitType.CAVALRY, 24.5f + i * 1.45f, 10.8f);
        updateCamera();
    }

    private void createResourceNodes() {
        addResource(ResourceType.FOOD, -31f, -7f, 760f);
        addResource(ResourceType.WOOD, -14f, -22f, 950f);
        addResource(ResourceType.GOLD, -12f, -8f, 700f);
        addResource(ResourceType.FOOD, 31f, 7f, 760f);
        addResource(ResourceType.WOOD, 14f, 22f, 950f);
        addResource(ResourceType.GOLD, 12f, 8f, 700f);
        addResource(ResourceType.FOOD, -3f, 16f, 900f);
        addResource(ResourceType.GOLD, 16f, -14f, 850f);
    }

    private void addResource(ResourceType type, float x, float z, float amount) {
        ResourceNode n = new ResourceNode(type, x, z, amount);
        if (type == ResourceType.FOOD) {
            n.primary = new ModelInstance(foodNodeModel); n.detail = new ModelInstance(foodDetailModel);
        } else if (type == ResourceType.WOOD) {
            n.primary = new ModelInstance(woodNodeModel); n.detail = new ModelInstance(woodDetailModel);
        } else {
            n.primary = new ModelInstance(goldNodeModel); n.detail = new ModelInstance(goldDetailModel);
        }
        updateResourceTransform(n);
        resources.add(n);
    }

    private Unit spawn(int team, UnitType type, float x, float z) {
        Unit u = new Unit(nextId++, team, type, x, z);
        Model torso = type == UnitType.CAVALRY
            ? (team == PLAYER ? playerCavalryBody : enemyCavalryBody)
            : (team == PLAYER ? playerBody : enemyBody);
        u.body = new ModelInstance(torso);
        u.head = new ModelInstance(headModel);
        u.shadow = new ModelInstance(shadowModel);
        switch (type) {
            case VILLAGER -> {
                u.weapon = new ModelInstance(toolModel);
                u.helmet = new ModelInstance(villagerHatModel);
            }
            case SWORD -> {
                u.weapon = new ModelInstance(swordModel);
                u.offhand = new ModelInstance(team == PLAYER ? blueShieldModel : redShieldModel);
                u.helmet = new ModelInstance(helmetModel);
            }
            case ARCHER -> {
                u.weapon = new ModelInstance(bowModel);
                u.helmet = new ModelInstance(hoodModel);
            }
            case CAVALRY -> {
                u.weapon = new ModelInstance(swordModel);
                u.offhand = new ModelInstance(team == PLAYER ? blueShieldModel : redShieldModel);
                u.helmet = new ModelInstance(helmetModel);
                u.mount = new ModelInstance(horseModel);
                u.mountHead = new ModelInstance(horseHeadModel);
            }
        }
        updateUnitTransform(u, 0f);
        units.add(u);
        return u;
    }

    private void updateUnitTransform(Unit u, float dt) {
        u.anim += dt * (u.moving ? 8.0f : 2.0f);
        float bob = u.moving ? (float)Math.sin(u.anim) * 0.07f : (float)Math.sin(u.anim) * 0.015f;
        float swing = u.cooldown > u.attackPeriod * 0.55f ? 38f : 0f;
        float yaw = u.facingYaw;

        if (u.type == UnitType.CAVALRY) {
            u.mount.transform.setToTranslation(u.pos.x, 0.78f + bob * 0.35f, u.pos.z).rotate(Vector3.Y, yaw);
            u.mountHead.transform.setToTranslation(u.pos.x + (float)Math.sin(yaw * DEG) * 0.72f, 1.13f + bob * 0.35f,
                u.pos.z + (float)Math.cos(yaw * DEG) * 0.72f).rotate(Vector3.Y, yaw);
            u.body.transform.setToTranslation(u.pos.x, 1.74f + bob, u.pos.z).rotate(Vector3.Y, yaw);
            u.head.transform.setToTranslation(u.pos.x, 2.66f + bob, u.pos.z);
            u.helmet.transform.setToTranslation(u.pos.x, 2.88f + bob, u.pos.z);
            u.weapon.transform.setToTranslation(u.pos.x + 0.48f, 1.90f + bob, u.pos.z).rotate(Vector3.Z, -28f - swing);
            u.offhand.transform.setToTranslation(u.pos.x - 0.46f, 1.72f + bob, u.pos.z).rotate(Vector3.X, 90f);
            u.shadow.transform.setToTranslation(u.pos.x, 0.035f, u.pos.z).scale(1.55f, 1f, 1.20f);
            return;
        }

        u.body.transform.setToTranslation(u.pos.x, 0.97f + bob, u.pos.z).rotate(Vector3.Y, yaw);
        u.head.transform.setToTranslation(u.pos.x, 1.88f + bob, u.pos.z);
        float helmetY = u.type == UnitType.ARCHER ? 2.18f : u.type == UnitType.VILLAGER ? 2.15f : 2.10f;
        u.helmet.transform.setToTranslation(u.pos.x, helmetY + bob, u.pos.z);
        u.shadow.transform.setToTranslation(u.pos.x, 0.035f, u.pos.z);
        float rot = u.type == UnitType.SWORD ? -30f - swing : u.type == UnitType.ARCHER ? 8f : 32f - swing * 0.6f;
        u.weapon.transform.setToTranslation(u.pos.x + 0.44f, 1.16f + bob, u.pos.z).rotate(Vector3.Z, rot);
        if (u.offhand != null) u.offhand.transform.setToTranslation(u.pos.x - 0.44f, 1.08f + bob, u.pos.z).rotate(Vector3.X, 90f);
    }

    private void updateResourceTransform(ResourceNode n) {
        float ratio = Math.max(0.18f, n.amount / n.maxAmount);
        float s = 0.68f + ratio * 0.36f;
        if (n.type == ResourceType.WOOD) {
            n.primary.transform.setToTranslation(n.pos.x, 1.08f, n.pos.z).scale(s, s, s);
            n.detail.transform.setToTranslation(n.pos.x, 3.62f, n.pos.z).scale(s, s, s);
        } else if (n.type == ResourceType.FOOD) {
            n.primary.transform.setToTranslation(n.pos.x, 0.62f, n.pos.z).scale(s, s, s);
            n.detail.transform.setToTranslation(n.pos.x + 0.70f, 0.70f, n.pos.z - 0.35f).scale(s, s, s);
        } else {
            n.primary.transform.setToTranslation(n.pos.x, 0.65f, n.pos.z).scale(s, s, s);
            n.detail.transform.setToTranslation(n.pos.x + 0.55f, 1.05f, n.pos.z - 0.30f).scale(s, s, s);
        }
    }

    private Building createBuilding(BuildingType type, float x, float z) {
        Building b = new Building(nextBuildingId++, PLAYER, type, x, z);
        if (type == BuildingType.HOUSE) {
            b.body = new ModelInstance(houseBodyModel); b.roof = new ModelInstance(houseRoofModel); b.detail = new ModelInstance(houseDetailModel);
            populationCap = Math.min(90, populationCap + 10);
        } else if (type == BuildingType.BARRACKS) {
            b.body = new ModelInstance(barracksBodyModel); b.roof = new ModelInstance(barracksRoofModel); b.detail = new ModelInstance(barracksDetailModel);
            barracksCount++;
        } else {
            b.body = new ModelInstance(towerBodyModel); b.roof = new ModelInstance(towerRoofModel); b.detail = new ModelInstance(towerDetailModel);
        }
        updateBuildingTransform(b);
        buildings.add(b);
        return b;
    }

    private void updateBuildingTransform(Building b) {
        if (b.type == BuildingType.HOUSE) {
            b.body.transform.setToTranslation(b.pos.x, 1.20f, b.pos.z);
            b.roof.transform.setToTranslation(b.pos.x, 3.46f, b.pos.z).rotate(Vector3.Y, 45f);
            b.detail.transform.setToTranslation(b.pos.x, 1.15f, b.pos.z - 1.78f);
        } else if (b.type == BuildingType.BARRACKS) {
            b.body.transform.setToTranslation(b.pos.x, 1.38f, b.pos.z);
            b.roof.transform.setToTranslation(b.pos.x, 3.83f, b.pos.z).rotate(Vector3.Y, 45f);
            b.detail.transform.setToTranslation(b.pos.x, 1.15f, b.pos.z - 2.25f);
        } else {
            b.body.transform.setToTranslation(b.pos.x, 3.10f, b.pos.z);
            b.roof.transform.setToTranslation(b.pos.x, 7.48f, b.pos.z);
            b.detail.transform.setToTranslation(b.pos.x, 3.20f, b.pos.z - 1.46f);
        }
    }

    @Override
    public void render() {
        float rawDt = Math.max(0.0001f, Gdx.graphics.getDeltaTime());
        fpsSmooth = fpsSmooth * 0.94f + (1f / rawDt) * 0.06f;
        float dt = Math.min(MAX_DT, rawDt);
        commandMarkerTime = Math.max(0f, commandMarkerTime - dt);
        updateAdaptiveQuality(dt);

        if (mode == Mode.GAME && !gameOver) updateGame(dt);
        else if (mode == Mode.MENU) {
            menuOrbit += dt * 1.2f;
            cameraYaw = 42f + (float)Math.sin(menuOrbit * 0.20f) * 4f;
        }
        updateCamera();

        ScreenUtils.clear(0.54f, 0.63f, 0.61f, 1f, true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);

        modelBatch.begin(camera);
        renderVisibleChunks();
        renderTransparentWorld();
        renderDynamicWorld();
        modelBatch.end();

        drawWorldFeedback();
        drawUi();
    }

    private void updateAdaptiveQuality(float dt) {
        if (fpsSmooth < 40f) {
            lowFpsTimer += dt; highFpsTimer = 0f;
            if (lowFpsTimer > 2.0f) lowQuality = true;
        } else if (fpsSmooth > 55f) {
            highFpsTimer += dt; lowFpsTimer = 0f;
            if (highFpsTimer > 4.0f) lowQuality = false;
        } else {
            lowFpsTimer = Math.max(0f, lowFpsTimer - dt * 0.5f);
            highFpsTimer = Math.max(0f, highFpsTimer - dt * 0.5f);
        }
    }

    private void renderVisibleChunks() {
        for (Chunk c : chunks) if (c.cache != null && camera.frustum.sphereInFrustum(c.center, c.radius)) modelBatch.render(c.cache, environment);
    }

    private void renderTransparentWorld() {
        for (StaticPiece p : transparentPieces) if (camera.frustum.sphereInFrustum(p.x, 0f, p.z, 7f)) modelBatch.render(p.instance, environment);
    }

    private void renderDynamicWorld() {
        for (ResourceNode n : resources) {
            if (n.amount <= 0f || !camera.frustum.sphereInFrustum(n.pos.x, 1.5f, n.pos.z, 3.2f)) continue;
            modelBatch.render(n.primary, environment);
            if (!lowQuality && camera.position.dst2(n.pos.x, 1f, n.pos.z) < 1800f) modelBatch.render(n.detail, environment);
        }
        for (Building b : buildings) {
            if (b.hp <= 0f || !camera.frustum.sphereInFrustum(b.pos.x, 2.5f, b.pos.z, 5f)) continue;
            modelBatch.render(b.body, environment);
            modelBatch.render(b.roof, environment);
            if (!lowQuality) modelBatch.render(b.detail, environment);
        }
        for (Unit u : units) renderUnit3d(u);
        for (Projectile p : projectiles) modelBatch.render(p.model, environment);
    }

    private void renderUnit3d(Unit u) {
        if (!camera.frustum.sphereInFrustum(u.pos.x, 1.5f, u.pos.z, 2.4f)) return;
        float d2 = camera.position.dst2(u.pos.x, 1.4f, u.pos.z);
        boolean detail = !lowQuality && d2 < 1700f;
        if (detail && d2 < 1200f) modelBatch.render(u.shadow, environment);
        if (u.mount != null) {
            modelBatch.render(u.mount, environment);
            if (detail) modelBatch.render(u.mountHead, environment);
        }
        modelBatch.render(u.body, environment);
        if (detail) {
            modelBatch.render(u.head, environment);
            modelBatch.render(u.helmet, environment);
            modelBatch.render(u.weapon, environment);
            if (u.offhand != null) modelBatch.render(u.offhand, environment);
        }
    }

    private void updateGame(float dt) {
        updateTraining(dt);
        updateStrongholds(dt);
        updateBuildings(dt);
        updateProjectiles(dt);

        for (Unit u : units) {
            if (u.hp <= 0f) continue;
            u.cooldown -= dt;
            u.thinkTimer -= dt;
            if (u.type == UnitType.VILLAGER && u.team == PLAYER && u.gatherNode != null) {
                updateGatherer(u, dt);
                updateUnitTransform(u, dt);
                continue;
            }
            Unit enemy = findById(u.targetUnitId);
            if (enemy != null && enemy.hp > 0f && enemy.team != u.team) attackUnitTarget(u, enemy, dt);
            else if (u.targetStronghold >= 0) {
                Stronghold target = stronghold(u.targetStronghold);
                if (target != null && target.hp > 0f && target.team != u.team) attackStrongholdTarget(u, target, dt);
                else clearTargets(u);
            } else if (u.moving) moveToward(u, u.target.x, u.target.z, dt);
            else if (u.thinkTimer <= 0f) {
                u.thinkTimer = 0.45f + (u.id % 5) * 0.09f;
                Unit near = nearestEnemy(u, 5.5f);
                if (near != null) u.targetUnitId = near.id;
            }
            updateUnitTransform(u, dt);
        }

        units.removeIf(u -> u.hp <= 0f);
        selected.removeIf(id -> findById(id) == null);
        buildings.removeIf(b -> b.hp <= 0f);
        separateUnits();
        updateAi(dt);
        checkEnd();
    }

    private void updateTraining(float dt) {
        if (trainQueue.isEmpty()) return;
        TrainOrder o = trainQueue.get(0);
        o.remaining -= dt;
        if (o.remaining <= 0f) {
            spawn(PLAYER, o.type, -22.5f + rng.nextFloat() * 4.5f, -12.5f + rng.nextFloat() * 3.5f);
            trainQueue.remove(0);
        }
    }

    private void updateStrongholds(float dt) {
        if (playerStronghold != null && playerStronghold.hp > 0f) strongholdDefense(playerStronghold, dt);
        if (enemyStronghold != null && enemyStronghold.hp > 0f) strongholdDefense(enemyStronghold, dt);
    }

    private void strongholdDefense(Stronghold base, float dt) {
        base.cooldown -= dt;
        if (base.cooldown > 0f) return;
        Unit target = nearestUnitTo(base.pos.x, base.pos.z, base.team == PLAYER ? ENEMY : PLAYER, 11f);
        if (target != null) {
            target.hp -= 18f;
            base.cooldown = 1.32f;
            spawnProjectile(base.pos.x, 5.6f, base.pos.z, target.pos.x, 1.3f, target.pos.z);
        }
    }

    private void updateBuildings(float dt) {
        for (Building b : buildings) {
            if (b.type != BuildingType.TOWER || b.hp <= 0f) continue;
            b.cooldown -= dt;
            if (b.cooldown > 0f) continue;
            Unit target = nearestUnitTo(b.pos.x, b.pos.z, b.team == PLAYER ? ENEMY : PLAYER, 9.2f);
            if (target != null) {
                target.hp -= 16f;
                b.cooldown = 1.15f;
                spawnProjectile(b.pos.x, 5.3f, b.pos.z, target.pos.x, 1.25f, target.pos.z);
            }
        }
    }

    private void updateProjectiles(float dt) {
        Iterator<Projectile> it = projectiles.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();
            p.life -= dt;
            if (p.life <= 0f) { it.remove(); continue; }
            float t = 1f - p.life / p.duration;
            float x = p.start.x + (p.end.x - p.start.x) * t;
            float z = p.start.z + (p.end.z - p.start.z) * t;
            float y = p.start.y + (p.end.y - p.start.y) * t + (float)Math.sin(t * Math.PI) * 1.5f;
            p.model.transform.setToTranslation(x, y, z);
        }
    }

    private void spawnProjectile(float sx, float sy, float sz, float ex, float ey, float ez) {
        if (projectiles.size() > (lowQuality ? 10 : 28)) return;
        ModelInstance i = new ModelInstance(projectileModel);
        Vector3 start = new Vector3(sx, sy, sz);
        Vector3 end = new Vector3(ex, ey, ez);
        i.transform.setToTranslation(start);
        projectiles.add(new Projectile(i, start, end, 0.44f));
    }

    private void updateGatherer(Unit u, float dt) {
        ResourceNode n = u.gatherNode;
        if (n == null || n.amount <= 0f) {
            if (u.carried > 0f) u.gatherState = 2;
            else { u.gatherNode = null; u.gatherState = 0; return; }
        }
        if (u.gatherState == 0) {
            if (n == null) return;
            float d = Vector2.dst(u.pos.x, u.pos.z, n.pos.x, n.pos.z);
            if (d > 1.6f) moveToward(u, n.pos.x, n.pos.z, dt);
            else { u.moving = false; u.gatherState = 1; }
        } else if (u.gatherState == 1) {
            u.gatherTimer += dt;
            if (u.gatherTimer >= 0.46f) {
                u.gatherTimer = 0f;
                float take = Math.min(1.7f, n.amount);
                n.amount -= take;
                u.carried += take;
                updateResourceTransform(n);
                if (u.carried >= 14f || n.amount <= 0f) u.gatherState = 2;
            }
        } else {
            float d = Vector2.dst(u.pos.x, u.pos.z, playerStronghold.pos.x, playerStronghold.pos.z);
            if (d > 5.1f) moveToward(u, playerStronghold.pos.x, playerStronghold.pos.z, dt);
            else {
                if (n != null) {
                    if (n.type == ResourceType.FOOD) food += u.carried;
                    else if (n.type == ResourceType.WOOD) wood += u.carried;
                    else gold += u.carried;
                }
                u.carried = 0f;
                if (n != null && n.amount > 0f) u.gatherState = 0;
                else { u.gatherNode = null; u.gatherState = 0; }
            }
        }
    }

    private void assignGather(Unit u, ResourceNode n) {
        if (u.type != UnitType.VILLAGER || u.team != PLAYER) return;
        clearTargets(u);
        u.gatherNode = n; u.gatherState = 0; u.gatherTimer = 0f; u.moving = false;
    }

    private void attackUnitTarget(Unit u, Unit enemy, float dt) {
        float d = Vector2.dst(u.pos.x, u.pos.z, enemy.pos.x, enemy.pos.z);
        if (d <= u.range) {
            u.moving = false; faceToward(u, enemy.pos.x, enemy.pos.z);
            if (u.cooldown <= 0f) {
                enemy.hp -= u.damage; u.cooldown = u.attackPeriod;
                if (u.type == UnitType.ARCHER) spawnProjectile(u.pos.x, 1.9f, u.pos.z, enemy.pos.x, 1.3f, enemy.pos.z);
            }
        } else moveToward(u, enemy.pos.x, enemy.pos.z, dt);
    }

    private void attackStrongholdTarget(Unit u, Stronghold base, float dt) {
        float d = Vector2.dst(u.pos.x, u.pos.z, base.pos.x, base.pos.z);
        float r = u.type == UnitType.ARCHER ? 8.2f : 4.9f;
        if (d <= r) {
            u.moving = false; faceToward(u, base.pos.x, base.pos.z);
            if (u.cooldown <= 0f) {
                base.hp = Math.max(0f, base.hp - u.damage * 0.72f);
                u.cooldown = u.attackPeriod;
                if (u.type == UnitType.ARCHER) spawnProjectile(u.pos.x, 1.9f, u.pos.z, base.pos.x, 3.2f, base.pos.z);
            }
        } else moveToward(u, base.pos.x, base.pos.z, dt);
    }

    private void clearTargets(Unit u) {
        u.targetUnitId = -1; u.targetStronghold = -1;
    }

    private void moveToward(Unit u, float x, float z, float dt) {
        float dx = x - u.pos.x, dz = z - u.pos.z;
        float d = (float)Math.sqrt(dx * dx + dz * dz);
        if (d < 0.12f) { u.moving = false; return; }
        faceToward(u, x, z);
        u.moving = true;
        float step = Math.min(d, u.speed * dt);
        u.pos.x += dx / d * step; u.pos.z += dz / d * step;
        u.pos.x = clamp(u.pos.x, -WORLD, WORLD); u.pos.z = clamp(u.pos.z, -WORLD, WORLD);
    }

    private void faceToward(Unit u, float x, float z) {
        u.facingYaw = (float)Math.toDegrees(Math.atan2(x - u.pos.x, z - u.pos.z));
    }

    private void separateUnits() {
        for (int i = 0; i < units.size(); i++) {
            Unit a = units.get(i);
            for (int j = i + 1; j < units.size(); j++) {
                Unit b = units.get(j);
                float dx = b.pos.x - a.pos.x, dz = b.pos.z - a.pos.z;
                float d2 = dx * dx + dz * dz;
                if (d2 < 0.04f || d2 > 0.82f) continue;
                float d = (float)Math.sqrt(d2);
                float push = (0.90f - d) * 0.038f;
                if (push <= 0f) continue;
                a.pos.x -= dx / d * push; a.pos.z -= dz / d * push;
                b.pos.x += dx / d * push; b.pos.z += dz / d * push;
            }
        }
    }

    private void updateAi(float dt) {
        aiAccumulator += dt;
        float interval = difficulty == 0 ? 4.9f : difficulty == 1 ? 3.15f : 2.0f;
        if (aiAccumulator < interval) return;
        aiAccumulator = 0f;
        int enemyCount = countTeam(ENEMY);
        int limit = 26 + difficulty * 11;
        if (enemyCount < limit && enemyStronghold != null && enemyStronghold.hp > 0f) {
            int r = rng.nextInt(100);
            UnitType type = r < 41 ? UnitType.SWORD : r < 72 ? UnitType.ARCHER : UnitType.CAVALRY;
            spawn(ENEMY, type, 20f + rng.nextFloat() * 7f, 12f + rng.nextFloat() * 8f);
        }
        float aggression = difficulty == 0 ? 0.32f : difficulty == 1 ? 0.68f : 0.95f;
        for (Unit u : units) {
            if (u.team != ENEMY || rng.nextFloat() > aggression) continue;
            Unit nearest = nearestEnemyForTeam(u, PLAYER, 18f);
            if (nearest != null) { u.targetUnitId = nearest.id; u.targetStronghold = -1; }
            else { u.targetUnitId = -1; u.targetStronghold = PLAYER; }
        }
    }

    private Unit nearestEnemy(Unit from, float maxDistance) {
        return nearestEnemyForTeam(from, from.team == PLAYER ? ENEMY : PLAYER, maxDistance);
    }

    private Unit nearestEnemyForTeam(Unit from, int team, float maxDistance) {
        Unit nearest = null; float best = maxDistance * maxDistance;
        for (Unit u : units) {
            if (u.team != team || u.hp <= 0f) continue;
            float d2 = Vector2.dst2(from.pos.x, from.pos.z, u.pos.x, u.pos.z);
            if (d2 < best) { best = d2; nearest = u; }
        }
        return nearest;
    }

    private Unit nearestUnitTo(float x, float z, int team, float maxDistance) {
        Unit nearest = null; float best = maxDistance * maxDistance;
        for (Unit u : units) {
            if (u.team != team || u.hp <= 0f) continue;
            float d2 = Vector2.dst2(x, z, u.pos.x, u.pos.z);
            if (d2 < best) { best = d2; nearest = u; }
        }
        return nearest;
    }

    private void checkEnd() {
        if (playerStronghold != null && playerStronghold.hp <= 0f) { gameOver = true; endText = "DERROTA"; }
        else if (enemyStronghold != null && enemyStronghold.hp <= 0f) { gameOver = true; endText = "VITORIA"; }
    }

    private Stronghold stronghold(int team) { return team == PLAYER ? playerStronghold : enemyStronghold; }

    private int countTeam(int team) {
        int n = 0; for (Unit u : units) if (u.team == team) n++; return n;
    }

    private Unit findById(int id) {
        if (id < 0) return null;
        for (Unit u : units) if (u.id == id) return u;
        return null;
    }

    private void updateCamera() {
        float yaw = cameraYaw * DEG, pitch = cameraPitch * DEG;
        float horizontal = cameraDistance * (float)Math.cos(pitch);
        camera.position.set(cameraTarget.x + (float)Math.sin(yaw) * horizontal,
            cameraTarget.y + cameraDistance * (float)Math.sin(pitch),
            cameraTarget.z + (float)Math.cos(yaw) * horizontal);
        camera.up.set(Vector3.Y);
        camera.lookAt(cameraTarget);
        camera.viewportWidth = Gdx.graphics.getWidth(); camera.viewportHeight = Gdx.graphics.getHeight();
        camera.update();
    }

    private void drawWorldFeedback() {
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.98f, 0.82f, 0.24f, 1f);
        for (Integer id : selected) {
            Unit u = findById(id);
            if (u != null) drawGroundRing(u.pos.x, u.pos.z, u.type == UnitType.CAVALRY ? 1.08f : 0.74f, lowQuality ? 14 : 24);
        }
        if (commandMarkerTime > 0f) {
            float pulse = 0.58f + (1f - commandMarkerTime) * 0.55f;
            shapes.setColor(0.40f, 0.82f, 1f, Math.min(1f, commandMarkerTime * 1.8f));
            drawGroundRing(commandMarker.x, commandMarker.z, pulse, lowQuality ? 16 : 26);
        }
        shapes.end();
    }

    private void drawGroundRing(float x, float z, float r, int segments) {
        for (int i = 0; i < segments; i++) {
            float a0 = i * 6.2831853f / segments, a1 = (i + 1) * 6.2831853f / segments;
            shapes.line(x + (float)Math.cos(a0) * r, 0.07f, z + (float)Math.sin(a0) * r,
                x + (float)Math.cos(a1) * r, 0.07f, z + (float)Math.sin(a1) * r);
        }
    }

    private void drawUi() {
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        uiMatrix.setToOrtho2D(0f, 0f, w, h);
        float scale = Math.max(0.90f, Math.min(1.45f, h / 720f));
        font.getData().setScale(scale);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(uiMatrix);
        if (mode == Mode.MENU) drawMenu(w, h); else drawGameHud(w, h);
    }

    private void drawMenu(int w, int h) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.01f, 0.015f, 0.012f, 0.50f); shapes.rect(0, 0, w, h);
        float panelW = Math.min(w * 0.66f, 850f), panelH = Math.min(h * 0.67f, 555f);
        float px = (w - panelW) * 0.5f, py = (h - panelH) * 0.5f;
        shapes.setColor(0.033f, 0.045f, 0.038f, 0.95f); shapes.rect(px, py, panelW, panelH);
        shapes.setColor(0.73f, 0.57f, 0.24f, 0.96f); shapes.rect(px, py + panelH - 5f, panelW, 5f);
        float bw = panelW * 0.27f, bh = Math.max(52f, panelH * 0.12f), gap = panelW * 0.035f;
        float by = py + panelH * 0.36f, start = px + (panelW - bw * 3f - gap * 2f) * 0.5f;
        easyButton.set(start, by, bw, bh); normalButton.set(start + bw + gap, by, bw, bh); hardButton.set(start + (bw + gap) * 2f, by, bw, bh);
        drawButtonFill(easyButton, difficulty == 0); drawButtonFill(normalButton, difficulty == 1); drawButtonFill(hardButton, difficulty == 2);
        playButton.set(px + panelW * 0.28f, py + panelH * 0.13f, panelW * 0.44f, bh * 1.08f);
        shapes.setColor(0.80f, 0.62f, 0.24f, 0.98f); shapes.rect(playButton.x, playButton.y, playButton.width, playButton.height);
        shapes.end();

        sprites.setProjectionMatrix(uiMatrix); sprites.begin();
        font.setColor(0.97f, 0.86f, 0.58f, 1f);
        float old = font.getData().scaleX; font.getData().setScale(old * 2.08f);
        font.draw(sprites, "IMPERIUM VALE", px, py + panelH * 0.83f, panelW, Align.center, false);
        font.getData().setScale(old); font.setColor(0.84f, 0.86f, 0.78f, 1f);
        font.draw(sprites, "Construa. Comande. Conquiste.", px, py + panelH * 0.69f, panelW, Align.center, false);
        font.draw(sprites, "FACIL", easyButton.x, easyButton.y + easyButton.height * 0.62f, easyButton.width, Align.center, false);
        font.draw(sprites, "NORMAL", normalButton.x, normalButton.y + normalButton.height * 0.62f, normalButton.width, Align.center, false);
        font.draw(sprites, "DIFICIL", hardButton.x, hardButton.y + hardButton.height * 0.62f, hardButton.width, Align.center, false);
        font.setColor(0.07f, 0.06f, 0.035f, 1f); font.draw(sprites, "JOGAR", playButton.x, playButton.y + playButton.height * 0.63f, playButton.width, Align.center, false);
        font.setColor(0.56f, 0.61f, 0.55f, 1f); font.getData().setScale(old * 0.76f);
        font.draw(sprites, VERSION, px, py + 24f, panelW, Align.center, false); font.getData().setScale(old);
        sprites.end();
    }

    private void drawButtonFill(Rectangle r, boolean active) {
        shapes.setColor(active ? 0.68f : 0.12f, active ? 0.53f : 0.16f, active ? 0.25f : 0.13f, 0.98f);
        shapes.rect(r.x, r.y, r.width, r.height);
    }

    private void drawGameHud(int w, int h) {
        float top = Math.max(58f, h * 0.092f), bottom = Math.max(82f, h * 0.142f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.015f, 0.023f, 0.019f, 0.93f); shapes.rect(0, h - top, w, top); shapes.rect(0, 0, w, bottom);
        shapes.setColor(0.66f, 0.54f, 0.28f, 0.76f); shapes.rect(0, h - top, w, 2.5f); shapes.rect(0, bottom - 2.5f, w, 2.5f);
        float bw = Math.min(176f, w * 0.162f), bh = bottom * 0.70f, gap = 9f;
        float total = bw * 4f + gap * 3f, x = w - total - 16f, y = (bottom - bh) * 0.5f;
        villagerButton.set(x, y, bw, bh); x += bw + gap;
        swordButton.set(x, y, bw, bh); x += bw + gap;
        archerButton.set(x, y, bw, bh); x += bw + gap;
        cavalryButton.set(x, y, bw, bh);
        drawButtonFill(villagerButton, false); drawButtonFill(swordButton, false); drawButtonFill(archerButton, false); drawButtonFill(cavalryButton, false);

        float mapW = Math.min(210f, w * 0.185f), mapH = bottom * 0.76f, mapY = (bottom - mapH) * 0.5f;
        shapes.setColor(0.052f, 0.078f, 0.052f, 0.98f); shapes.rect(10f, mapY, mapW, mapH);
        for (ResourceNode n : resources) {
            if (n.amount <= 0f) continue;
            float mx = 10f + ((n.pos.x + WORLD) / (WORLD * 2f)) * mapW;
            float my = mapY + ((n.pos.z + WORLD) / (WORLD * 2f)) * mapH;
            if (n.type == ResourceType.GOLD) shapes.setColor(0.95f, 0.70f, 0.12f, 1f);
            else if (n.type == ResourceType.WOOD) shapes.setColor(0.30f, 0.62f, 0.20f, 1f);
            else shapes.setColor(0.78f, 0.30f, 0.28f, 1f);
            shapes.circle(mx, my, 2.1f, 7);
        }
        for (Unit u : units) {
            float mx = 10f + ((u.pos.x + WORLD) / (WORLD * 2f)) * mapW;
            float my = mapY + ((u.pos.z + WORLD) / (WORLD * 2f)) * mapH;
            if (u.team == PLAYER) shapes.setColor(0.18f, 0.48f, 0.96f, 1f); else shapes.setColor(0.92f, 0.20f, 0.12f, 1f);
            shapes.circle(mx, my, 2.4f, 7);
        }
        shapes.end();

        if (hasSelectedVillager()) drawBuildButtons(w, h, bottom);
        drawHealthBars(w, h);

        sprites.setProjectionMatrix(uiMatrix); sprites.begin();
        font.setColor(0.95f, 0.90f, 0.76f, 1f);
        font.draw(sprites, "COMIDA " + (int)food + "    MADEIRA " + (int)wood + "    OURO " + (int)gold + "    POP " + countTeam(PLAYER) + "/" + populationCap,
            18f, h - top * 0.40f);
        font.setColor(0.76f, 0.82f, 0.74f, 1f);
        String base = playerStronghold == null ? "" : "FORTALEZA " + (int)playerStronghold.hp + "/" + (int)playerStronghold.maxHp;
        font.draw(sprites, base, w * 0.50f - 190f, h - top * 0.40f, 380f, Align.center, false);
        font.draw(sprites, Math.round(Math.min(99f, fpsSmooth)) + " FPS  " + (lowQuality ? "LOD" : "HD"), w - 190f, h - top * 0.40f);
        drawHudLabel("ALDEAO", "50 C", villagerButton); drawHudLabel("ESPADA", "60 C 20 O", swordButton);
        drawHudLabel("ARQUEIRO", "45 M 30 O", archerButton); drawHudLabel("CAVALO", "90 C 60 O", cavalryButton);
        if (!trainQueue.isEmpty()) {
            font.setColor(0.83f, 0.77f, 0.60f, 1f);
            font.draw(sprites, "FILA " + trainQueue.size() + "  " + Math.max(0, (int)Math.ceil(trainQueue.get(0).remaining)) + "s", mapW + 24f, 28f);
        }
        if (pendingBuild != null) {
            font.setColor(0.62f, 0.90f, 0.62f, 1f);
            font.draw(sprites, "TOQUE NO TERRENO PARA CONSTRUIR " + pendingBuild.name(), 0, bottom + 28f, w, Align.center, false);
        }
        font.setColor(0.48f, 0.53f, 0.46f, 1f); float old = font.getData().scaleX; font.getData().setScale(old * 0.70f);
        font.draw(sprites, VERSION, 12f, bottom + 18f); font.getData().setScale(old);
        if (gameOver) {
            font.setColor(endText.equals("VITORIA") ? new Color(0.95f, 0.78f, 0.28f, 1f) : new Color(0.95f, 0.28f, 0.22f, 1f));
            float s = font.getData().scaleX; font.getData().setScale(s * 2.2f);
            font.draw(sprites, endText, 0, h * 0.57f, w, Align.center, false); font.getData().setScale(s);
            font.setColor(Color.WHITE); font.draw(sprites, "Toque para voltar ao menu", 0, h * 0.48f, w, Align.center, false);
        }
        sprites.end();
    }

    private void drawBuildButtons(int w, int h, float bottom) {
        float bw = Math.min(150f, w * 0.13f), bh = Math.max(48f, h * 0.07f), x = 16f, y = bottom + 18f;
        houseButton.set(x, y, bw, bh); barracksButton.set(x + bw + 8f, y, bw, bh); towerButton.set(x + (bw + 8f) * 2f, y, bw, bh);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawButtonFill(houseButton, pendingBuild == BuildingType.HOUSE); drawButtonFill(barracksButton, pendingBuild == BuildingType.BARRACKS);
        drawButtonFill(towerButton, pendingBuild == BuildingType.TOWER); shapes.end();
        sprites.setProjectionMatrix(uiMatrix); sprites.begin(); font.setColor(0.90f, 0.84f, 0.70f, 1f);
        font.draw(sprites, "CASA 100M", houseButton.x, houseButton.y + bh * 0.60f, bw, Align.center, false);
        font.draw(sprites, "QUARTEL 150M", barracksButton.x, barracksButton.y + bh * 0.60f, bw, Align.center, false);
        font.draw(sprites, "TORRE 120M 80O", towerButton.x, towerButton.y + bh * 0.60f, bw, Align.center, false); sprites.end();
    }

    private void drawHealthBars(int w, int h) {
        shapes.setProjectionMatrix(uiMatrix); shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Unit u : units) {
            if (u.hp >= u.maxHp && !selected.contains(u.id)) continue;
            tmpScreen.set(u.pos.x, u.type == UnitType.CAVALRY ? 3.25f : 2.55f, u.pos.z); camera.project(tmpScreen);
            float sx = tmpScreen.x, sy = tmpScreen.y;
            if (sx < -40f || sx > w + 40f || sy < 0f || sy > h) continue;
            float barW = u.type == UnitType.CAVALRY ? 48f : 40f;
            shapes.setColor(0.10f, 0.06f, 0.06f, 0.88f); shapes.rect(sx - barW * 0.5f, sy, barW, 5f);
            float r = Math.max(0f, u.hp / u.maxHp);
            shapes.setColor(r > 0.55f ? 0.28f : 0.88f, r > 0.55f ? 0.72f : 0.30f, 0.20f, 0.96f);
            shapes.rect(sx - barW * 0.5f, sy, barW * r, 5f);
        }
        drawStrongholdBar(playerStronghold, w, h); drawStrongholdBar(enemyStronghold, w, h); shapes.end();
    }

    private void drawStrongholdBar(Stronghold s, int w, int h) {
        if (s == null) return;
        tmpScreen.set(s.pos.x, 9.2f, s.pos.z); camera.project(tmpScreen);
        float sx = tmpScreen.x, sy = tmpScreen.y;
        if (sx < -110f || sx > w + 110f || sy < 0f || sy > h) return;
        float bw = 104f; shapes.setColor(0.09f, 0.055f, 0.05f, 0.92f); shapes.rect(sx - bw * 0.5f, sy, bw, 8f);
        float r = Math.max(0f, s.hp / s.maxHp);
        if (s.team == PLAYER) shapes.setColor(0.20f, 0.55f, 0.95f, 0.98f); else shapes.setColor(0.92f, 0.20f, 0.12f, 0.98f);
        shapes.rect(sx - bw * 0.5f, sy, bw * r, 8f);
    }

    private void drawHudLabel(String title, String price, Rectangle r) {
        font.setColor(0.90f, 0.85f, 0.72f, 1f); font.draw(sprites, title, r.x, r.y + r.height * 0.68f, r.width, Align.center, false);
        float s = font.getData().scaleX; font.getData().setScale(s * 0.82f); font.setColor(0.72f, 0.69f, 0.56f, 1f);
        font.draw(sprites, price, r.x, r.y + r.height * 0.31f, r.width, Align.center, false); font.getData().setScale(s);
    }

    private boolean hasSelectedVillager() {
        for (Integer id : selected) { Unit u = findById(id); if (u != null && u.type == UnitType.VILLAGER) return true; }
        return false;
    }

    private boolean inside(Rectangle r, float x, float y) { return r.contains(x, y); }

    private void train(UnitType type, float f, float w, float g, float seconds) {
        if (countTeam(PLAYER) + trainQueue.size() >= populationCap || trainQueue.size() >= 8) return;
        if (type != UnitType.VILLAGER && barracksCount <= 0) return;
        if (food < f || wood < w || gold < g) return;
        food -= f; wood -= w; gold -= g; trainQueue.add(new TrainOrder(type, seconds));
    }

    private void selectBuild(BuildingType type) {
        if (!hasSelectedVillager()) return;
        pendingBuild = pendingBuild == type ? null : type;
    }

    private boolean placePendingBuilding(float x, float z) {
        if (pendingBuild == null) return false;
        if (Vector2.dst(x, z, playerStronghold.pos.x, playerStronghold.pos.z) > 25f || Math.abs(x - 4.2f) < 5.4f) return false;
        for (Building b : buildings) if (Vector2.dst(x, z, b.pos.x, b.pos.z) < 4.5f) return false;
        if (pendingBuild == BuildingType.HOUSE) {
            if (wood < 100f) return false; wood -= 100f;
        } else if (pendingBuild == BuildingType.BARRACKS) {
            if (wood < 150f) return false; wood -= 150f;
        } else {
            if (wood < 120f || gold < 80f) return false; wood -= 120f; gold -= 80f;
        }
        createBuilding(pendingBuild, x, z); pendingBuild = null; return true;
    }

    private void handleTap(float x, float y) {
        int h = Gdx.graphics.getHeight(); float uy = h - y;
        if (mode == Mode.MENU) {
            if (inside(easyButton, x, uy)) difficulty = 0;
            else if (inside(normalButton, x, uy)) difficulty = 1;
            else if (inside(hardButton, x, uy)) difficulty = 2;
            else if (inside(playButton, x, uy)) resetGame();
            return;
        }
        if (gameOver) {
            mode = Mode.MENU; units.clear(); resources.clear(); buildings.clear(); trainQueue.clear(); projectiles.clear(); selected.clear(); resetMenuCamera(); return;
        }
        if (inside(villagerButton, x, uy)) { train(UnitType.VILLAGER, 50, 0, 0, 2.8f); return; }
        if (inside(swordButton, x, uy)) { train(UnitType.SWORD, 60, 0, 20, 3.4f); return; }
        if (inside(archerButton, x, uy)) { train(UnitType.ARCHER, 0, 45, 30, 3.7f); return; }
        if (inside(cavalryButton, x, uy)) { train(UnitType.CAVALRY, 90, 0, 60, 4.6f); return; }
        if (hasSelectedVillager()) {
            if (inside(houseButton, x, uy)) { selectBuild(BuildingType.HOUSE); return; }
            if (inside(barracksButton, x, uy)) { selectBuild(BuildingType.BARRACKS); return; }
            if (inside(towerButton, x, uy)) { selectBuild(BuildingType.TOWER); return; }
        }

        Unit hit = findUnitAtScreen(x, y);
        long now = System.currentTimeMillis();
        if (hit != null) {
            if (hit.team == PLAYER) {
                boolean dbl = hit.id == lastTapUnit && now - lastTapMs < 330;
                selected.clear();
                if (dbl) for (Unit u : units) if (u.team == PLAYER && u.type == hit.type && Vector2.dst2(u.pos.x, u.pos.z, hit.pos.x, hit.pos.z) < 256f) selected.add(u.id);
                else selected.add(hit.id);
                pendingBuild = null; lastTapUnit = hit.id; lastTapMs = now;
            } else if (!selected.isEmpty()) issueUnitAttack(hit.id);
            return;
        }

        ResourceNode resource = findResourceAtScreen(x, y);
        if (resource != null && !selected.isEmpty()) {
            boolean assigned = false;
            for (Integer id : selected) {
                Unit u = findById(id);
                if (u != null && u.type == UnitType.VILLAGER) { assignGather(u, resource); assigned = true; }
            }
            if (assigned) return;
        }

        int baseHit = findStrongholdAtScreen(x, y);
        if (baseHit == ENEMY && !selected.isEmpty()) {
            for (Integer id : selected) {
                Unit u = findById(id); if (u == null) continue;
                u.gatherNode = null; u.targetUnitId = -1; u.targetStronghold = ENEMY; u.moving = false;
            }
            return;
        }

        Ray ray = camera.getPickRay(x, y);
        if (!Intersector.intersectRayPlane(ray, groundPlane, tmp3)) return;
        if (pendingBuild != null && placePendingBuilding(tmp3.x, tmp3.z)) return;
        if (!selected.isEmpty()) issueMove(tmp3.x, tmp3.z);
    }

    private void issueUnitAttack(int enemyId) {
        for (Integer id : selected) {
            Unit u = findById(id);
            if (u != null) { u.gatherNode = null; u.targetUnitId = enemyId; u.targetStronghold = -1; u.moving = false; }
        }
    }

    private void issueMove(float x, float z) {
        List<Unit> group = new ArrayList<>();
        for (Integer id : selected) { Unit u = findById(id); if (u != null) group.add(u); }
        group.sort(Comparator.comparingInt(a -> a.id));
        int cols = Math.max(1, (int)Math.ceil(Math.sqrt(group.size()))); float spacing = 1.38f;
        for (int i = 0; i < group.size(); i++) {
            int row = i / cols, col = i % cols; Unit u = group.get(i); u.gatherNode = null;
            u.target.set(x + (col - (cols - 1) * 0.5f) * spacing, 0f, z + (row - (cols - 1) * 0.5f) * spacing);
            clearTargets(u); u.moving = true;
        }
        commandMarker.set(x, 0f, z); commandMarkerTime = 1f;
    }

    private Unit findUnitAtScreen(float x, float inputY) {
        float best = 64f; Unit result = null; int h = Gdx.graphics.getHeight();
        for (Unit u : units) {
            tmpScreen.set(u.pos.x, u.type == UnitType.CAVALRY ? 1.9f : 1.30f, u.pos.z); camera.project(tmpScreen);
            float dx = tmpScreen.x - x, dy = (h - tmpScreen.y) - inputY; float d = (float)Math.sqrt(dx * dx + dy * dy);
            if (d < best) { best = d; result = u; }
        }
        return result;
    }

    private ResourceNode findResourceAtScreen(float x, float inputY) {
        float best = 82f; ResourceNode result = null; int h = Gdx.graphics.getHeight();
        for (ResourceNode n : resources) {
            if (n.amount <= 0f) continue;
            tmpScreen.set(n.pos.x, 1.3f, n.pos.z); camera.project(tmpScreen);
            float dx = tmpScreen.x - x, dy = (h - tmpScreen.y) - inputY; float d = (float)Math.sqrt(dx * dx + dy * dy);
            if (d < best) { best = d; result = n; }
        }
        return result;
    }

    private int findStrongholdAtScreen(float x, float inputY) {
        int h = Gdx.graphics.getHeight(), bestTeam = -1; float best = 110f;
        Stronghold[] bases = {playerStronghold, enemyStronghold};
        for (Stronghold s : bases) {
            if (s == null || s.hp <= 0f) continue;
            tmpScreen.set(s.pos.x, 2.8f, s.pos.z); camera.project(tmpScreen);
            float dx = tmpScreen.x - x, dy = (h - tmpScreen.y) - inputY; float d = (float)Math.sqrt(dx * dx + dy * dy);
            if (d < best) { best = d; bestTeam = s.team; }
        }
        return bestTeam;
    }

    private final class GameInput extends InputAdapter {
        @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (pointer == 0) { downX = lastX = screenX; downY = lastY = screenY; dragged = false; }
            if (Gdx.input.isTouched(0) && Gdx.input.isTouched(1)) { pinchDistance = pointerDistance(); pinchCameraDistance = cameraDistance; }
            return true;
        }

        @Override public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (mode != Mode.GAME || gameOver) return true;
            if (Gdx.input.isTouched(0) && Gdx.input.isTouched(1)) {
                float d = pointerDistance();
                if (pinchDistance > 20f && d > 20f) cameraDistance = clamp(pinchCameraDistance * (pinchDistance / d), 18f, 58f);
                dragged = true; return true;
            }
            if (pointer == 0) {
                float dx = screenX - lastX, dy = screenY - lastY;
                if (Vector2.dst(downX, downY, screenX, screenY) > 14f) dragged = true;
                if (dragged) {
                    float factor = cameraDistance * 0.0021f;
                    cameraTarget.x -= (dx * 0.72f - dy * 0.42f) * factor;
                    cameraTarget.z -= (dx * 0.72f + dy * 0.42f) * factor;
                    cameraTarget.x = clamp(cameraTarget.x, -WORLD + 5f, WORLD - 5f);
                    cameraTarget.z = clamp(cameraTarget.z, -WORLD + 5f, WORLD - 5f);
                }
                lastX = screenX; lastY = screenY;
            }
            return true;
        }

        @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (pointer == 0 && !dragged) handleTap(screenX, screenY);
            if (!Gdx.input.isTouched(0) || !Gdx.input.isTouched(1)) pinchDistance = 0f;
            return true;
        }

        @Override public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.BACK && mode == Mode.GAME) {
                if (pendingBuild != null) { pendingBuild = null; return true; }
                mode = Mode.MENU; units.clear(); resources.clear(); buildings.clear(); trainQueue.clear(); projectiles.clear(); selected.clear(); resetMenuCamera(); return true;
            }
            return false;
        }
    }

    private float pointerDistance() {
        if (!Gdx.input.isTouched(0) || !Gdx.input.isTouched(1)) return 0f;
        return Vector2.dst(Gdx.input.getX(0), Gdx.input.getY(0), Gdx.input.getX(1), Gdx.input.getY(1));
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    @Override public void resize(int width, int height) {
        camera.viewportWidth = width; camera.viewportHeight = height; camera.update();
    }

    @Override public void dispose() {
        if (modelBatch != null) modelBatch.dispose();
        if (shapes != null) shapes.dispose();
        if (sprites != null) sprites.dispose();
        if (font != null) font.dispose();
        for (Chunk c : chunks) if (c.cache != null) c.cache.dispose();
        for (Model m : ownedModels) m.dispose();
        for (Texture t : ownedTextures) t.dispose();
    }
}
