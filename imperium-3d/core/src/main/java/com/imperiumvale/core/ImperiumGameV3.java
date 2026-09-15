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
 * Imperium Vale 3D V3.
 *
 * Mobile-first RTS core with chunked static rendering, adaptive LOD,
 * gatherable resources, production queues, villager construction, defensive
 * towers, stronghold objectives and touch-first controls.
 */
public final class ImperiumGameV3 extends ApplicationAdapter {
    private static final long ATTR = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
    private static final long ATTR_TEX = ATTR | VertexAttributes.Usage.TextureCoordinates;
    private static final int PLAYER = 0;
    private static final int ENEMY = 1;
    private static final float WORLD = 39f;
    private static final float MAX_DT = 1f / 20f;
    private static final float DEG = 0.017453292f;
    private static final int CHUNK_COUNT = 6;
    private static final String VERSION = "v0.6.0-alpha";

    private enum Mode { MENU, GAME }
    private enum UnitType { VILLAGER, SWORD, ARCHER, CAVALRY }
    private enum ResourceType { FOOD, WOOD, GOLD }
    private enum BuildingType { HOUSE, BARRACKS, TOWER }

    private static final class Stronghold {
        final int team;
        final Vector3 pos = new Vector3();
        float hp = 2200f;
        final float maxHp = 2200f;
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
        ModelInstance model;

        ResourceNode(ResourceType type, float x, float z, float amount) {
            this.type = type;
            this.pos.set(x, 0f, z);
            this.amount = amount;
            this.maxAmount = amount;
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
        ModelInstance extra;
        ModelInstance shadow;

        Unit(int id, int team, UnitType type, float x, float z) {
            this.id = id;
            this.team = team;
            this.type = type;
            pos.set(x, 0f, z);
            target.set(pos);
            switch (type) {
                case VILLAGER -> {
                    maxHp = 72f;
                    speed = 4.45f;
                    damage = 5.5f;
                    range = 0.95f;
                    attackPeriod = 1.0f;
                }
                case SWORD -> {
                    maxHp = 132f;
                    speed = 4.0f;
                    damage = 21f;
                    range = 1.05f;
                    attackPeriod = 0.86f;
                }
                case ARCHER -> {
                    maxHp = 84f;
                    speed = 4.15f;
                    damage = 15f;
                    range = 6.7f;
                    attackPeriod = 1.08f;
                }
                case CAVALRY -> {
                    maxHp = 182f;
                    speed = 6.35f;
                    damage = 29f;
                    range = 1.42f;
                    attackPeriod = 0.96f;
                }
            }
            hp = maxHp;
            thinkTimer = 0.1f + (id % 7) * 0.08f;
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

        Building(int id, int team, BuildingType type, float x, float z) {
            this.id = id;
            this.team = team;
            this.type = type;
            pos.set(x, 0f, z);
            switch (type) {
                case HOUSE -> maxHp = 520f;
                case BARRACKS -> maxHp = 850f;
                case TOWER -> maxHp = 720f;
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
            this.life = duration;
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
    private final RandomXS128 rng = new RandomXS128(904221L);

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
    private Texture woodTexture;

    private Model blueBody;
    private Model redBody;
    private Model blueCavalryBody;
    private Model redCavalryBody;
    private Model headModel;
    private Model swordModel;
    private Model bowModel;
    private Model toolModel;
    private Model blueShieldModel;
    private Model redShieldModel;
    private Model horseModel;
    private Model shadowModel;
    private Model foodNodeModel;
    private Model woodNodeModel;
    private Model goldNodeModel;
    private Model projectileModel;
    private Model houseBodyModel;
    private Model houseRoofModel;
    private Model barracksBodyModel;
    private Model barracksRoofModel;
    private Model towerBodyModel;
    private Model towerRoofModel;

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

    private float food = 650f;
    private float wood = 620f;
    private float gold = 420f;
    private float aiAccumulator;
    private float menuOrbit;
    private float cameraDistance = 43f;
    private float cameraYaw = 43f;
    private float cameraPitch = 48f;
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

        camera = new PerspectiveCamera(38f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.25f;
        camera.far = 165f;

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.44f, 0.47f, 0.42f, 1f));
        environment.set(new ColorAttribute(ColorAttribute.Fog, 0.64f, 0.71f, 0.67f, 1f));
        environment.add(new DirectionalLight().set(1.04f, 0.96f, 0.82f, -0.58f, -0.78f, -0.35f));
        environment.add(new DirectionalLight().set(0.17f, 0.22f, 0.30f, 0.45f, -0.42f, 0.40f));

        buildTextures();
        buildSharedModels();
        buildWorld();
        rebuildChunks();
        resetMenuCamera();
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        Gdx.input.setInputProcessor(new GameInput());
    }

    private void buildTextures() {
        grassTexture = makeTexture(64, 0.28f, 0.46f, 0.18f, 0.12f, 0);
        dirtTexture = makeTexture(64, 0.46f, 0.33f, 0.18f, 0.14f, 1);
        stoneTexture = makeTexture(64, 0.55f, 0.56f, 0.52f, 0.11f, 2);
        woodTexture = makeTexture(64, 0.50f, 0.34f, 0.17f, 0.13f, 3);
    }

    private Texture makeTexture(int size, float br, float bg, float bb, float variation, int style) {
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int hash = x * 928371 + y * 364479 + style * 19273;
                hash ^= hash << 13;
                hash ^= hash >>> 17;
                hash ^= hash << 5;
                float noise = ((hash & 1023) / 1023f - 0.5f) * variation;
                float r = clamp(br + noise, 0f, 1f);
                float g = clamp(bg + noise, 0f, 1f);
                float b = clamp(bb + noise, 0f, 1f);
                if (style == 2 && (y % 16 == 0 || (x + ((y / 16) & 1) * 8) % 16 == 0)) {
                    r *= 0.66f; g *= 0.66f; b *= 0.66f;
                } else if (style == 3 && x % 11 == 0) {
                    r *= 0.76f; g *= 0.76f; b *= 0.76f;
                } else if (style == 1 && (x + y * 3) % 23 == 0) {
                    r *= 1.10f; g *= 1.08f; b *= 1.04f;
                }
                pixmap.setColor(r, g, b, 1f);
                pixmap.drawPixel(x, y);
            }
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        pixmap.dispose();
        ownedTextures.add(texture);
        return texture;
    }

    private Material material(float r, float g, float b) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
    }

    private Material textured(Texture texture, float r, float g, float b) {
        return new Material(
            TextureAttribute.createDiffuse(texture),
            ColorAttribute.createDiffuse(new Color(r, g, b, 1f))
        );
    }

    private Material alphaMaterial(float r, float g, float b, float a) {
        return new Material(
            ColorAttribute.createDiffuse(new Color(r, g, b, a)),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, a)
        );
    }

    private Model own(Model model) {
        ownedModels.add(model);
        return model;
    }

