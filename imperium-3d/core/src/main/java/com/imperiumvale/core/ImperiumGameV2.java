package com.imperiumvale.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
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
import java.util.List;
import java.util.Set;

/**
 * Imperium Vale 3D - second-generation mobile RTS renderer/gameplay core.
 *
 * Goals: strong visual readability on a phone, low draw-call pressure, stable
 * Android lifecycle behavior, touch-first RTS controls and a real stronghold
 * objective instead of a simple unit-death match.
 */
public final class ImperiumGameV2 extends ApplicationAdapter {
    private static final long ATTR = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
    private static final int PLAYER = 0;
    private static final int ENEMY = 1;
    private static final float WORLD = 39f;
    private static final float MAX_DT = 1f / 20f;
    private static final float DEG = 0.017453292f;

    private enum Mode { MENU, GAME }
    private enum UnitType { VILLAGER, SWORD, ARCHER, CAVALRY }

    private static final class Stronghold {
        final int team;
        final Vector3 pos = new Vector3();
        float hp = 1800f;
        final float maxHp = 1800f;

        Stronghold(int team, float x, float z) {
            this.team = team;
            pos.set(x, 0f, z);
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
        boolean moving;
        int targetUnitId = -1;
        int targetStronghold = -1;
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
                    speed = 4.3f;
                    damage = 6f;
                    range = 0.95f;
                    attackPeriod = 1.0f;
                }
                case SWORD -> {
                    maxHp = 128f;
                    speed = 4.0f;
                    damage = 20f;
                    range = 1.05f;
                    attackPeriod = 0.85f;
                }
                case ARCHER -> {
                    maxHp = 82f;
                    speed = 4.1f;
                    damage = 15f;
                    range = 6.6f;
                    attackPeriod = 1.05f;
                }
                case CAVALRY -> {
                    maxHp = 178f;
                    speed = 6.3f;
                    damage = 28f;
                    range = 1.4f;
                    attackPeriod = 0.95f;
                }
            }
            hp = maxHp;
        }
    }

    private ModelBatch modelBatch;
    private ModelCache staticCache;
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
    private final Array<ModelInstance> staticWorld = new Array<>();
    private final List<Unit> units = new ArrayList<>();
    private final Set<Integer> selected = new HashSet<>();

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

    private Stronghold playerStronghold;
    private Stronghold enemyStronghold;

    private Mode mode = Mode.MENU;
    private int difficulty = 1;
    private boolean gameOver;
    private String endText = "";
    private int nextId = 1;

    private float food = 540f;
    private float wood = 500f;
    private float gold = 370f;
    private float incomeAccumulator;
    private float aiAccumulator;
    private float menuOrbit;
    private float cameraDistance = 43f;
    private float cameraYaw = 43f;
    private float cameraPitch = 48f;
    private float fpsSmooth = 60f;
    private final Vector3 commandMarker = new Vector3();
    private float commandMarkerTime;

    private final Rectangle easyButton = new Rectangle();
    private final Rectangle normalButton = new Rectangle();
    private final Rectangle hardButton = new Rectangle();
    private final Rectangle playButton = new Rectangle();
    private final Rectangle villagerButton = new Rectangle();
    private final Rectangle swordButton = new Rectangle();
    private final Rectangle archerButton = new Rectangle();
    private final Rectangle cavalryButton = new Rectangle();

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
        staticCache = new ModelCache();
        shapes = new ShapeRenderer();
        sprites = new SpriteBatch();
        font = new BitmapFont();
        font.getData().markupEnabled = false;

        camera = new PerspectiveCamera(38f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.25f;
        camera.far = 170f;

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.47f, 0.49f, 0.43f, 1f));
        environment.set(new ColorAttribute(ColorAttribute.Fog, 0.66f, 0.72f, 0.68f, 1f));
        environment.add(new DirectionalLight().set(1.00f, 0.94f, 0.81f, -0.58f, -0.78f, -0.35f));
        environment.add(new DirectionalLight().set(0.18f, 0.23f, 0.32f, 0.45f, -0.40f, 0.38f));

        buildSharedModels();
        buildWorld();
        rebuildStaticCache();
        resetMenuCamera();
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        Gdx.input.setInputProcessor(new GameInput());
    }

    private Material material(float r, float g, float b) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
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
        blueBody = own(mb.createCapsule(0.36f, 1.38f, 12, material(0.10f, 0.31f, 0.74f), ATTR));
        redBody = own(mb.createCapsule(0.36f, 1.38f, 12, material(0.71f, 0.12f, 0.08f), ATTR));
        blueCavalryBody = own(mb.createCapsule(0.38f, 1.42f, 12, material(0.08f, 0.27f, 0.68f), ATTR));
        redCavalryBody = own(mb.createCapsule(0.38f, 1.42f, 12, material(0.67f, 0.10f, 0.07f), ATTR));
        headModel = own(mb.createSphere(0.44f, 0.44f, 0.44f, 12, 8, material(0.78f, 0.65f, 0.49f), ATTR));
        swordModel = own(mb.createBox(0.09f, 0.95f, 0.09f, material(0.78f, 0.80f, 0.81f), ATTR));
        bowModel = own(mb.createBox(0.07f, 0.92f, 0.20f, material(0.38f, 0.20f, 0.07f), ATTR));
        toolModel = own(mb.createBox(0.08f, 0.82f, 0.08f, material(0.35f, 0.20f, 0.08f), ATTR));
        blueShieldModel = own(mb.createCylinder(0.72f, 0.12f, 0.72f, 12, material(0.12f, 0.33f, 0.72f), ATTR));
        redShieldModel = own(mb.createCylinder(0.72f, 0.12f, 0.72f, 12, material(0.70f, 0.13f, 0.09f), ATTR));
        horseModel = own(mb.createBox(1.45f, 0.72f, 0.62f, material(0.30f, 0.17f, 0.08f), ATTR));
        shadowModel = own(mb.createCylinder(1.30f, 0.025f, 0.80f, 14, alphaMaterial(0.03f, 0.035f, 0.03f, 0.33f), ATTR));
    }

    private void buildWorld() {
        staticWorld.clear();
        ModelBuilder mb = new ModelBuilder();

        Model[] grass = new Model[] {
            own(mb.createBox(6.05f, 0.36f, 6.05f, material(0.31f, 0.47f, 0.20f), ATTR)),
            own(mb.createBox(6.05f, 0.36f, 6.05f, material(0.34f, 0.50f, 0.22f), ATTR)),
            own(mb.createBox(6.05f, 0.36f, 6.05f, material(0.29f, 0.44f, 0.18f), ATTR)),
            own(mb.createBox(6.05f, 0.36f, 6.05f, material(0.36f, 0.49f, 0.23f), ATTR))
        };
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                int h = Math.abs(x * 31 + z * 17 + x * z * 3) & 3;
                addStatic(grass[h], x * 6f, -0.22f, z * 6f, 0f, 1f, 1f, 1f);
            }
        }

        createRoad(mb);
        createRiver(mb);
        createBase(mb, PLAYER, -24f, -17f);
        createBase(mb, ENEMY, 24f, 17f);
        createForest(mb);
        createGoldMines(mb);
        createRockFields(mb);
    }

    private void createRoad(ModelBuilder mb) {
        Model road = own(mb.createBox(7.0f, 0.075f, 4.8f, material(0.47f, 0.35f, 0.20f), ATTR));
        float ax = -23f, az = -16f;
        float bx = 23f, bz = 16f;
        float dx = bx - ax, dz = bz - az;
        float yaw = (float)Math.toDegrees(Math.atan2(dx, dz));
        for (int i = 0; i <= 10; i++) {
            float t = i / 10f;
            float x = ax + dx * t;
            float z = az + dz * t + (float)Math.sin(t * 6.283f) * 0.7f;
            addStatic(road, x, 0.01f, z, yaw, 1f, 1f, 1f);
        }
    }

    private void createRiver(ModelBuilder mb) {
        Material waterMat = alphaMaterial(0.07f, 0.34f, 0.48f, 0.88f);
        Material shallowMat = alphaMaterial(0.18f, 0.48f, 0.57f, 0.72f);
        Model water = own(mb.createBox(7.8f, 0.06f, 9.4f, waterMat, ATTR));
        Model shallow = own(mb.createBox(9.1f, 0.035f, 9.4f, shallowMat, ATTR));
        Model bank = own(mb.createBox(1.0f, 0.24f, 9.4f, material(0.39f, 0.34f, 0.20f), ATTR));
        for (int i = -4; i <= 4; i++) {
            float z = i * 9.0f;
            float x = 4.0f + (float)Math.sin(i * 0.72f) * 1.5f;
            addStatic(shallow, x, -0.03f, z, -4f, 1f, 1f, 1f);
            addStatic(water, x, 0.005f, z, -4f, 1f, 1f, 1f);
            addStatic(bank, x - 4.55f, 0.04f, z, -4f, 1f, 1f, 1f);
            addStatic(bank, x + 4.55f, 0.04f, z, -4f, 1f, 1f, 1f);
        }

        Model bridgeDeck = own(mb.createBox(10.8f, 0.42f, 5.3f, material(0.37f, 0.21f, 0.09f), ATTR));
        Model bridgeRail = own(mb.createBox(10.8f, 0.58f, 0.24f, material(0.28f, 0.16f, 0.07f), ATTR));
        addStatic(bridgeDeck, 4.0f, 0.29f, 0.2f, -32f, 1f, 1f, 1f);
        addStatic(bridgeRail, 4.0f, 0.78f, -2.35f, -32f, 1f, 1f, 1f);
        addStatic(bridgeRail, 4.0f, 0.78f, 2.75f, -32f, 1f, 1f, 1f);
    }

    private void createBase(ModelBuilder mb, int team, float x, float z) {
        Color stone = team == PLAYER
            ? new Color(0.57f, 0.59f, 0.56f, 1f)
            : new Color(0.55f, 0.53f, 0.50f, 1f);
        Color darkStone = stone.cpy().mul(0.73f);
        Color roof = team == PLAYER
            ? new Color(0.08f, 0.26f, 0.64f, 1f)
            : new Color(0.62f, 0.12f, 0.075f, 1f);

        Model keep = own(mb.createBox(7.3f, 3.8f, 6.4f, new Material(ColorAttribute.createDiffuse(stone)), ATTR));
        addStatic(keep, x, 1.9f, z, 0f, 1f, 1f, 1f);

        Model battlement = own(mb.createBox(0.82f, 0.72f, 0.92f, new Material(ColorAttribute.createDiffuse(stone.cpy().mul(1.05f))), ATTR));
        for (int i = -3; i <= 3; i++) {
            addStatic(battlement, x + i * 1.0f, 4.15f, z - 2.8f, 0f, 1f, 1f, 1f);
            addStatic(battlement, x + i * 1.0f, 4.15f, z + 2.8f, 0f, 1f, 1f, 1f);
        }
        for (int i = -2; i <= 2; i++) {
            addStatic(battlement, x - 3.25f, 4.15f, z + i * 1.1f, 0f, 1f, 1f, 1f);
            addStatic(battlement, x + 3.25f, 4.15f, z + i * 1.1f, 0f, 1f, 1f, 1f);
        }

        Model tower = own(mb.createCylinder(2.25f, 6.3f, 2.25f, 14, new Material(ColorAttribute.createDiffuse(darkStone)), ATTR));
        Model towerRoof = own(mb.createCone(2.85f, 2.5f, 2.85f, 14, new Material(ColorAttribute.createDiffuse(roof)), ATTR));
        float[][] towerOffsets = {{-3.15f,-2.65f},{3.15f,2.65f}};
        for (float[] o : towerOffsets) {
            addStatic(tower, x + o[0], 3.15f, z + o[1], 0f, 1f, 1f, 1f);
            addStatic(towerRoof, x + o[0], 7.52f, z + o[1], 0f, 1f, 1f, 1f);
        }

        Model door = own(mb.createBox(1.30f, 2.25f, 0.18f, material(0.18f, 0.105f, 0.045f), ATTR));
        Model window = own(mb.createBox(0.48f, 0.82f, 0.15f, material(0.10f, 0.12f, 0.12f), ATTR));
        addStatic(door, x, 1.15f, z - 3.28f, 0f, 1f, 1f, 1f);
        addStatic(window, x - 2.0f, 2.6f, z - 3.29f, 0f, 1f, 1f, 1f);
        addStatic(window, x + 2.0f, 2.6f, z - 3.29f, 0f, 1f, 1f, 1f);

        Model pole = own(mb.createCylinder(0.10f, 4.4f, 0.10f, 6, material(0.24f, 0.20f, 0.14f), ATTR));
        Model flag = own(mb.createBox(1.65f, 0.82f, 0.08f, new Material(ColorAttribute.createDiffuse(roof)), ATTR));
        addStatic(pole, x, 6.0f, z, 0f, 1f, 1f, 1f);
        addStatic(flag, x + 0.82f, 7.45f, z, 0f, 1f, 1f, 1f);

        Model wall = own(mb.createBox(8.2f, 2.05f, 0.72f, new Material(ColorAttribute.createDiffuse(darkStone)), ATTR));
        addStatic(wall, x - 7.4f, 1.03f, z - 7.0f, 0f, 1f, 1f, 1f);
        addStatic(wall, x + 7.4f, 1.03f, z + 7.0f, 0f, 1f, 1f, 1f);
        addStatic(wall, x - 7.4f, 1.03f, z + 7.0f, 0f, 1f, 1f, 1f);
        addStatic(wall, x + 7.4f, 1.03f, z - 7.0f, 0f, 1f, 1f, 1f);

        createVillageBuildings(mb, team, x, z, roof);
    }

    private void createVillageBuildings(ModelBuilder mb, int team, float x, float z, Color roof) {
        Model house = own(mb.createBox(3.6f, 2.15f, 3.0f, material(0.62f, 0.51f, 0.34f), ATTR));
        Model roofModel = own(mb.createCone(4.45f, 2.15f, 3.75f, 4, new Material(ColorAttribute.createDiffuse(roof.cpy().mul(0.84f))), ATTR));
        Model chimney = own(mb.createBox(0.42f, 1.2f, 0.42f, material(0.36f, 0.29f, 0.23f), ATTR));
        float side = team == PLAYER ? 1f : -1f;
        for (int i = 0; i < 4; i++) {
            float sx = x + side * (6.2f + (i % 2) * 4.4f);
            float sz = z + (i / 2 == 0 ? -1f : 1f) * (3.1f + (i % 2) * 1.4f);
            addStatic(house, sx, 1.08f, sz, 0f, 1f, 1f, 1f);
            addStatic(roofModel, sx, 3.15f, sz, 45f, 1f, 1f, 1f);
            addStatic(chimney, sx + 0.8f, 3.65f, sz + 0.55f, 0f, 1f, 1f, 1f);
        }

        Model barracks = own(mb.createBox(5.1f, 2.3f, 3.6f, material(0.46f, 0.39f, 0.29f), ATTR));
        Model barracksRoof = own(mb.createCone(5.9f, 2.3f, 4.4f, 4, new Material(ColorAttribute.createDiffuse(roof.cpy().mul(0.72f))), ATTR));
        addStatic(barracks, x - side * 6.3f, 1.15f, z + side * 4.8f, 0f, 1f, 1f, 1f);
        addStatic(barracksRoof, x - side * 6.3f, 3.35f, z + side * 4.8f, 45f, 1f, 1f, 1f);
    }

    private void createForest(ModelBuilder mb) {
        Model trunk = own(mb.createCylinder(0.46f, 2.5f, 0.46f, 8, material(0.28f, 0.15f, 0.065f), ATTR));
        Model crownLow = own(mb.createCone(2.7f, 3.7f, 2.7f, 10, material(0.11f, 0.31f, 0.09f), ATTR));
        Model crownMid = own(mb.createCone(2.2f, 3.2f, 2.2f, 10, material(0.15f, 0.39f, 0.11f), ATTR));
        Model crownHigh = own(mb.createCone(1.6f, 2.6f, 1.6f, 10, material(0.20f, 0.45f, 0.13f), ATTR));
        for (int i = 0; i < 88; i++) {
            float x;
            float z;
            do {
                x = rng.nextFloat() * 76f - 38f;
                z = rng.nextFloat() * 76f - 38f;
            } while (nearBase(x, z) || Math.abs(x - 4.0f) < 5.8f || nearMainRoad(x, z));
            float s = 0.70f + rng.nextFloat() * 0.62f;
            addStatic(trunk, x, 1.22f * s, z, 0f, s, s, s);
            addStatic(crownLow, x, 3.15f * s, z, rng.nextFloat() * 360f, s, s, s);
            addStatic(crownMid, x, 4.30f * s, z, rng.nextFloat() * 360f, s, s, s);
            if ((i % 3) != 0) {
                addStatic(crownHigh, x, 5.25f * s, z, rng.nextFloat() * 360f, s, s, s);
            }
        }
    }

    private boolean nearBase(float x, float z) {
        return Vector2.dst(x, z, -24f, -17f) < 13f || Vector2.dst(x, z, 24f, 17f) < 13f;
    }

    private boolean nearMainRoad(float x, float z) {
        float expectedZ = x * (34f / 48f);
        return Math.abs(z - expectedZ) < 3.4f;
    }

    private void createGoldMines(ModelBuilder mb) {
        Model rock = own(mb.createSphere(1.45f, 0.96f, 1.18f, 10, 6, material(0.40f, 0.38f, 0.30f), ATTR));
        Model ore = own(mb.createSphere(0.48f, 0.34f, 0.40f, 8, 5, material(0.88f, 0.63f, 0.10f), ATTR));
        float[][] mines = {{-10f, 15f}, {14f, -10f}, {-31f, 19f}, {31f, -18f}};
        for (float[] m : mines) {
            for (int i = 0; i < 6; i++) {
                float a = i * 60f * DEG;
                float rx = m[0] + (float)Math.cos(a) * 1.35f;
                float rz = m[1] + (float)Math.sin(a) * 1.35f;
                addStatic(rock, rx, 0.48f, rz, i * 29f, 0.86f + i * 0.035f, 1f, 1f);
                addStatic(ore, rx + 0.28f, 0.94f, rz - 0.15f, i * 13f, 1f, 1f, 1f);
            }
        }
    }

    private void createRockFields(ModelBuilder mb) {
        Model rockA = own(mb.createSphere(1.1f, 0.70f, 0.92f, 8, 5, material(0.41f, 0.42f, 0.39f), ATTR));
        Model rockB = own(mb.createSphere(0.72f, 0.52f, 0.66f, 8, 5, material(0.52f, 0.51f, 0.46f), ATTR));
        for (int i = 0; i < 34; i++) {
            float x = rng.nextFloat() * 70f - 35f;
            float z = rng.nextFloat() * 70f - 35f;
            if (nearBase(x, z) || nearMainRoad(x, z) || Math.abs(x - 4f) < 6f) continue;
            float s = 0.45f + rng.nextFloat() * 0.75f;
            addStatic((i & 1) == 0 ? rockA : rockB, x, 0.28f * s, z, rng.nextFloat() * 180f, s, s, s);
        }
    }

    private void addStatic(Model model, float x, float y, float z, float yaw, float sx, float sy, float sz) {
        ModelInstance instance = new ModelInstance(model);
        instance.transform.setToTranslation(x, y, z).rotate(Vector3.Y, yaw).scale(sx, sy, sz);
        staticWorld.add(instance);
    }

    private void rebuildStaticCache() {
        staticCache.begin();
        for (ModelInstance i : staticWorld) staticCache.add(i);
        staticCache.end();
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
        selected.clear();
        nextId = 1;
        food = 540f;
        wood = 500f;
        gold = 370f;
        incomeAccumulator = 0f;
        aiAccumulator = 0f;
        gameOver = false;
        endText = "";
        commandMarkerTime = 0f;
        playerStronghold = new Stronghold(PLAYER, -24f, -17f);
        enemyStronghold = new Stronghold(ENEMY, 24f, 17f);

        cameraTarget.set(-17f, 0f, -11f);
        cameraDistance = 35f;
        cameraYaw = 43f;
        cameraPitch = 50f;

        for (int i = 0; i < 7; i++) spawn(PLAYER, UnitType.VILLAGER, -26f + i * 1.05f, -11.5f + (i & 1) * 1.1f);
        for (int i = 0; i < 7; i++) spawn(PLAYER, UnitType.SWORD, -18.5f + i * 1.0f, -13.2f + (i & 1) * 1.0f);
        for (int i = 0; i < 5; i++) spawn(PLAYER, UnitType.ARCHER, -19f + i * 1.1f, -9.8f);
        spawn(PLAYER, UnitType.CAVALRY, -21f, -6.8f);
        spawn(PLAYER, UnitType.CAVALRY, -19.4f, -6.3f);

        int extra = difficulty * 2;
        for (int i = 0; i < 8 + extra; i++) spawn(ENEMY, UnitType.SWORD, 18.5f + (i % 5) * 1.1f, 12.5f + (i / 5) * 1.1f);
        for (int i = 0; i < 5 + difficulty; i++) spawn(ENEMY, UnitType.ARCHER, 17.5f + i * 1.1f, 17.4f);
        for (int i = 0; i < 2 + difficulty; i++) spawn(ENEMY, UnitType.CAVALRY, 24.5f + i * 1.45f, 10.8f);
        updateCamera();
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
        if (u.type == UnitType.CAVALRY) {
            u.extra.transform.setToTranslation(u.pos.x, 0.72f, u.pos.z);
            u.body.transform.setToTranslation(u.pos.x, 1.62f, u.pos.z);
            u.head.transform.setToTranslation(u.pos.x, 2.52f, u.pos.z);
            u.weapon.transform.setToTranslation(u.pos.x + 0.48f, 1.80f, u.pos.z)
                .rotate(Vector3.Z, -26f);
            u.shadow.transform.setToTranslation(u.pos.x, 0.035f, u.pos.z).scale(1.45f, 1f, 1.15f);
            return;
        }

        u.body.transform.setToTranslation(u.pos.x, 0.94f, u.pos.z);
        u.head.transform.setToTranslation(u.pos.x, 1.82f, u.pos.z);
        u.shadow.transform.setToTranslation(u.pos.x, 0.035f, u.pos.z);
        if (u.weapon != null) {
            float rot = u.type == UnitType.SWORD ? -27f : u.type == UnitType.ARCHER ? 8f : 35f;
            u.weapon.transform.setToTranslation(u.pos.x + 0.43f, 1.13f, u.pos.z).rotate(Vector3.Z, rot);
        }
        if (u.extra != null) {
            u.extra.transform.setToTranslation(u.pos.x - 0.43f, 1.06f, u.pos.z)
                .rotate(Vector3.X, 90f);
        }
    }

    @Override
    public void render() {
        float rawDt = Math.max(0.0001f, Gdx.graphics.getDeltaTime());
        fpsSmooth = fpsSmooth * 0.94f + (1f / rawDt) * 0.06f;
        float dt = Math.min(MAX_DT, rawDt);
        commandMarkerTime = Math.max(0f, commandMarkerTime - dt);

        if (mode == Mode.GAME && !gameOver) updateGame(dt);
        else if (mode == Mode.MENU) {
            menuOrbit += dt * 1.5f;
            cameraYaw = 42f + (float)Math.sin(menuOrbit * 0.22f) * 5f;
        }
        updateCamera();

        ScreenUtils.clear(0.63f, 0.70f, 0.68f, 1f, true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);

        modelBatch.begin(camera);
        modelBatch.render(staticCache, environment);
        for (Unit u : units) renderUnit3d(u);
        modelBatch.end();

        drawWorldFeedback();
        drawUi();
    }

    private void renderUnit3d(Unit u) {
        modelBatch.render(u.shadow, environment);
        if (u.extra != null) modelBatch.render(u.extra, environment);
        modelBatch.render(u.body, environment);
        modelBatch.render(u.head, environment);
        if (u.weapon != null) modelBatch.render(u.weapon, environment);
    }

    private void updateGame(float dt) {
        incomeAccumulator += dt;
        if (incomeAccumulator >= 1f) {
            incomeAccumulator -= 1f;
            int villagers = 0;
            for (Unit u : units) {
                if (u.team == PLAYER && u.type == UnitType.VILLAGER && u.hp > 0f) villagers++;
            }
            food += villagers * 1.00f;
            wood += villagers * 0.80f;
            gold += villagers * 0.42f;
        }

        for (Unit u : units) {
            if (u.hp <= 0f) continue;
            u.cooldown -= dt;
            Unit enemy = findById(u.targetUnitId);
            if (enemy != null && enemy.hp > 0f && enemy.team != u.team) {
                attackUnitTarget(u, enemy, dt);
            } else if (u.targetStronghold >= 0) {
                Stronghold targetBase = stronghold(u.targetStronghold);
                if (targetBase != null && targetBase.hp > 0f && targetBase.team != u.team) {
                    attackStrongholdTarget(u, targetBase, dt);
                } else {
                    clearTargets(u);
                }
            } else if (u.moving) {
                moveToward(u, u.target.x, u.target.z, dt);
            }
            updateUnitTransform(u);
        }

        units.removeIf(u -> u.hp <= 0f);
        selected.removeIf(id -> findById(id) == null);
        separateUnits();
        updateAi(dt);
        checkEnd();
    }

    private void attackUnitTarget(Unit u, Unit enemy, float dt) {
        float d = Vector2.dst(u.pos.x, u.pos.z, enemy.pos.x, enemy.pos.z);
        if (d <= u.range) {
            u.moving = false;
            if (u.cooldown <= 0f) {
                enemy.hp -= u.damage;
                u.cooldown = u.attackPeriod;
            }
        } else {
            moveToward(u, enemy.pos.x, enemy.pos.z, dt);
        }
    }

    private void attackStrongholdTarget(Unit u, Stronghold targetBase, float dt) {
        float d = Vector2.dst(u.pos.x, u.pos.z, targetBase.pos.x, targetBase.pos.z);
        float attackRange = u.type == UnitType.ARCHER ? 8.0f : 4.5f;
        if (d <= attackRange) {
            u.moving = false;
            if (u.cooldown <= 0f) {
                targetBase.hp = Math.max(0f, targetBase.hp - u.damage * 0.72f);
                u.cooldown = u.attackPeriod;
            }
        } else {
            moveToward(u, targetBase.pos.x, targetBase.pos.z, dt);
        }
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
        float step = Math.min(d, u.speed * dt);
        u.pos.x += dx / d * step;
        u.pos.z += dz / d * step;
        u.pos.x = clamp(u.pos.x, -WORLD, WORLD);
        u.pos.z = clamp(u.pos.z, -WORLD, WORLD);
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
                float push = (0.90f - d) * 0.045f;
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
        float interval = difficulty == 0 ? 4.8f : difficulty == 1 ? 3.15f : 2.05f;
        if (aiAccumulator < interval) return;
        aiAccumulator = 0f;

        int enemyCount = countTeam(ENEMY);
        int limit = 26 + difficulty * 11;
        if (enemyCount < limit && enemyStronghold != null && enemyStronghold.hp > 0f) {
            int r = rng.nextInt(100);
            UnitType type = r < 42 ? UnitType.SWORD : r < 73 ? UnitType.ARCHER : UnitType.CAVALRY;
            spawn(ENEMY, type, 20f + rng.nextFloat() * 7f, 12f + rng.nextFloat() * 8f);
        }

        float aggression = difficulty == 0 ? 0.34f : difficulty == 1 ? 0.68f : 0.94f;
        for (Unit u : units) {
            if (u.team != ENEMY || rng.nextFloat() > aggression) continue;
            Unit nearest = nearestEnemy(u, PLAYER, 18f);
            if (nearest != null) {
                u.targetUnitId = nearest.id;
                u.targetStronghold = -1;
            } else {
                u.targetUnitId = -1;
                u.targetStronghold = PLAYER;
            }
        }
    }

    private Unit nearestEnemy(Unit from, int targetTeam, float maxDistance) {
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
        int n = 0;
        for (Unit u : units) if (u.team == team) n++;
        return n;
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
            float r = u.type == UnitType.CAVALRY ? 1.05f : 0.72f;
            drawGroundRing(u.pos.x, u.pos.z, r, 24);
        }
        if (commandMarkerTime > 0f) {
            float pulse = 0.55f + (1f - commandMarkerTime) * 0.55f;
            shapes.setColor(0.40f, 0.82f, 1.0f, Math.min(1f, commandMarkerTime * 1.8f));
            drawGroundRing(commandMarker.x, commandMarker.z, pulse, 26);
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
        float scale = Math.max(0.92f, Math.min(1.48f, h / 720f));
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
        shapes.setColor(0.015f, 0.020f, 0.016f, 0.52f);
        shapes.rect(0, 0, w, h);
        float panelW = Math.min(w * 0.68f, 860f);
        float panelH = Math.min(h * 0.68f, 560f);
        float px = (w - panelW) * 0.5f;
        float py = (h - panelH) * 0.5f;
        shapes.setColor(0.045f, 0.057f, 0.047f, 0.93f);
        shapes.rect(px, py, panelW, panelH);
        shapes.setColor(0.67f, 0.52f, 0.22f, 0.95f);
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
        shapes.setColor(0.76f, 0.58f, 0.22f, 0.98f);
        shapes.rect(playButton.x, playButton.y, playButton.width, playButton.height);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.66f, 0.58f, 0.38f, 0.88f);
        shapes.rect(px, py, panelW, panelH);
        shapes.rect(playButton.x, playButton.y, playButton.width, playButton.height);
        shapes.end();

        sprites.setProjectionMatrix(uiMatrix);
        sprites.begin();
        font.setColor(0.95f, 0.84f, 0.58f, 1f);
        float old = font.getData().scaleX;
        font.getData().setScale(old * 2.0f);
        font.draw(sprites, "IMPERIUM VALE", px, py + panelH * 0.83f, panelW, Align.center, false);
        font.getData().setScale(old);
        font.setColor(0.82f, 0.84f, 0.77f, 1f);
        font.draw(sprites, "Conquiste a fortaleza inimiga", px, py + panelH * 0.69f, panelW, Align.center, false);
        font.draw(sprites, "FACIL", easyButton.x, easyButton.y + easyButton.height * 0.62f, easyButton.width, Align.center, false);
        font.draw(sprites, "NORMAL", normalButton.x, normalButton.y + normalButton.height * 0.62f, normalButton.width, Align.center, false);
        font.draw(sprites, "DIFICIL", hardButton.x, hardButton.y + hardButton.height * 0.62f, hardButton.width, Align.center, false);
        font.setColor(0.08f, 0.07f, 0.045f, 1f);
        font.draw(sprites, "JOGAR", playButton.x, playButton.y + playButton.height * 0.63f, playButton.width, Align.center, false);
        sprites.end();
    }

    private void drawButtonFill(Rectangle r, boolean active) {
        if (active) shapes.setColor(0.63f, 0.50f, 0.25f, 0.98f);
        else shapes.setColor(0.13f, 0.17f, 0.14f, 0.96f);
        shapes.rect(r.x, r.y, r.width, r.height);
    }

    private void drawGameHud(int w, int h) {
        float top = Math.max(60f, h * 0.095f);
        float bottom = Math.max(78f, h * 0.135f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.020f, 0.027f, 0.022f, 0.91f);
        shapes.rect(0, h - top, w, top);
        shapes.rect(0, 0, w, bottom);
        shapes.setColor(0.64f, 0.53f, 0.29f, 0.72f);
        shapes.rect(0, h - top, w, 2.5f);
        shapes.rect(0, bottom - 2.5f, w, 2.5f);

        float bw = Math.min(180f, w * 0.17f);
        float bh = bottom * 0.72f;
        float gap = 10f;
        float total = bw * 4f + gap * 3f;
        float x = w - total - 18f;
        float y = (bottom - bh) * 0.5f;
        villagerButton.set(x, y, bw, bh); x += bw + gap;
        swordButton.set(x, y, bw, bh); x += bw + gap;
        archerButton.set(x, y, bw, bh); x += bw + gap;
        cavalryButton.set(x, y, bw, bh);
        drawButtonFill(villagerButton, false);
        drawButtonFill(swordButton, false);
        drawButtonFill(archerButton, false);
        drawButtonFill(cavalryButton, false);

        float mapW = Math.min(214f, w * 0.19f);
        float mapH = bottom * 0.78f;
        float mapY = (bottom - mapH) * 0.5f;
        shapes.setColor(0.065f, 0.095f, 0.065f, 0.98f);
        shapes.rect(12f, mapY, mapW, mapH);
        for (Unit u : units) {
            float mx = 12f + ((u.pos.x + WORLD) / (WORLD * 2f)) * mapW;
            float my = mapY + ((u.pos.z + WORLD) / (WORLD * 2f)) * mapH;
            shapes.setColor(u.team == PLAYER ? 0.18f : 0.92f, u.team == PLAYER ? 0.48f : 0.20f, u.team == PLAYER ? 0.96f : 0.12f, 1f);
            shapes.circle(mx, my, 2.7f, 8);
        }
        if (playerStronghold != null) {
            shapes.setColor(0.32f, 0.64f, 1f, 1f);
            shapes.rect(12f + ((playerStronghold.pos.x + WORLD) / (WORLD * 2f)) * mapW - 3f,
                mapY + ((playerStronghold.pos.z + WORLD) / (WORLD * 2f)) * mapH - 3f, 6f, 6f);
        }
        if (enemyStronghold != null) {
            shapes.setColor(1f, 0.30f, 0.18f, 1f);
            shapes.rect(12f + ((enemyStronghold.pos.x + WORLD) / (WORLD * 2f)) * mapW - 3f,
                mapY + ((enemyStronghold.pos.z + WORLD) / (WORLD * 2f)) * mapH - 3f, 6f, 6f);
        }
        shapes.end();

        drawHealthBars(w, h);

        sprites.setProjectionMatrix(uiMatrix);
        sprites.begin();
        font.setColor(0.94f, 0.89f, 0.76f, 1f);
        font.draw(sprites, "COMIDA " + (int)food + "    MADEIRA " + (int)wood + "    OURO " + (int)gold
            + "    POP " + countTeam(PLAYER) + "/70", 18f, h - top * 0.40f);
        font.setColor(0.77f, 0.81f, 0.73f, 1f);
        String baseText = playerStronghold == null ? "" : "FORTALEZA " + (int)playerStronghold.hp + "/" + (int)playerStronghold.maxHp;
        font.draw(sprites, baseText, w * 0.50f - 190f, h - top * 0.40f, 380f, Align.center, false);
        font.draw(sprites, Math.round(Math.min(99f, fpsSmooth)) + " FPS", w - 138f, h - top * 0.40f);

        drawHudLabel("ALDEAO", "50 C", villagerButton);
        drawHudLabel("ESPADA", "60 C 20 O", swordButton);
        drawHudLabel("ARQUEIRO", "45 M 30 O", archerButton);
        drawHudLabel("CAVALO", "90 C 60 O", cavalryButton);

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

    private boolean inside(Rectangle r, float x, float y) {
        return r.contains(x, y);
    }

    private void train(UnitType type, float f, float w, float g) {
        if (countTeam(PLAYER) >= 70) return;
        if (playerStronghold == null || playerStronghold.hp <= 0f) return;
        if (food < f || wood < w || gold < g) return;
        food -= f;
        wood -= w;
        gold -= g;
        spawn(PLAYER, type, -22.5f + rng.nextFloat() * 5f, -12.5f + rng.nextFloat() * 4f);
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
            selected.clear();
            resetMenuCamera();
            return;
        }

        if (inside(villagerButton, x, uy)) { train(UnitType.VILLAGER, 50, 0, 0); return; }
        if (inside(swordButton, x, uy)) { train(UnitType.SWORD, 60, 0, 20); return; }
        if (inside(archerButton, x, uy)) { train(UnitType.ARCHER, 0, 45, 30); return; }
        if (inside(cavalryButton, x, uy)) { train(UnitType.CAVALRY, 90, 0, 60); return; }

        Unit hit = findUnitAtScreen(x, y);
        long now = System.currentTimeMillis();
        if (hit != null) {
            if (hit.team == PLAYER) {
                boolean doubleTap = hit.id == lastTapUnit && now - lastTapMs < 330;
                selected.clear();
                if (doubleTap) {
                    for (Unit u : units) {
                        if (u.team == PLAYER && u.type == hit.type
                            && Vector2.dst2(u.pos.x, u.pos.z, hit.pos.x, hit.pos.z) < 256f) {
                            selected.add(u.id);
                        }
                    }
                } else {
                    selected.add(hit.id);
                }
                lastTapUnit = hit.id;
                lastTapMs = now;
            } else if (!selected.isEmpty()) {
                issueUnitAttack(hit.id);
            }
            return;
        }

        int baseHit = findStrongholdAtScreen(x, y);
        if (baseHit == ENEMY && !selected.isEmpty()) {
            for (Integer id : selected) {
                Unit u = findById(id);
                if (u == null) continue;
                u.targetUnitId = -1;
                u.targetStronghold = ENEMY;
                u.moving = false;
            }
            return;
        }

        if (selected.isEmpty()) return;
        Ray ray = camera.getPickRay(x, y);
        if (!Intersector.intersectRayPlane(ray, groundPlane, tmp3)) return;
        issueMove(tmp3.x, tmp3.z);
    }

    private void issueUnitAttack(int enemyId) {
        for (Integer id : selected) {
            Unit u = findById(id);
            if (u != null) {
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
        float spacing = 1.34f;
        for (int i = 0; i < group.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            Unit u = group.get(i);
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
                if (pinchDistance > 20f && d > 20f) {
                    cameraDistance = clamp(pinchCameraDistance * (pinchDistance / d), 18f, 58f);
                }
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
                mode = Mode.MENU;
                units.clear();
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
        if (staticCache != null) staticCache.dispose();
        if (shapes != null) shapes.dispose();
        if (sprites != null) sprites.dispose();
        if (font != null) font.dispose();
        for (Model m : ownedModels) m.dispose();
    }
}