    private void buildSharedModels() {
        ModelBuilder mb = new ModelBuilder();
        blueBody = own(mb.createCapsule(0.37f, 1.42f, 10, material(0.08f, 0.29f, 0.72f), ATTR));
        redBody = own(mb.createCapsule(0.37f, 1.42f, 10, material(0.70f, 0.11f, 0.07f), ATTR));
        blueCavalryBody = own(mb.createCapsule(0.39f, 1.46f, 10, material(0.07f, 0.25f, 0.65f), ATTR));
        redCavalryBody = own(mb.createCapsule(0.39f, 1.46f, 10, material(0.64f, 0.09f, 0.06f), ATTR));
        headModel = own(mb.createSphere(0.45f, 0.45f, 0.45f, 10, 7, material(0.79f, 0.66f, 0.49f), ATTR));
        swordModel = own(mb.createBox(0.09f, 1.00f, 0.09f, material(0.80f, 0.82f, 0.84f), ATTR));
        bowModel = own(mb.createBox(0.07f, 0.94f, 0.20f, material(0.39f, 0.20f, 0.07f), ATTR));
        toolModel = own(mb.createBox(0.09f, 0.85f, 0.09f, material(0.36f, 0.20f, 0.08f), ATTR));
        blueShieldModel = own(mb.createCylinder(0.74f, 0.12f, 0.74f, 10, material(0.10f, 0.31f, 0.72f), ATTR));
        redShieldModel = own(mb.createCylinder(0.74f, 0.12f, 0.74f, 10, material(0.69f, 0.12f, 0.08f), ATTR));
        horseModel = own(mb.createBox(1.52f, 0.75f, 0.66f, material(0.31f, 0.18f, 0.085f), ATTR));
        shadowModel = own(mb.createCylinder(1.30f, 0.025f, 0.82f, 12, alphaMaterial(0.02f, 0.025f, 0.02f, 0.30f), ATTR));
        projectileModel = own(mb.createBox(0.08f, 0.08f, 0.55f, material(0.28f, 0.17f, 0.07f), ATTR));

        foodNodeModel = own(mb.createSphere(2.2f, 1.15f, 1.9f, 10, 7, material(0.31f, 0.52f, 0.16f), ATTR));
        woodNodeModel = own(mb.createBox(2.5f, 1.35f, 2.0f, textured(woodTexture, 0.92f, 0.92f, 0.92f), ATTR_TEX));
        goldNodeModel = own(mb.createSphere(2.1f, 1.35f, 1.8f, 10, 7, material(0.58f, 0.50f, 0.25f), ATTR));

        houseBodyModel = own(mb.createBox(3.7f, 2.25f, 3.2f, textured(woodTexture, 1f, 0.92f, 0.78f), ATTR_TEX));
        houseRoofModel = own(mb.createCone(4.55f, 2.15f, 3.95f, 4, material(0.12f, 0.31f, 0.69f), ATTR));
        barracksBodyModel = own(mb.createBox(5.5f, 2.55f, 4.1f, textured(stoneTexture, 0.82f, 0.82f, 0.78f), ATTR_TEX));
        barracksRoofModel = own(mb.createCone(6.2f, 2.35f, 4.8f, 4, material(0.10f, 0.27f, 0.63f), ATTR));
        towerBodyModel = own(mb.createCylinder(2.6f, 5.8f, 2.6f, 12, textured(stoneTexture, 0.86f, 0.86f, 0.82f), ATTR_TEX));
        towerRoofModel = own(mb.createCone(3.1f, 2.35f, 3.1f, 12, material(0.09f, 0.27f, 0.64f), ATTR));
    }

    private void buildWorld() {
        staticPieces.clear();
        transparentPieces.clear();
        ModelBuilder mb = new ModelBuilder();

        Model[] grass = new Model[] {
            own(mb.createBox(6.05f, 0.34f, 6.05f, textured(grassTexture, 0.88f, 0.98f, 0.86f), ATTR_TEX)),
            own(mb.createBox(6.05f, 0.34f, 6.05f, textured(grassTexture, 0.96f, 1.00f, 0.92f), ATTR_TEX)),
            own(mb.createBox(6.05f, 0.34f, 6.05f, textured(grassTexture, 0.82f, 0.93f, 0.80f), ATTR_TEX))
        };
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                int h = Math.abs(x * 31 + z * 17 + x * z * 3) % grass.length;
                addStatic(grass[h], x * 6f, -0.20f, z * 6f, 0f, 1f, 1f, 1f, false);
            }
        }

        createRoad(mb);
        createRiver(mb);
        createBase(mb, PLAYER, -24f, -17f);
        createBase(mb, ENEMY, 24f, 17f);
        createForest(mb);
        createDecorativeMines(mb);
        createRockFields(mb);
    }

    private void createRoad(ModelBuilder mb) {
        Model road = own(mb.createBox(7.1f, 0.075f, 4.9f, textured(dirtTexture, 0.98f, 0.94f, 0.87f), ATTR_TEX));
        float ax = -24f, az = -17f;
        float bx = 24f, bz = 17f;
        float dx = bx - ax, dz = bz - az;
        float yaw = (float)Math.toDegrees(Math.atan2(dx, dz));
        for (int i = 0; i <= 11; i++) {
            float t = i / 11f;
            float x = ax + dx * t;
            float z = az + dz * t + (float)Math.sin(t * 6.283f) * 0.65f;
            addStatic(road, x, 0.015f, z, yaw, 1f, 1f, 1f, false);
        }
    }

    private void createRiver(ModelBuilder mb) {
        Model water = own(mb.createBox(7.6f, 0.05f, 9.3f, alphaMaterial(0.06f, 0.34f, 0.50f, 0.84f), ATTR));
        Model shallow = own(mb.createBox(9.0f, 0.03f, 9.3f, alphaMaterial(0.18f, 0.49f, 0.58f, 0.62f), ATTR));
        Model bank = own(mb.createBox(1.05f, 0.23f, 9.3f, textured(dirtTexture, 0.88f, 0.88f, 0.74f), ATTR_TEX));
        for (int i = -4; i <= 4; i++) {
            float z = i * 9f;
            float x = 4f + (float)Math.sin(i * 0.72f) * 1.45f;
            addStatic(shallow, x, -0.025f, z, -4f, 1f, 1f, 1f, true);
            addStatic(water, x, 0.006f, z, -4f, 1f, 1f, 1f, true);
            addStatic(bank, x - 4.5f, 0.035f, z, -4f, 1f, 1f, 1f, false);
            addStatic(bank, x + 4.5f, 0.035f, z, -4f, 1f, 1f, 1f, false);
        }
        Model deck = own(mb.createBox(10.8f, 0.42f, 5.3f, textured(woodTexture, 0.94f, 0.88f, 0.78f), ATTR_TEX));
        Model rail = own(mb.createBox(10.8f, 0.58f, 0.24f, material(0.27f, 0.15f, 0.065f), ATTR));
        addStatic(deck, 4f, 0.29f, 0.2f, -32f, 1f, 1f, 1f, false);
        addStatic(rail, 4f, 0.78f, -2.35f, -32f, 1f, 1f, 1f, false);
        addStatic(rail, 4f, 0.78f, 2.75f, -32f, 1f, 1f, 1f, false);
    }

    private void createBase(ModelBuilder mb, int team, float x, float z) {
        Color roof = team == PLAYER ? new Color(0.07f, 0.25f, 0.66f, 1f) : new Color(0.63f, 0.12f, 0.07f, 1f);
        Model keep = own(mb.createBox(7.6f, 3.9f, 6.6f, textured(stoneTexture, 0.90f, 0.90f, 0.86f), ATTR_TEX));
        Model dark = own(mb.createCylinder(2.25f, 6.3f, 2.25f, 12, textured(stoneTexture, 0.72f, 0.72f, 0.70f), ATTR_TEX));
        Model roofModel = own(mb.createCone(2.86f, 2.5f, 2.86f, 12, new Material(ColorAttribute.createDiffuse(roof)), ATTR));
        Model battlement = own(mb.createBox(0.84f, 0.72f, 0.92f, textured(stoneTexture, 0.96f, 0.96f, 0.91f), ATTR_TEX));
        Model door = own(mb.createBox(1.35f, 2.30f, 0.18f, textured(woodTexture, 0.62f, 0.55f, 0.45f), ATTR_TEX));

        addStatic(keep, x, 1.95f, z, 0f, 1f, 1f, 1f, false);
        for (int i = -3; i <= 3; i++) {
            addStatic(battlement, x + i, 4.22f, z - 2.9f, 0f, 1f, 1f, 1f, false);
            addStatic(battlement, x + i, 4.22f, z + 2.9f, 0f, 1f, 1f, 1f, false);
        }
        float[][] towers = {{-3.25f, -2.70f}, {3.25f, 2.70f}};
        for (float[] off : towers) {
            addStatic(dark, x + off[0], 3.15f, z + off[1], 0f, 1f, 1f, 1f, false);
            addStatic(roofModel, x + off[0], 7.50f, z + off[1], 0f, 1f, 1f, 1f, false);
        }
        addStatic(door, x, 1.16f, z - 3.40f, 0f, 1f, 1f, 1f, false);

        Model wall = own(mb.createBox(8.4f, 2.1f, 0.72f, textured(stoneTexture, 0.76f, 0.76f, 0.72f), ATTR_TEX));
        addStatic(wall, x - 7.5f, 1.05f, z - 7f, 0f, 1f, 1f, 1f, false);
        addStatic(wall, x + 7.5f, 1.05f, z + 7f, 0f, 1f, 1f, 1f, false);
        addStatic(wall, x - 7.5f, 1.05f, z + 7f, 0f, 1f, 1f, 1f, false);
        addStatic(wall, x + 7.5f, 1.05f, z - 7f, 0f, 1f, 1f, 1f, false);

        createVillageBuildings(mb, team, x, z, roof);
    }

    private void createVillageBuildings(ModelBuilder mb, int team, float x, float z, Color roof) {
        Model house = own(mb.createBox(3.6f, 2.15f, 3f, textured(woodTexture, 1f, 0.92f, 0.80f), ATTR_TEX));
        Model roofModel = own(mb.createCone(4.45f, 2.15f, 3.75f, 4, new Material(ColorAttribute.createDiffuse(roof.cpy().mul(0.84f))), ATTR));
        float side = team == PLAYER ? 1f : -1f;
        for (int i = 0; i < 4; i++) {
            float sx = x + side * (6.2f + (i % 2) * 4.4f);
            float sz = z + (i / 2 == 0 ? -1f : 1f) * (3.1f + (i % 2) * 1.4f);
            addStatic(house, sx, 1.08f, sz, 0f, 1f, 1f, 1f, false);
            addStatic(roofModel, sx, 3.15f, sz, 45f, 1f, 1f, 1f, false);
        }
        Model barracks = own(mb.createBox(5.2f, 2.35f, 3.7f, textured(stoneTexture, 0.80f, 0.78f, 0.70f), ATTR_TEX));
        Model barracksRoof = own(mb.createCone(5.95f, 2.3f, 4.45f, 4, new Material(ColorAttribute.createDiffuse(roof.cpy().mul(0.72f))), ATTR));
        addStatic(barracks, x - side * 6.3f, 1.18f, z + side * 4.8f, 0f, 1f, 1f, 1f, false);
        addStatic(barracksRoof, x - side * 6.3f, 3.38f, z + side * 4.8f, 45f, 1f, 1f, 1f, false);
    }

    private void createForest(ModelBuilder mb) {
        Model trunk = own(mb.createCylinder(0.44f, 2.4f, 0.44f, 7, textured(woodTexture, 0.72f, 0.66f, 0.56f), ATTR_TEX));
        Model crownLow = own(mb.createCone(2.65f, 3.65f, 2.65f, 8, material(0.11f, 0.31f, 0.09f), ATTR));
        Model crownMid = own(mb.createCone(2.08f, 3.05f, 2.08f, 8, material(0.15f, 0.39f, 0.11f), ATTR));
        for (int i = 0; i < 72; i++) {
            float x;
            float z;
            do {
                x = rng.nextFloat() * 76f - 38f;
                z = rng.nextFloat() * 76f - 38f;
            } while (nearBase(x, z) || Math.abs(x - 4f) < 5.8f || nearMainRoad(x, z));
            float s = 0.72f + rng.nextFloat() * 0.60f;
            addStatic(trunk, x, 1.20f * s, z, 0f, s, s, s, false);
            addStatic(crownLow, x, 3.10f * s, z, rng.nextFloat() * 360f, s, s, s, false);
            addStatic(crownMid, x, 4.22f * s, z, rng.nextFloat() * 360f, s, s, s, false);
        }
    }

    private void createDecorativeMines(ModelBuilder mb) {
        Model rock = own(mb.createSphere(1.35f, 0.92f, 1.14f, 8, 5, material(0.42f, 0.40f, 0.31f), ATTR));
        Model ore = own(mb.createSphere(0.45f, 0.32f, 0.39f, 7, 4, material(0.88f, 0.64f, 0.10f), ATTR));
        float[][] mines = {{-10f, 15f}, {14f, -10f}, {-31f, 19f}, {31f, -18f}};
        for (float[] m : mines) {
            for (int i = 0; i < 5; i++) {
                float a = i * 72f * DEG;
                float x = m[0] + (float)Math.cos(a) * 1.25f;
                float z = m[1] + (float)Math.sin(a) * 1.25f;
                addStatic(rock, x, 0.45f, z, i * 29f, 0.9f, 1f, 1f, false);
                addStatic(ore, x + 0.24f, 0.90f, z - 0.16f, i * 13f, 1f, 1f, 1f, false);
            }
        }
    }

    private void createRockFields(ModelBuilder mb) {
        Model rock = own(mb.createSphere(1.0f, 0.66f, 0.88f, 7, 4, material(0.43f, 0.44f, 0.40f), ATTR));
        for (int i = 0; i < 26; i++) {
            float x = rng.nextFloat() * 70f - 35f;
            float z = rng.nextFloat() * 70f - 35f;
            if (nearBase(x, z) || nearMainRoad(x, z) || Math.abs(x - 4f) < 6f) continue;
            float s = 0.5f + rng.nextFloat() * 0.7f;
            addStatic(rock, x, 0.26f * s, z, rng.nextFloat() * 180f, s, s, s, false);
        }
    }

    private boolean nearBase(float x, float z) {
        return Vector2.dst(x, z, -24f, -17f) < 13f || Vector2.dst(x, z, 24f, 17f) < 13f;
    }

    private boolean nearMainRoad(float x, float z) {
        float expectedZ = x * (34f / 48f);
        return Math.abs(z - expectedZ) < 3.4f;
    }

    private void addStatic(Model model, float x, float y, float z, float yaw, float sx, float sy, float sz, boolean transparent) {
        ModelInstance instance = new ModelInstance(model);
        instance.transform.setToTranslation(x, y, z).rotate(Vector3.Y, yaw).scale(sx, sy, sz);
        StaticPiece piece = new StaticPiece(instance, x, z, transparent);
        if (transparent) transparentPieces.add(piece);
        else staticPieces.add(piece);
    }

    private void rebuildChunks() {
        for (Chunk chunk : chunks) if (chunk.cache != null) chunk.cache.dispose();
        chunks.clear();
        float size = WORLD * 2f / CHUNK_COUNT;
        for (int z = 0; z < CHUNK_COUNT; z++) {
            for (int x = 0; x < CHUNK_COUNT; x++) {
                Chunk chunk = new Chunk();
                chunk.center.set(-WORLD + (x + 0.5f) * size, 1.8f, -WORLD + (z + 0.5f) * size);
                chunk.radius = size * 1.10f;
                chunks.add(chunk);
            }
        }
        for (StaticPiece piece : staticPieces) {
            int cx = (int)((piece.x + WORLD) / (WORLD * 2f) * CHUNK_COUNT);
            int cz = (int)((piece.z + WORLD) / (WORLD * 2f) * CHUNK_COUNT);
            cx = Math.max(0, Math.min(CHUNK_COUNT - 1, cx));
            cz = Math.max(0, Math.min(CHUNK_COUNT - 1, cz));
            chunks.get(cz * CHUNK_COUNT + cx).instances.add(piece.instance);
        }
        for (Chunk chunk : chunks) {
            if (chunk.instances.size == 0) continue;
            chunk.cache = new ModelCache();
            chunk.cache.begin();
            for (ModelInstance instance : chunk.instances) chunk.cache.add(instance);
            chunk.cache.end();
        }
    }

    private void resetMenuCamera() {
        cameraTarget.set(0f, 0f, 0f);
        cameraDistance = 49f;
        cameraYaw = 42f;
        cameraPitch = 49f;
        updateCamera();
    }

    private void resetGame() {
        mode = Mode.GAME;
        units.clear();
        resources.clear();
        buildings.clear();
        trainQueue.clear();
        projectiles.clear();
        selected.clear();
        nextId = 1;
        nextBuildingId = 1;
        populationCap = 40;
        barracksCount = 1;
        food = 650f;
        wood = 620f;
        gold = 420f;
        aiAccumulator = 0f;
        gameOver = false;
        endText = "";
        pendingBuild = null;
        commandMarkerTime = 0f;
        playerStronghold = new Stronghold(PLAYER, -24f, -17f);
        enemyStronghold = new Stronghold(ENEMY, 24f, 17f);

        cameraTarget.set(-17f, 0f, -11f);
        cameraDistance = 35f;
        cameraYaw = 43f;
        cameraPitch = 50f;

        createResourceNodes();
        List<Unit> villagers = new ArrayList<>();
        for (int i = 0; i < 7; i++) villagers.add(spawn(PLAYER, UnitType.VILLAGER, -26f + i * 1.05f, -11.5f + (i & 1) * 1.1f));
        for (int i = 0; i < 6; i++) spawn(PLAYER, UnitType.SWORD, -18.5f + i * 1.0f, -13.2f + (i & 1) * 1.0f);
        for (int i = 0; i < 4; i++) spawn(PLAYER, UnitType.ARCHER, -19f + i * 1.1f, -9.8f);
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
        addResource(ResourceType.FOOD, -30f, -7f, 750f);
        addResource(ResourceType.WOOD, -14f, -21f, 900f);
        addResource(ResourceType.GOLD, -12f, -8f, 650f);
        addResource(ResourceType.FOOD, 30f, 7f, 750f);
        addResource(ResourceType.WOOD, 14f, 21f, 900f);
        addResource(ResourceType.GOLD, 12f, 8f, 650f);
        addResource(ResourceType.FOOD, -3f, 15f, 900f);
        addResource(ResourceType.GOLD, 15f, -14f, 800f);
    }

    private void addResource(ResourceType type, float x, float z, float amount) {
        ResourceNode node = new ResourceNode(type, x, z, amount);
        Model model = type == ResourceType.FOOD ? foodNodeModel : type == ResourceType.WOOD ? woodNodeModel : goldNodeModel;
        node.model = new ModelInstance(model);
        updateResourceTransform(node);
        resources.add(node);
    }

    private Unit spawn(int team, UnitType type, float x, float z) {
        Unit u = new Unit(nextId++, team, type, x, z);
        Model torso = type == UnitType.CAVALRY
            ? (team == PLAYER ? blueCavalryBody : redCavalryBody)
            : (team == PLAYER ? blueBody : redBody);
        u.body = new ModelInstance(torso);
        u.head = new ModelInstance(headModel);
        u.shadow = new ModelInstance(shadowModel);
        switch (type) {
            case VILLAGER -> u.weapon = new ModelInstance(toolModel);
            case SWORD -> {
                u.weapon = new ModelInstance(swordModel);
                u.extra = new ModelInstance(team == PLAYER ? blueShieldModel : redShieldModel);
            }
            case ARCHER -> u.weapon = new ModelInstance(bowModel);
            case CAVALRY -> {
                u.weapon = new ModelInstance(swordModel);
                u.extra = new ModelInstance(horseModel);
            }
        }
        updateUnitTransform(u);
        units.add(u);
        return u;
    }

    private void updateUnitTransform(Unit u) {
        float attackSwing = u.cooldown > u.attackPeriod * 0.55f ? 28f : 0f;
        if (u.type == UnitType.CAVALRY) {
            u.extra.transform.setToTranslation(u.pos.x, 0.72f, u.pos.z).rotate(Vector3.Y, u.facingYaw);
            u.body.transform.setToTranslation(u.pos.x, 1.62f, u.pos.z).rotate(Vector3.Y, u.facingYaw);
            u.head.transform.setToTranslation(u.pos.x, 2.52f, u.pos.z);
            u.weapon.transform.setToTranslation(u.pos.x + 0.48f, 1.80f, u.pos.z)
                .rotate(Vector3.Z, -26f - attackSwing);
            u.shadow.transform.setToTranslation(u.pos.x, 0.035f, u.pos.z).scale(1.45f, 1f, 1.15f);
            return;
        }
        u.body.transform.setToTranslation(u.pos.x, 0.94f, u.pos.z).rotate(Vector3.Y, u.facingYaw);
        u.head.transform.setToTranslation(u.pos.x, 1.82f, u.pos.z);
        u.shadow.transform.setToTranslation(u.pos.x, 0.035f, u.pos.z);
        if (u.weapon != null) {
            float rot = u.type == UnitType.SWORD ? -27f - attackSwing : u.type == UnitType.ARCHER ? 8f : 35f;
            u.weapon.transform.setToTranslation(u.pos.x + 0.43f, 1.13f, u.pos.z).rotate(Vector3.Z, rot);
        }
        if (u.extra != null) {
            u.extra.transform.setToTranslation(u.pos.x - 0.43f, 1.06f, u.pos.z).rotate(Vector3.X, 90f);
        }
    }

    private void updateResourceTransform(ResourceNode node) {
        float ratio = Math.max(0.22f, node.amount / node.maxAmount);
        float scale = 0.68f + ratio * 0.36f;
        node.model.transform.setToTranslation(node.pos.x, node.type == ResourceType.WOOD ? 0.68f : 0.62f, node.pos.z)
            .scale(scale, scale, scale);
    }

    private Building createBuilding(BuildingType type, float x, float z) {
        Building b = new Building(nextBuildingId++, PLAYER, type, x, z);
        switch (type) {
            case HOUSE -> {
                b.body = new ModelInstance(houseBodyModel);
                b.roof = new ModelInstance(houseRoofModel);
                populationCap = Math.min(90, populationCap + 10);
            }
            case BARRACKS -> {
                b.body = new ModelInstance(barracksBodyModel);
                b.roof = new ModelInstance(barracksRoofModel);
                barracksCount++;
            }
            case TOWER -> {
                b.body = new ModelInstance(towerBodyModel);
                b.roof = new ModelInstance(towerRoofModel);
            }
        }
        updateBuildingTransform(b);
        buildings.add(b);
        return b;
    }

    private void updateBuildingTransform(Building b) {
        if (b.type == BuildingType.HOUSE) {
            b.body.transform.setToTranslation(b.pos.x, 1.12f, b.pos.z);
            b.roof.transform.setToTranslation(b.pos.x, 3.18f, b.pos.z).rotate(Vector3.Y, 45f);
        } else if (b.type == BuildingType.BARRACKS) {
            b.body.transform.setToTranslation(b.pos.x, 1.28f, b.pos.z);
            b.roof.transform.setToTranslation(b.pos.x, 3.55f, b.pos.z).rotate(Vector3.Y, 45f);
        } else {
            b.body.transform.setToTranslation(b.pos.x, 2.9f, b.pos.z);
            b.roof.transform.setToTranslation(b.pos.x, 6.95f, b.pos.z);
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
            menuOrbit += dt * 1.35f;
            cameraYaw = 42f + (float)Math.sin(menuOrbit * 0.22f) * 4.5f;
        }
        updateCamera();

        ScreenUtils.clear(0.62f, 0.70f, 0.68f, 1f, true);
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
        if (fpsSmooth < 42f) {
            lowFpsTimer += dt;
            highFpsTimer = 0f;
            if (lowFpsTimer > 2.0f) lowQuality = true;
        } else if (fpsSmooth > 54f) {
            highFpsTimer += dt;
            lowFpsTimer = 0f;
            if (highFpsTimer > 4.0f) lowQuality = false;
        } else {
            lowFpsTimer = Math.max(0f, lowFpsTimer - dt * 0.5f);
            highFpsTimer = Math.max(0f, highFpsTimer - dt * 0.5f);
        }
    }

    private void renderVisibleChunks() {
        for (Chunk chunk : chunks) {
            if (chunk.cache == null) continue;
            if (camera.frustum.sphereInFrustum(chunk.center, chunk.radius)) modelBatch.render(chunk.cache, environment);
        }
    }

    private void renderTransparentWorld() {
        for (StaticPiece piece : transparentPieces) {
            if (camera.frustum.sphereInFrustum(piece.x, 0f, piece.z, 7f)) modelBatch.render(piece.instance, environment);
        }
    }

    private void renderDynamicWorld() {
        for (ResourceNode node : resources) {
            if (node.amount <= 0f) continue;
            if (camera.frustum.sphereInFrustum(node.pos.x, 1f, node.pos.z, 2.5f)) modelBatch.render(node.model, environment);
        }
        for (Building b : buildings) {
            if (b.hp <= 0f) continue;
            if (!camera.frustum.sphereInFrustum(b.pos.x, 2f, b.pos.z, 4.5f)) continue;
            modelBatch.render(b.body, environment);
            if (b.roof != null) modelBatch.render(b.roof, environment);
        }
        for (Unit u : units) renderUnit3d(u);
        for (Projectile p : projectiles) modelBatch.render(p.model, environment);
    }

    private void renderUnit3d(Unit u) {
        if (!camera.frustum.sphereInFrustum(u.pos.x, 1.3f, u.pos.z, 2.2f)) return;
        float d2 = camera.position.dst2(u.pos.x, 1.3f, u.pos.z);
        boolean detail = !lowQuality && d2 < 1750f;
        if (detail && d2 < 1150f) modelBatch.render(u.shadow, environment);
        if (u.extra != null) modelBatch.render(u.extra, environment);
        modelBatch.render(u.body, environment);
        if (detail) {
            modelBatch.render(u.head, environment);
            if (u.weapon != null) modelBatch.render(u.weapon, environment);
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
                updateUnitTransform(u);
                continue;
            }

            Unit enemy = findById(u.targetUnitId);
            if (enemy != null && enemy.hp > 0f && enemy.team != u.team) {
                attackUnitTarget(u, enemy, dt);
            } else if (u.targetStronghold >= 0) {
                Stronghold targetBase = stronghold(u.targetStronghold);
                if (targetBase != null && targetBase.hp > 0f && targetBase.team != u.team) attackStrongholdTarget(u, targetBase, dt);
                else clearTargets(u);
            } else if (u.moving) {
                moveToward(u, u.target.x, u.target.z, dt);
            } else if (u.thinkTimer <= 0f) {
                u.thinkTimer = 0.45f + (u.id % 5) * 0.09f;
                Unit near = nearestEnemy(u, 5.4f);
                if (near != null) u.targetUnitId = near.id;
            }
            updateUnitTransform(u);
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
        TrainOrder order = trainQueue.get(0);
        order.remaining -= dt;
        if (order.remaining <= 0f) {
            spawn(PLAYER, order.type, -22.5f + rng.nextFloat() * 4.5f, -12.5f + rng.nextFloat() * 3.5f);
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
        Unit target = nearestUnitTo(base.pos.x, base.pos.z, base.team == PLAYER ? ENEMY : PLAYER, 10.5f);
        if (target != null) {
            target.hp -= 18f;
            base.cooldown = 1.35f;
            spawnProjectile(base.pos.x, 5.3f, base.pos.z, target.pos.x, 1.2f, target.pos.z);
        }
    }

    private void updateBuildings(float dt) {
        for (Building b : buildings) {
            if (b.type != BuildingType.TOWER || b.hp <= 0f) continue;
            b.cooldown -= dt;
            if (b.cooldown > 0f) continue;
            Unit target = nearestUnitTo(b.pos.x, b.pos.z, b.team == PLAYER ? ENEMY : PLAYER, 9.0f);
            if (target != null) {
                target.hp -= 16f;
                b.cooldown = 1.18f;
                spawnProjectile(b.pos.x, 5.0f, b.pos.z, target.pos.x, 1.2f, target.pos.z);
            }
        }
    }

    private void updateProjectiles(float dt) {
        Iterator<Projectile> it = projectiles.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();
            p.life -= dt;
            if (p.life <= 0f) {
                it.remove();
                continue;
            }
            float t = 1f - p.life / p.duration;
            float x = p.start.x + (p.end.x - p.start.x) * t;
            float z = p.start.z + (p.end.z - p.start.z) * t;
            float y = p.start.y + (p.end.y - p.start.y) * t + (float)Math.sin(t * Math.PI) * 1.4f;
            p.model.transform.setToTranslation(x, y, z);
        }
    }

    private void spawnProjectile(float sx, float sy, float sz, float ex, float ey, float ez) {
        if (projectiles.size() > (lowQuality ? 12 : 28)) return;
        ModelInstance instance = new ModelInstance(projectileModel);
        Vector3 start = new Vector3(sx, sy, sz);
        Vector3 end = new Vector3(ex, ey, ez);
        instance.transform.setToTranslation(start);
        projectiles.add(new Projectile(instance, start, end, 0.42f));
    }

    private void updateGatherer(Unit u, float dt) {
        ResourceNode node = u.gatherNode;
        if (node == null || node.amount <= 0f) {
            if (u.carried > 0f) u.gatherState = 2;
            else {
                u.gatherNode = null;
                u.gatherState = 0;
                return;
            }
        }

        if (u.gatherState == 0) {
            if (node == null) return;
            float d = Vector2.dst(u.pos.x, u.pos.z, node.pos.x, node.pos.z);
            if (d > 1.55f) moveToward(u, node.pos.x, node.pos.z, dt);
            else {
                u.moving = false;
                u.gatherState = 1;
            }
        } else if (u.gatherState == 1) {
            u.gatherTimer += dt;
            if (u.gatherTimer >= 0.48f) {
                u.gatherTimer = 0f;
                float take = Math.min(1.6f, node.amount);
                node.amount -= take;
                u.carried += take;
                updateResourceTransform(node);
                if (u.carried >= 14f || node.amount <= 0f) u.gatherState = 2;
            }
        } else {
            float d = Vector2.dst(u.pos.x, u.pos.z, playerStronghold.pos.x, playerStronghold.pos.z);
            if (d > 5.0f) moveToward(u, playerStronghold.pos.x, playerStronghold.pos.z, dt);
            else {
                if (node != null) {
                    if (node.type == ResourceType.FOOD) food += u.carried;
                    else if (node.type == ResourceType.WOOD) wood += u.carried;
                    else gold += u.carried;
                }
                u.carried = 0f;
                if (node != null && node.amount > 0f) u.gatherState = 0;
                else {
                    u.gatherNode = null;
                    u.gatherState = 0;
                }
            }
        }
    }

    private void assignGather(Unit u, ResourceNode node) {
        if (u.type != UnitType.VILLAGER || u.team != PLAYER) return;
        clearTargets(u);
        u.gatherNode = node;
        u.gatherState = 0;
        u.gatherTimer = 0f;
        u.moving = false;
    }

    private void attackUnitTarget(Unit u, Unit enemy, float dt) {
        float d = Vector2.dst(u.pos.x, u.pos.z, enemy.pos.x, enemy.pos.z);
        if (d <= u.range) {
            u.moving = false;
            faceToward(u, enemy.pos.x, enemy.pos.z);
            if (u.cooldown <= 0f) {
                enemy.hp -= u.damage;
                u.cooldown = u.attackPeriod;
                if (u.type == UnitType.ARCHER) spawnProjectile(u.pos.x, 1.8f, u.pos.z, enemy.pos.x, 1.25f, enemy.pos.z);
            }
        } else moveToward(u, enemy.pos.x, enemy.pos.z, dt);
    }

    private void attackStrongholdTarget(Unit u, Stronghold targetBase, float dt) {
        float d = Vector2.dst(u.pos.x, u.pos.z, targetBase.pos.x, targetBase.pos.z);
        float attackRange = u.type == UnitType.ARCHER ? 8.0f : 4.7f;
        if (d <= attackRange) {
            u.moving = false;
            faceToward(u, targetBase.pos.x, targetBase.pos.z);
            if (u.cooldown <= 0f) {
                targetBase.hp = Math.max(0f, targetBase.hp - u.damage * 0.72f);
                u.cooldown = u.attackPeriod;
                if (u.type == UnitType.ARCHER) spawnProjectile(u.pos.x, 1.8f, u.pos.z, targetBase.pos.x, 3.2f, targetBase.pos.z);
            }
        } else moveToward(u, targetBase.pos.x, targetBase.pos.z, dt);
    }

    private void clearTargets(Unit u) {
        u.targetUnitId = -1;
        u.targetStronghold = -1;
    }

    private void moveToward(Unit u, float x, float z, float dt) {
        float dx = x - u.pos.x;
        float dz = z - u.pos.z;
        float d = (float)Math.sqrt(dx * dx + dz * dz);
        if (d < 0.12f) {
            u.moving = false;
            return;
        }
        faceToward(u, x, z);
        float step = Math.min(d, u.speed * dt);
        u.pos.x += dx / d * step;
        u.pos.z += dz / d * step;
        u.pos.x = clamp(u.pos.x, -WORLD, WORLD);
        u.pos.z = clamp(u.pos.z, -WORLD, WORLD);
    }

    private void faceToward(Unit u, float x, float z) {
        u.facingYaw = (float)Math.toDegrees(Math.atan2(x - u.pos.x, z - u.pos.z));
    }

    private void separateUnits() {
        for (int i = 0; i < units.size(); i++) {
            Unit a = units.get(i);
            for (int j = i + 1; j < units.size(); j++) {
                Unit b = units.get(j);
                float dx = b.pos.x - a.pos.x;
                float dz = b.pos.z - a.pos.z;
                float d2 = dx * dx + dz * dz;
                if (d2 < 0.04f || d2 > 0.82f) continue;
                float d = (float)Math.sqrt(d2);
                float push = (0.90f - d) * 0.040f;
                if (push <= 0f) continue;
                a.pos.x -= dx / d * push;
                a.pos.z -= dz / d * push;
                b.pos.x += dx / d * push;
                b.pos.z += dz / d * push;
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
            if (nearest != null) {
                u.targetUnitId = nearest.id;
                u.targetStronghold = -1;
            } else {
                u.targetUnitId = -1;
                u.targetStronghold = PLAYER;
            }
        }
    }

    private Unit nearestEnemy(Unit from, float maxDistance) {
        return nearestEnemyForTeam(from, from.team == PLAYER ? ENEMY : PLAYER, maxDistance);
    }

    private Unit nearestEnemyForTeam(Unit from, int targetTeam, float maxDistance) {
        Unit nearest = null;
        float best = maxDistance * maxDistance;
        for (Unit u : units) {
            if (u.team != targetTeam || u.hp <= 0f) continue;
            float d2 = Vector2.dst2(from.pos.x, from.pos.z, u.pos.x, u.pos.z);
            if (d2 < best) {
                best = d2;
                nearest = u;
            }
        }
        return nearest;
    }

    private Unit nearestUnitTo(float x, float z, int team, float maxDistance) {
        Unit nearest = null;
        float best = maxDistance * maxDistance;
        for (Unit u : units) {
            if (u.team != team || u.hp <= 0f) continue;
            float d2 = Vector2.dst2(x, z, u.pos.x, u.pos.z);
            if (d2 < best) {
                best = d2;
                nearest = u;
            }
        }
        return nearest;
    }

    private void checkEnd() {
        if (playerStronghold != null && playerStronghold.hp <= 0f) {
            gameOver = true;
            endText = "DERROTA";
        } else if (enemyStronghold != null && enemyStronghold.hp <= 0f) {
            gameOver = true;
            endText = "VITORIA";
        }
    }

    private Stronghold stronghold(int team) {
        return team == PLAYER ? playerStronghold : enemyStronghold;
    }

    private int countTeam(int team) {
        int count = 0;
        for (Unit u : units) if (u.team == team) count++;
        return count;
    }

    private int queuedPopulation() {
        return trainQueue.size();
    }

    private Unit findById(int id) {
        if (id < 0) return null;
        for (Unit u : units) if (u.id == id) return u;
        return null;
    }

    private void updateCamera() {
        float yaw = cameraYaw * DEG;
        float pitch = cameraPitch * DEG;
        float horizontal = cameraDistance * (float)Math.cos(pitch);
        camera.position.set(
            cameraTarget.x + (float)Math.sin(yaw) * horizontal,
            cameraTarget.y + cameraDistance * (float)Math.sin(pitch),
            cameraTarget.z + (float)Math.cos(yaw) * horizontal
        );
        camera.up.set(Vector3.Y);
        camera.lookAt(cameraTarget);
        camera.viewportWidth = Gdx.graphics.getWidth();
        camera.viewportHeight = Gdx.graphics.getHeight();
        camera.update();
    }

    private void drawWorldFeedback() {
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.98f, 0.82f, 0.25f, 1f);
        for (Integer id : selected) {
            Unit u = findById(id);
            if (u == null) continue;
            drawGroundRing(u.pos.x, u.pos.z, u.type == UnitType.CAVALRY ? 1.05f : 0.72f, lowQuality ? 14 : 22);
        }
        if (commandMarkerTime > 0f) {
            float pulse = 0.58f + (1f - commandMarkerTime) * 0.55f;
            shapes.setColor(0.40f, 0.82f, 1.0f, Math.min(1f, commandMarkerTime * 1.8f));
            drawGroundRing(commandMarker.x, commandMarker.z, pulse, lowQuality ? 16 : 24);
        }
        if (pendingBuild != null) {
            shapes.setColor(0.28f, 0.82f, 0.42f, 0.9f);
            drawGroundRing(cameraTarget.x, cameraTarget.z, 2.0f, 20);
        }
        shapes.end();
    }

    private void drawGroundRing(float x, float z, float r, int segments) {
        for (int i = 0; i < segments; i++) {
            float a0 = i * 6.2831853f / segments;
            float a1 = (i + 1) * 6.2831853f / segments;
            shapes.line(
                x + (float)Math.cos(a0) * r, 0.07f, z + (float)Math.sin(a0) * r,
                x + (float)Math.cos(a1) * r, 0.07f, z + (float)Math.sin(a1) * r
            );
        }
    }

    private void drawUi() {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        uiMatrix.setToOrtho2D(0f, 0f, w, h);
        float scale = Math.max(0.90f, Math.min(1.46f, h / 720f));
        font.getData().setScale(scale);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(uiMatrix);
        if (mode == Mode.MENU) drawMenu(w, h);
        else drawGameHud(w, h);
    }

    private void drawMenu(int w, int h) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.012f, 0.018f, 0.014f, 0.54f);
        shapes.rect(0, 0, w, h);
        float panelW = Math.min(w * 0.68f, 860f);
        float panelH = Math.min(h * 0.68f, 560f);
        float px = (w - panelW) * 0.5f;
        float py = (h - panelH) * 0.5f;
        shapes.setColor(0.042f, 0.055f, 0.045f, 0.94f);
        shapes.rect(px, py, panelW, panelH);
        shapes.setColor(0.69f, 0.53f, 0.22f, 0.95f);
        shapes.rect(px, py + panelH - 5f, panelW, 5f);

        float bw = panelW * 0.27f;
        float bh = Math.max(52f, panelH * 0.12f);
        float gap = panelW * 0.035f;
        float by = py + panelH * 0.36f;
        float start = px + (panelW - bw * 3f - gap * 2f) * 0.5f;
        easyButton.set(start, by, bw, bh);
        normalButton.set(start + bw + gap, by, bw, bh);
        hardButton.set(start + (bw + gap) * 2f, by, bw, bh);
        drawButtonFill(easyButton, difficulty == 0);
        drawButtonFill(normalButton, difficulty == 1);
        drawButtonFill(hardButton, difficulty == 2);
        playButton.set(px + panelW * 0.28f, py + panelH * 0.13f, panelW * 0.44f, bh * 1.08f);
        shapes.setColor(0.78f, 0.60f, 0.22f, 0.98f);
        shapes.rect(playButton.x, playButton.y, playButton.width, playButton.height);
        shapes.end();

        sprites.setProjectionMatrix(uiMatrix);
        sprites.begin();
        font.setColor(0.96f, 0.85f, 0.58f, 1f);
        float old = font.getData().scaleX;
        font.getData().setScale(old * 2.05f);
        font.draw(sprites, "IMPERIUM VALE", px, py + panelH * 0.83f, panelW, Align.center, false);
        font.getData().setScale(old);
        font.setColor(0.82f, 0.84f, 0.77f, 1f);
        font.draw(sprites, "Economia, construcao e guerra em tempo real", px, py + panelH * 0.69f, panelW, Align.center, false);
        font.draw(sprites, "FACIL", easyButton.x, easyButton.y + easyButton.height * 0.62f, easyButton.width, Align.center, false);
        font.draw(sprites, "NORMAL", normalButton.x, normalButton.y + normalButton.height * 0.62f, normalButton.width, Align.center, false);
        font.draw(sprites, "DIFICIL", hardButton.x, hardButton.y + hardButton.height * 0.62f, hardButton.width, Align.center, false);
        font.setColor(0.08f, 0.07f, 0.04f, 1f);
        font.draw(sprites, "JOGAR", playButton.x, playButton.y + playButton.height * 0.63f, playButton.width, Align.center, false);
        font.setColor(0.58f, 0.62f, 0.56f, 1f);
        font.getData().setScale(old * 0.78f);
        font.draw(sprites, VERSION, px, py + 24f, panelW, Align.center, false);
        font.getData().setScale(old);
        sprites.end();
    }

    private void drawButtonFill(Rectangle r, boolean active) {
        if (active) shapes.setColor(0.65f, 0.51f, 0.24f, 0.98f);
        else shapes.setColor(0.13f, 0.17f, 0.14f, 0.96f);
        shapes.rect(r.x, r.y, r.width, r.height);
    }

    private void drawGameHud(int w, int h) {
        float top = Math.max(60f, h * 0.095f);
        float bottom = Math.max(80f, h * 0.14f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.018f, 0.026f, 0.021f, 0.92f);
        shapes.rect(0, h - top, w, top);
        shapes.rect(0, 0, w, bottom);
        shapes.setColor(0.64f, 0.53f, 0.29f, 0.72f);
        shapes.rect(0, h - top, w, 2.5f);
        shapes.rect(0, bottom - 2.5f, w, 2.5f);

        float bw = Math.min(178f, w * 0.165f);
        float bh = bottom * 0.70f;
        float gap = 9f;
        float total = bw * 4f + gap * 3f;
        float x = w - total - 16f;
        float y = (bottom - bh) * 0.5f;
        villagerButton.set(x, y, bw, bh); x += bw + gap;
        swordButton.set(x, y, bw, bh); x += bw + gap;
        archerButton.set(x, y, bw, bh); x += bw + gap;
        cavalryButton.set(x, y, bw, bh);
        drawButtonFill(villagerButton, false);
        drawButtonFill(swordButton, false);
        drawButtonFill(archerButton, false);
        drawButtonFill(cavalryButton, false);

        float mapW = Math.min(210f, w * 0.185f);
        float mapH = bottom * 0.76f;
        float mapY = (bottom - mapH) * 0.5f;
        shapes.setColor(0.062f, 0.092f, 0.062f, 0.98f);
        shapes.rect(10f, mapY, mapW, mapH);
        for (ResourceNode node : resources) {
            if (node.amount <= 0f) continue;
            float mx = 10f + ((node.pos.x + WORLD) / (WORLD * 2f)) * mapW;
            float my = mapY + ((node.pos.z + WORLD) / (WORLD * 2f)) * mapH;
            if (node.type == ResourceType.GOLD) shapes.setColor(0.95f, 0.70f, 0.12f, 1f);
            else if (node.type == ResourceType.WOOD) shapes.setColor(0.32f, 0.62f, 0.21f, 1f);
            else shapes.setColor(0.78f, 0.30f, 0.28f, 1f);
            shapes.circle(mx, my, 2.1f, 7);
        }
        for (Unit u : units) {
            float mx = 10f + ((u.pos.x + WORLD) / (WORLD * 2f)) * mapW;
            float my = mapY + ((u.pos.z + WORLD) / (WORLD * 2f)) * mapH;
            if (u.team == PLAYER) shapes.setColor(0.18f, 0.48f, 0.96f, 1f);
            else shapes.setColor(0.92f, 0.20f, 0.12f, 1f);
            shapes.circle(mx, my, 2.5f, 7);
        }
        shapes.end();

        if (hasSelectedVillager()) drawBuildButtons(w, h, bottom);
        drawHealthBars(w, h);

        sprites.setProjectionMatrix(uiMatrix);
        sprites.begin();
        font.setColor(0.94f, 0.89f, 0.76f, 1f);
        font.draw(sprites, "COMIDA " + (int)food + "    MADEIRA " + (int)wood + "    OURO " + (int)gold
            + "    POP " + countTeam(PLAYER) + "/" + populationCap, 18f, h - top * 0.40f);
        font.setColor(0.76f, 0.81f, 0.73f, 1f);
        String baseText = playerStronghold == null ? "" : "FORTALEZA " + (int)playerStronghold.hp + "/" + (int)playerStronghold.maxHp;
        font.draw(sprites, baseText, w * 0.50f - 190f, h - top * 0.40f, 380f, Align.center, false);
        font.draw(sprites, Math.round(Math.min(99f, fpsSmooth)) + " FPS  " + (lowQuality ? "LOD" : "HD"), w - 190f, h - top * 0.40f);

        drawHudLabel("ALDEAO", "50 C", villagerButton);
        drawHudLabel("ESPADA", "60 C 20 O", swordButton);
        drawHudLabel("ARQUEIRO", "45 M 30 O", archerButton);
        drawHudLabel("CAVALO", "90 C 60 O", cavalryButton);

        if (!trainQueue.isEmpty()) {
            font.setColor(0.83f, 0.77f, 0.60f, 1f);
            font.draw(sprites, "FILA " + trainQueue.size() + "  " + Math.max(0, (int)Math.ceil(trainQueue.get(0).remaining)) + "s", mapW + 24f, 28f);
        }
        if (pendingBuild != null) {
            font.setColor(0.62f, 0.90f, 0.62f, 1f);
            font.draw(sprites, "TOQUE NO TERRENO PARA CONSTRUIR " + pendingBuild.name(), 0, bottom + 28f, w, Align.center, false);
        }
        font.setColor(0.48f, 0.53f, 0.46f, 1f);
        float old = font.getData().scaleX;
        font.getData().setScale(old * 0.70f);
        font.draw(sprites, VERSION, 12f, bottom + 18f);
        font.getData().setScale(old);

        if (gameOver) {
            font.setColor(endText.equals("VITORIA") ? new Color(0.95f, 0.78f, 0.28f, 1f) : new Color(0.95f, 0.28f, 0.22f, 1f));
            float s = font.getData().scaleX;
            font.getData().setScale(s * 2.2f);
            font.draw(sprites, endText, 0, h * 0.57f, w, Align.center, false);
            font.getData().setScale(s);
            font.setColor(Color.WHITE);
            font.draw(sprites, "Toque para voltar ao menu", 0, h * 0.48f, w, Align.center, false);
        }
        sprites.end();
    }

    private void drawBuildButtons(int w, int h, float bottom) {
        float bw = Math.min(150f, w * 0.13f);
        float bh = Math.max(48f, h * 0.07f);
        float x = 16f;
        float y = bottom + 18f;
        houseButton.set(x, y, bw, bh);
        barracksButton.set(x + bw + 8f, y, bw, bh);
        towerButton.set(x + (bw + 8f) * 2f, y, bw, bh);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawButtonFill(houseButton, pendingBuild == BuildingType.HOUSE);
        drawButtonFill(barracksButton, pendingBuild == BuildingType.BARRACKS);
        drawButtonFill(towerButton, pendingBuild == BuildingType.TOWER);
        shapes.end();

        sprites.setProjectionMatrix(uiMatrix);
        sprites.begin();
        font.setColor(0.90f, 0.84f, 0.70f, 1f);
        font.draw(sprites, "CASA 100M", houseButton.x, houseButton.y + bh * 0.60f, bw, Align.center, false);
        font.draw(sprites, "QUARTEL 150M", barracksButton.x, barracksButton.y + bh * 0.60f, bw, Align.center, false);
        font.draw(sprites, "TORRE 120M 80O", towerButton.x, towerButton.y + bh * 0.60f, bw, Align.center, false);
        sprites.end();
    }

    private void drawHealthBars(int w, int h) {
        shapes.setProjectionMatrix(uiMatrix);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Unit u : units) {
            if (u.hp >= u.maxHp && !selected.contains(u.id)) continue;
            tmpScreen.set(u.pos.x, u.type == UnitType.CAVALRY ? 3.15f : 2.45f, u.pos.z);
            camera.project(tmpScreen);
            float sx = tmpScreen.x;
            float sy = tmpScreen.y;
            if (sx < -40f || sx > w + 40f || sy < 0f || sy > h) continue;
            float barW = u.type == UnitType.CAVALRY ? 46f : 38f;
            shapes.setColor(0.12f, 0.08f, 0.08f, 0.88f);
            shapes.rect(sx - barW * 0.5f, sy, barW, 5f);
            float ratio = Math.max(0f, u.hp / u.maxHp);
            shapes.setColor(ratio > 0.55f ? 0.30f : 0.88f, ratio > 0.55f ? 0.72f : 0.30f, 0.20f, 0.96f);
            shapes.rect(sx - barW * 0.5f, sy, barW * ratio, 5f);
        }
        drawStrongholdBar(playerStronghold, w, h);
        drawStrongholdBar(enemyStronghold, w, h);
        shapes.end();
    }

    private void drawStrongholdBar(Stronghold s, int w, int h) {
        if (s == null) return;
        tmpScreen.set(s.pos.x, 8.8f, s.pos.z);
        camera.project(tmpScreen);
        float sx = tmpScreen.x;
        float sy = tmpScreen.y;
        if (sx < -100f || sx > w + 100f || sy < 0f || sy > h) return;
        float barW = 100f;
        shapes.setColor(0.10f, 0.07f, 0.06f, 0.90f);
        shapes.rect(sx - barW * 0.5f, sy, barW, 8f);
        float ratio = Math.max(0f, s.hp / s.maxHp);
        if (s.team == PLAYER) shapes.setColor(0.20f, 0.55f, 0.95f, 0.98f);
        else shapes.setColor(0.92f, 0.20f, 0.12f, 0.98f);
        shapes.rect(sx - barW * 0.5f, sy, barW * ratio, 8f);
    }

    private void drawHudLabel(String title, String price, Rectangle r) {
        font.setColor(0.90f, 0.85f, 0.72f, 1f);
        font.draw(sprites, title, r.x, r.y + r.height * 0.68f, r.width, Align.center, false);
        float scale = font.getData().scaleX;
        font.getData().setScale(scale * 0.82f);
        font.setColor(0.72f, 0.69f, 0.56f, 1f);
        font.draw(sprites, price, r.x, r.y + r.height * 0.31f, r.width, Align.center, false);
        font.getData().setScale(scale);
    }

    private boolean hasSelectedVillager() {
        for (Integer id : selected) {
            Unit u = findById(id);
            if (u != null && u.type == UnitType.VILLAGER) return true;
        }
        return false;
    }

    private boolean inside(Rectangle r, float x, float y) {
        return r.contains(x, y);
    }

    private void train(UnitType type, float f, float w, float g, float seconds) {
        if (countTeam(PLAYER) + queuedPopulation() >= populationCap) return;
        if (trainQueue.size() >= 8) return;
        if (type != UnitType.VILLAGER && barracksCount <= 0) return;
        if (food < f || wood < w || gold < g) return;
        food -= f;
        wood -= w;
        gold -= g;
        trainQueue.add(new TrainOrder(type, seconds));
    }

    private void selectBuild(BuildingType type) {
        if (!hasSelectedVillager()) return;
        pendingBuild = pendingBuild == type ? null : type;
    }

    private boolean placePendingBuilding(float x, float z) {
        if (pendingBuild == null) return false;
        if (Vector2.dst(x, z, playerStronghold.pos.x, playerStronghold.pos.z) > 25f) return false;
        if (Math.abs(x - 4f) < 5.4f) return false;
        for (Building b : buildings) if (Vector2.dst(x, z, b.pos.x, b.pos.z) < 4.5f) return false;
        if (pendingBuild == BuildingType.HOUSE) {
            if (wood < 100f) return false;
            wood -= 100f;
        } else if (pendingBuild == BuildingType.BARRACKS) {
            if (wood < 150f) return false;
            wood -= 150f;
        } else {
            if (wood < 120f || gold < 80f) return false;
            wood -= 120f;
            gold -= 80f;
        }
        createBuilding(pendingBuild, x, z);
        pendingBuild = null;
        return true;
    }

    private void handleTap(float x, float y) {
        int h = Gdx.graphics.getHeight();
        float uy = h - y;
        if (mode == Mode.MENU) {
            if (inside(easyButton, x, uy)) difficulty = 0;
            else if (inside(normalButton, x, uy)) difficulty = 1;
            else if (inside(hardButton, x, uy)) difficulty = 2;
            else if (inside(playButton, x, uy)) resetGame();
            return;
        }
        if (gameOver) {
            mode = Mode.MENU;
            units.clear();
            resources.clear();
            buildings.clear();
            trainQueue.clear();
            projectiles.clear();
            selected.clear();
            resetMenuCamera();
            return;
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
                boolean doubleTap = hit.id == lastTapUnit && now - lastTapMs < 330;
                selected.clear();
                if (doubleTap) {
                    for (Unit u : units) {
                        if (u.team == PLAYER && u.type == hit.type && Vector2.dst2(u.pos.x, u.pos.z, hit.pos.x, hit.pos.z) < 256f) selected.add(u.id);
                    }
                } else selected.add(hit.id);
                pendingBuild = null;
                lastTapUnit = hit.id;
                lastTapMs = now;
            } else if (!selected.isEmpty()) issueUnitAttack(hit.id);
            return;
        }

        ResourceNode resource = findResourceAtScreen(x, y);
        if (resource != null && !selected.isEmpty()) {
            boolean assigned = false;
            for (Integer id : selected) {
                Unit u = findById(id);
                if (u != null && u.type == UnitType.VILLAGER) {
                    assignGather(u, resource);
                    assigned = true;
                }
            }
            if (assigned) return;
        }

        int baseHit = findStrongholdAtScreen(x, y);
        if (baseHit == ENEMY && !selected.isEmpty()) {
            for (Integer id : selected) {
                Unit u = findById(id);
                if (u == null) continue;
                u.gatherNode = null;
                u.targetUnitId = -1;
                u.targetStronghold = ENEMY;
                u.moving = false;
            }
            return;
        }

        Ray ray = camera.getPickRay(x, y);
        if (!Intersector.intersectRayPlane(ray, groundPlane, tmp3)) return;
        if (pendingBuild != null && placePendingBuilding(tmp3.x, tmp3.z)) return;
        if (selected.isEmpty()) return;
        issueMove(tmp3.x, tmp3.z);
    }

    private void issueUnitAttack(int enemyId) {
        for (Integer id : selected) {
            Unit u = findById(id);
            if (u != null) {
                u.gatherNode = null;
                u.targetUnitId = enemyId;
                u.targetStronghold = -1;
                u.moving = false;
            }
        }
    }

    private void issueMove(float x, float z) {
        List<Unit> group = new ArrayList<>();
        for (Integer id : selected) {
            Unit u = findById(id);
            if (u != null) group.add(u);
        }
        group.sort(Comparator.comparingInt(a -> a.id));
        int cols = Math.max(1, (int)Math.ceil(Math.sqrt(group.size())));
        float spacing = 1.36f;
        for (int i = 0; i < group.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            Unit u = group.get(i);
            u.gatherNode = null;
            u.target.set(
                x + (col - (cols - 1) * 0.5f) * spacing,
                0f,
                z + (row - (cols - 1) * 0.5f) * spacing
            );
            clearTargets(u);
            u.moving = true;
        }
        commandMarker.set(x, 0f, z);
        commandMarkerTime = 1f;
    }

    private Unit findUnitAtScreen(float x, float inputY) {
        float best = 64f;
        Unit result = null;
        int h = Gdx.graphics.getHeight();
        for (Unit u : units) {
            tmpScreen.set(u.pos.x, u.type == UnitType.CAVALRY ? 1.8f : 1.25f, u.pos.z);
            camera.project(tmpScreen);
            float dy = (h - tmpScreen.y) - inputY;
            float dx = tmpScreen.x - x;
            float d = (float)Math.sqrt(dx * dx + dy * dy);
            if (d < best) {
                best = d;
                result = u;
            }
        }
        return result;
    }

    private ResourceNode findResourceAtScreen(float x, float inputY) {
        float best = 82f;
        ResourceNode result = null;
        int h = Gdx.graphics.getHeight();
        for (ResourceNode node : resources) {
            if (node.amount <= 0f) continue;
            tmpScreen.set(node.pos.x, 1.1f, node.pos.z);
            camera.project(tmpScreen);
            float dx = tmpScreen.x - x;
            float dy = (h - tmpScreen.y) - inputY;
            float d = (float)Math.sqrt(dx * dx + dy * dy);
            if (d < best) {
                best = d;
                result = node;
            }
        }
        return result;
    }

    private int findStrongholdAtScreen(float x, float inputY) {
        int h = Gdx.graphics.getHeight();
        Stronghold[] bases = {playerStronghold, enemyStronghold};
        int bestTeam = -1;
        float best = 105f;
        for (Stronghold s : bases) {
            if (s == null || s.hp <= 0f) continue;
            tmpScreen.set(s.pos.x, 2.5f, s.pos.z);
            camera.project(tmpScreen);
            float dy = (h - tmpScreen.y) - inputY;
            float dx = tmpScreen.x - x;
            float d = (float)Math.sqrt(dx * dx + dy * dy);
            if (d < best) {
                best = d;
                bestTeam = s.team;
            }
        }
        return bestTeam;
    }

    private final class GameInput extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (pointer == 0) {
                downX = lastX = screenX;
                downY = lastY = screenY;
                dragged = false;
            }
            if (Gdx.input.isTouched(0) && Gdx.input.isTouched(1)) {
                pinchDistance = pointerDistance();
                pinchCameraDistance = cameraDistance;
            }
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (mode != Mode.GAME || gameOver) return true;
            if (Gdx.input.isTouched(0) && Gdx.input.isTouched(1)) {
                float d = pointerDistance();
                if (pinchDistance > 20f && d > 20f) cameraDistance = clamp(pinchCameraDistance * (pinchDistance / d), 18f, 58f);
                dragged = true;
                return true;
            }
            if (pointer == 0) {
                float dx = screenX - lastX;
                float dy = screenY - lastY;
                if (Vector2.dst(downX, downY, screenX, screenY) > 14f) dragged = true;
                if (dragged) {
                    float factor = cameraDistance * 0.00215f;
                    cameraTarget.x -= (dx * 0.72f - dy * 0.42f) * factor;
                    cameraTarget.z -= (dx * 0.72f + dy * 0.42f) * factor;
                    cameraTarget.x = clamp(cameraTarget.x, -WORLD + 5f, WORLD - 5f);
                    cameraTarget.z = clamp(cameraTarget.z, -WORLD + 5f, WORLD - 5f);
                }
                lastX = screenX;
                lastY = screenY;
            }
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (pointer == 0 && !dragged) handleTap(screenX, screenY);
            if (!Gdx.input.isTouched(0) || !Gdx.input.isTouched(1)) pinchDistance = 0f;
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.BACK && mode == Mode.GAME) {
                if (pendingBuild != null) {
                    pendingBuild = null;
                    return true;
                }
                mode = Mode.MENU;
                units.clear();
                resources.clear();
                buildings.clear();
                trainQueue.clear();
                projectiles.clear();
                selected.clear();
                resetMenuCamera();
                return true;
            }
            return false;
        }
    }

    private float pointerDistance() {
        if (!Gdx.input.isTouched(0) || !Gdx.input.isTouched(1)) return 0f;
        return Vector2.dst(Gdx.input.getX(0), Gdx.input.getY(0), Gdx.input.getX(1), Gdx.input.getY(1));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    @Override
    public void dispose() {
        if (modelBatch != null) modelBatch.dispose();
        if (shapes != null) shapes.dispose();
        if (sprites != null) sprites.dispose();
        if (font != null) font.dispose();
        for (Chunk chunk : chunks) if (chunk.cache != null) chunk.cache.dispose();
        for (Model model : ownedModels) model.dispose();
        for (Texture texture : ownedTextures) texture.dispose();
    }
}
