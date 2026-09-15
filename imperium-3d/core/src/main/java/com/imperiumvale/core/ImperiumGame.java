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
 * Imperium Vale 3D mobile RTS prototype.
 *
 * The scene uses real 3D geometry, directional lighting and a perspective
 * strategy camera. Everything is generated at runtime so the APK stays small.
 */
public final class ImperiumGame extends ApplicationAdapter {
    private static final long ATTR = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
    private static final int PLAYER = 0;
    private static final int ENEMY = 1;
    private static final float WORLD = 36f;
    private static final float MAX_DT = 1f / 20f;

    private enum Mode { MENU, GAME }
    private enum UnitType { VILLAGER, SWORD, ARCHER, CAVALRY }

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
        int targetId = -1;
        ModelInstance body;
        ModelInstance head;
        ModelInstance detail;

        Unit(int id, int team, UnitType type, float x, float z) {
            this.id = id;
            this.team = team;
            this.type = type;
            pos.set(x, 0f, z);
            target.set(pos);
            switch (type) {
                case VILLAGER -> {
                    maxHp = 65f; speed = 4.2f; damage = 5f; range = 0.9f; attackPeriod = 1.1f;
                }
                case SWORD -> {
                    maxHp = 115f; speed = 3.9f; damage = 18f; range = 1.0f; attackPeriod = 0.9f;
                }
                case ARCHER -> {
                    maxHp = 78f; speed = 4.1f; damage = 14f; range = 6.2f; attackPeriod = 1.15f;
                }
                case CAVALRY -> {
                    maxHp = 165f; speed = 6.1f; damage = 26f; range = 1.3f; attackPeriod = 1.0f;
                }
            }
            hp = maxHp;
        }
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
    private final Array<ModelInstance> staticWorld = new Array<>();
    private final List<Unit> units = new ArrayList<>();
    private final Set<Integer> selected = new HashSet<>();

    private Model blueBody;
    private Model redBody;
    private Model blueCavalry;
    private Model redCavalry;
    private Model headModel;
    private Model swordModel;
    private Model bowModel;
    private Model horseDetailModel;

    private Mode mode = Mode.MENU;
    private int difficulty = 1;
    private boolean gameOver;
    private String endText = "";
    private int nextId = 1;
    private float food = 520f;
    private float wood = 470f;
    private float gold = 350f;
    private float incomeAccumulator;
    private float aiAccumulator;
    private float menuOrbit;
    private float cameraDistance = 43f;
    private float cameraYaw = 43f;
    private float cameraPitch = 48f;
    private float fpsSmooth = 60f;

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
        shapes = new ShapeRenderer();
        sprites = new SpriteBatch();
        font = new BitmapFont();
        font.getData().markupEnabled = false;

        camera = new PerspectiveCamera(38f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.3f;
        camera.far = 150f;

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.48f, 0.50f, 0.43f, 1f));
        environment.set(new ColorAttribute(ColorAttribute.Fog, 0.67f, 0.73f, 0.68f, 1f));
        environment.add(new DirectionalLight().set(1.0f, 0.94f, 0.80f, -0.58f, -0.78f, -0.35f));
        environment.add(new DirectionalLight().set(0.18f, 0.24f, 0.34f, 0.45f, -0.45f, 0.40f));

        buildSharedModels();
        buildWorld();
        resetMenuCamera();
        Gdx.input.setInputProcessor(new GameInput());
    }

    private Material material(float r, float g, float b) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
    }

    private Model own(Model model) {
        ownedModels.add(model);
        return model;
    }

    private void buildSharedModels() {
        ModelBuilder mb = new ModelBuilder();
        blueBody = own(mb.createCapsule(0.34f, 1.35f, 12, material(0.12f, 0.35f, 0.78f), ATTR));
        redBody = own(mb.createCapsule(0.34f, 1.35f, 12, material(0.72f, 0.15f, 0.10f), ATTR));
        blueCavalry = own(mb.createCapsule(0.48f, 1.75f, 12, material(0.11f, 0.31f, 0.68f), ATTR));
        redCavalry = own(mb.createCapsule(0.48f, 1.75f, 12, material(0.67f, 0.13f, 0.09f), ATTR));
        headModel = own(mb.createSphere(0.43f, 0.43f, 0.43f, 12, 8, material(0.72f, 0.61f, 0.48f), ATTR));
        swordModel = own(mb.createBox(0.08f, 0.85f, 0.08f, material(0.72f, 0.74f, 0.76f), ATTR));
        bowModel = own(mb.createBox(0.06f, 0.78f, 0.18f, material(0.34f, 0.18f, 0.07f), ATTR));
        horseDetailModel = own(mb.createBox(1.15f, 0.55f, 0.50f, material(0.25f, 0.13f, 0.07f), ATTR));
    }

    private void buildWorld() {
        staticWorld.clear();
        ModelBuilder mb = new ModelBuilder();

        Model grass = own(mb.createBox(78f, 0.45f, 78f, material(0.30f, 0.43f, 0.18f), ATTR));
        ModelInstance ground = new ModelInstance(grass);
        ground.transform.setToTranslation(0f, -0.26f, 0f);
        staticWorld.add(ground);

        Model roadModel = own(mb.createBox(62f, 0.08f, 5.4f, material(0.45f, 0.34f, 0.20f), ATTR));
        ModelInstance road = new ModelInstance(roadModel);
        road.transform.setToTranslation(0f, 0.01f, 0f).rotate(Vector3.Y, -31f);
        staticWorld.add(road);

        Material waterMat = new Material(
            ColorAttribute.createDiffuse(new Color(0.09f, 0.34f, 0.49f, 0.82f)),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.82f)
        );
        Model riverModel = own(mb.createBox(7.4f, 0.05f, 76f, waterMat, ATTR));
        ModelInstance river = new ModelInstance(riverModel);
        river.transform.setToTranslation(3.5f, 0.03f, 0f).rotate(Vector3.Y, 7f);
        staticWorld.add(river);

        Model bridgeModel = own(mb.createBox(10f, 0.34f, 5.0f, material(0.37f, 0.20f, 0.09f), ATTR));
        ModelInstance bridge = new ModelInstance(bridgeModel);
        bridge.transform.setToTranslation(3.5f, 0.22f, 0.2f).rotate(Vector3.Y, -31f);
        staticWorld.add(bridge);

        createBase(mb, PLAYER, -22f, -16f);
        createBase(mb, ENEMY, 22f, 16f);
        createForest(mb);
        createGoldMines(mb);
    }

    private void createBase(ModelBuilder mb, int team, float x, float z) {
        Color stone = team == PLAYER ? new Color(0.52f, 0.56f, 0.58f, 1f) : new Color(0.49f, 0.48f, 0.45f, 1f);
        Color roof = team == PLAYER ? new Color(0.17f, 0.32f, 0.67f, 1f) : new Color(0.62f, 0.18f, 0.10f, 1f);

        Model keepBase = own(mb.createBox(6.3f, 3.2f, 5.5f, new Material(ColorAttribute.createDiffuse(stone)), ATTR));
        addStatic(keepBase, x, 1.6f, z, 0f, 1f);

        Model tower = own(mb.createCylinder(2.0f, 5.3f, 2.0f, 12, new Material(ColorAttribute.createDiffuse(stone.cpy().mul(0.88f))), ATTR));
        addStatic(tower, x - 2.4f, 2.65f, z - 2.1f, 0f, 1f);
        addStatic(tower, x + 2.4f, 2.65f, z + 2.1f, 0f, 1f);

        Model roofModel = own(mb.createCone(2.4f, 2.2f, 2.4f, 12, new Material(ColorAttribute.createDiffuse(roof)), ATTR));
        addStatic(roofModel, x - 2.4f, 6.0f, z - 2.1f, 0f, 1f);
        addStatic(roofModel, x + 2.4f, 6.0f, z + 2.1f, 0f, 1f);

        Model house = own(mb.createBox(3.4f, 2.0f, 2.8f, material(0.60f, 0.51f, 0.36f), ATTR));
        Model houseRoof = own(mb.createCone(4.1f, 2.0f, 3.5f, 4, new Material(ColorAttribute.createDiffuse(roof.cpy().mul(0.72f))), ATTR));
        for (int i = 0; i < 3; i++) {
            float sx = x + (team == PLAYER ? 1f : -1f) * (5.5f + i * 3.4f);
            float sz = z + (i - 1) * 3.5f;
            addStatic(house, sx, 1f, sz, 0f, 1f);
            addStatic(houseRoof, sx, 3.0f, sz, 45f, 1f);
        }

        Model wall = own(mb.createBox(12f, 1.8f, 0.65f, new Material(ColorAttribute.createDiffuse(stone.cpy().mul(0.76f))), ATTR));
        addStatic(wall, x, 0.9f, z - 6.5f, 0f, 1f);
        addStatic(wall, x, 0.9f, z + 6.5f, 0f, 1f);
    }

    private void createForest(ModelBuilder mb) {
        Model trunk = own(mb.createCylinder(0.42f, 2.4f, 0.42f, 8, material(0.29f, 0.16f, 0.07f), ATTR));
        Model crownA = own(mb.createCone(2.4f, 4.2f, 2.4f, 10, material(0.12f, 0.30f, 0.10f), ATTR));
        Model crownB = own(mb.createCone(1.8f, 3.2f, 1.8f, 10, material(0.16f, 0.38f, 0.12f), ATTR));
        for (int i = 0; i < 68; i++) {
            float x;
            float z;
            do {
                x = rng.nextFloat() * 68f - 34f;
                z = rng.nextFloat() * 68f - 34f;
            } while (nearBase(x, z) || Math.abs(x - 3.5f) < 5f);
            float s = 0.72f + rng.nextFloat() * 0.65f;
            addStatic(trunk, x, 1.15f * s, z, 0f, s);
            addStatic(crownA, x, 3.25f * s, z, rng.nextFloat() * 360f, s);
            if ((i & 1) == 0) addStatic(crownB, x, 4.35f * s, z, rng.nextFloat() * 360f, s);
        }
    }

    private boolean nearBase(float x, float z) {
        return Vector2.dst(x, z, -22f, -16f) < 11f || Vector2.dst(x, z, 22f, 16f) < 11f;
    }

    private void createGoldMines(ModelBuilder mb) {
        Model rock = own(mb.createSphere(1.3f, 0.9f, 1.1f, 10, 6, material(0.42f, 0.38f, 0.26f), ATTR));
        Model goldRock = own(mb.createSphere(0.44f, 0.30f, 0.38f, 8, 5, material(0.85f, 0.62f, 0.10f), ATTR));
        float[][] mines = {{-9f, 12f}, {13f, -8f}, {-28f, 17f}, {28f, -15f}};
        for (float[] m : mines) {
            for (int i = 0; i < 5; i++) {
                float a = i * 72f * 0.017453292f;
                float x = m[0] + (float)Math.cos(a) * 1.2f;
                float z = m[1] + (float)Math.sin(a) * 1.2f;
                addStatic(rock, x, 0.45f, z, i * 33f, 0.85f + i * 0.05f);
                addStatic(goldRock, x + 0.25f, 0.9f, z - 0.18f, i * 11f, 1f);
            }
        }
    }

    private void addStatic(Model model, float x, float y, float z, float yaw, float scale) {
        ModelInstance i = new ModelInstance(model);
        i.transform.setToTranslation(x, y, z).rotate(Vector3.Y, yaw).scale(scale, scale, scale);
        staticWorld.add(i);
    }

    private void resetMenuCamera() {
        cameraTarget.set(0f, 0f, 0f);
        cameraDistance = 47f;
        cameraYaw = 43f;
        cameraPitch = 48f;
        updateCamera();
    }

    private void resetGame() {
        mode = Mode.GAME;
        units.clear();
        selected.clear();
        nextId = 1;
        food = 520f;
        wood = 470f;
        gold = 350f;
        incomeAccumulator = 0f;
        aiAccumulator = 0f;
        gameOver = false;
        endText = "";
        cameraTarget.set(-16f, 0f, -11f);
        cameraDistance = 34f;
        cameraYaw = 43f;
        cameraPitch = 50f;

        for (int i = 0; i < 6; i++) spawn(PLAYER, UnitType.VILLAGER, -24f + i * 1.05f, -11f + (i & 1) * 1.1f);
        for (int i = 0; i < 6; i++) spawn(PLAYER, UnitType.SWORD, -17f + i * 1.0f, -13f + (i & 1) * 1.0f);
        for (int i = 0; i < 4; i++) spawn(PLAYER, UnitType.ARCHER, -18f + i * 1.1f, -10f);
        spawn(PLAYER, UnitType.CAVALRY, -20f, -7f);
        spawn(PLAYER, UnitType.CAVALRY, -18.5f, -6.5f);

        int extra = difficulty * 2;
        for (int i = 0; i < 7 + extra; i++) spawn(ENEMY, UnitType.SWORD, 18f + (i % 5) * 1.1f, 12f + (i / 5) * 1.1f);
        for (int i = 0; i < 4 + difficulty; i++) spawn(ENEMY, UnitType.ARCHER, 17f + i * 1.1f, 17f);
        for (int i = 0; i < 1 + difficulty; i++) spawn(ENEMY, UnitType.CAVALRY, 24f + i * 1.4f, 11f);
        updateCamera();
    }

    private Unit spawn(int team, UnitType type, float x, float z) {
        Unit u = new Unit(nextId++, team, type, x, z);
        Model bodyModel = type == UnitType.CAVALRY
            ? (team == PLAYER ? blueCavalry : redCavalry)
            : (team == PLAYER ? blueBody : redBody);
        u.body = new ModelInstance(bodyModel);
        u.head = new ModelInstance(headModel);
        if (type == UnitType.SWORD) u.detail = new ModelInstance(swordModel);
        else if (type == UnitType.ARCHER) u.detail = new ModelInstance(bowModel);
        else if (type == UnitType.CAVALRY) u.detail = new ModelInstance(horseDetailModel);
        updateUnitTransform(u);
        units.add(u);
        return u;
    }

    private void updateUnitTransform(Unit u) {
        float bodyY = u.type == UnitType.CAVALRY ? 1.25f : 0.90f;
        float headY = u.type == UnitType.CAVALRY ? 2.35f : 1.75f;
        u.body.transform.setToTranslation(u.pos.x, bodyY, u.pos.z);
        u.head.transform.setToTranslation(u.pos.x, headY, u.pos.z);
        if (u.detail != null) {
            if (u.type == UnitType.CAVALRY) {
                u.detail.transform.setToTranslation(u.pos.x, 0.75f, u.pos.z);
            } else {
                u.detail.transform.setToTranslation(u.pos.x + 0.42f, 1.10f, u.pos.z)
                    .rotate(Vector3.Z, u.type == UnitType.SWORD ? -24f : 0f);
            }
        }
    }

    @Override
    public void render() {
        float dt = Math.min(MAX_DT, Gdx.graphics.getDeltaTime());
        if (dt <= 0f) dt = 1f / 60f;
        fpsSmooth = fpsSmooth * 0.94f + (1f / dt) * 0.06f;

        if (mode == Mode.GAME && !gameOver) updateGame(dt);
        else if (mode == Mode.MENU) {
            menuOrbit += dt * 1.8f;
            cameraYaw = 43f + (float)Math.sin(menuOrbit * 0.22f) * 4f;
        }
        updateCamera();

        ScreenUtils.clear(0.61f, 0.69f, 0.66f, 1f, true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);

        modelBatch.begin(camera);
        for (ModelInstance i : staticWorld) modelBatch.render(i, environment);
        for (Unit u : units) renderUnit3d(u);
        modelBatch.end();

        drawSelectionRings();
        drawUi();
    }

    private void renderUnit3d(Unit u) {
        modelBatch.render(u.body, environment);
        modelBatch.render(u.head, environment);
        if (u.detail != null) modelBatch.render(u.detail, environment);
    }

    private void updateGame(float dt) {
        incomeAccumulator += dt;
        if (incomeAccumulator >= 1f) {
            incomeAccumulator -= 1f;
            int villagers = 0;
            for (Unit u : units) if (u.team == PLAYER && u.type == UnitType.VILLAGER && u.hp > 0f) villagers++;
            food += villagers * 0.9f;
            wood += villagers * 0.72f;
            gold += villagers * 0.38f;
        }

        for (Unit u : units) {
            if (u.hp <= 0f) continue;
            u.cooldown -= dt;
            Unit enemy = findById(u.targetId);
            if (enemy != null && enemy.hp > 0f && enemy.team != u.team) {
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
            } else {
                u.targetId = -1;
                if (u.moving) moveToward(u, u.target.x, u.target.z, dt);
            }
            updateUnitTransform(u);
        }

        units.removeIf(u -> u.hp <= 0f);
        selected.removeIf(id -> findById(id) == null);
        separateUnits();
        updateAi(dt);
        checkEnd();
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
                if (d2 < 0.04f || d2 > 0.75f) continue;
                float d = (float)Math.sqrt(d2);
                float push = (0.86f - d) * 0.045f;
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
        float interval = difficulty == 0 ? 4.8f : difficulty == 1 ? 3.25f : 2.15f;
        if (aiAccumulator < interval) return;
        aiAccumulator = 0f;

        int enemyCount = countTeam(ENEMY);
        int limit = 24 + difficulty * 10;
        if (enemyCount < limit) {
            int r = rng.nextInt(100);
            UnitType type = r < 44 ? UnitType.SWORD : r < 76 ? UnitType.ARCHER : UnitType.CAVALRY;
            spawn(ENEMY, type, 18f + rng.nextFloat() * 8f, 12f + rng.nextFloat() * 8f);
        }

        float aggression = difficulty == 0 ? 0.34f : difficulty == 1 ? 0.66f : 0.92f;
        List<Unit> humans = new ArrayList<>();
        for (Unit u : units) if (u.team == PLAYER) humans.add(u);
        if (humans.isEmpty()) return;

        for (Unit u : units) {
            if (u.team != ENEMY || rng.nextFloat() > aggression) continue;
            Unit nearest = null;
            float best = Float.MAX_VALUE;
            for (Unit h : humans) {
                float d2 = Vector2.dst2(u.pos.x, u.pos.z, h.pos.x, h.pos.z);
                if (d2 < best) {
                    best = d2;
                    nearest = h;
                }
            }
            if (nearest != null) u.targetId = nearest.id;
        }
    }

    private void checkEnd() {
        int p = countTeam(PLAYER);
        int e = countTeam(ENEMY);
        if (p == 0) {
            gameOver = true;
            endText = "DERROTA";
        } else if (e == 0) {
            gameOver = true;
            endText = "VITORIA";
        }
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
        float yaw = cameraYaw * 0.017453292f;
        float pitch = cameraPitch * 0.017453292f;
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

    private void drawSelectionRings() {
        if (selected.isEmpty()) return;
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.98f, 0.83f, 0.24f, 1f);
        for (Integer id : selected) {
            Unit u = findById(id);
            if (u == null) continue;
            float r = u.type == UnitType.CAVALRY ? 0.95f : 0.66f;
            for (int i = 0; i < 24; i++) {
                float a0 = i * 6.2831853f / 24f;
                float a1 = (i + 1) * 6.2831853f / 24f;
                shapes.line(
                    u.pos.x + (float)Math.cos(a0) * r, 0.08f, u.pos.z + (float)Math.sin(a0) * r,
                    u.pos.x + (float)Math.cos(a1) * r, 0.08f, u.pos.z + (float)Math.sin(a1) * r
                );
            }
        }
        shapes.end();
    }

    private void drawUi() {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        uiMatrix.setToOrtho2D(0f, 0f, w, h);
        float scale = Math.max(0.92f, Math.min(1.45f, h / 720f));
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
        shapes.setColor(0.02f, 0.025f, 0.02f, 0.64f);
        shapes.rect(0, 0, w, h);
        float panelW = Math.min(w * 0.72f, 840f);
        float panelH = Math.min(h * 0.70f, 560f);
        float px = (w - panelW) * 0.5f;
        float py = (h - panelH) * 0.5f;
        shapes.setColor(0.055f, 0.067f, 0.055f, 0.94f);
        shapes.rect(px, py, panelW, panelH);

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

        playButton.set(px + panelW * 0.28f, py + panelH * 0.14f, panelW * 0.44f, bh * 1.05f);
        drawButtonFill(playButton, true);
        shapes.end();

        sprites.setProjectionMatrix(uiMatrix);
        sprites.begin();
        font.setColor(0.95f, 0.84f, 0.58f, 1f);
        float old = font.getData().scaleX;
        font.getData().setScale(old * 1.95f);
        font.draw(sprites, "IMPERIUM VALE 3D", px, py + panelH * 0.82f, panelW, Align.center, false);
        font.getData().setScale(old);
        font.setColor(0.82f, 0.82f, 0.75f, 1f);
        font.draw(sprites, "RTS medieval nativo para Android", px, py + panelH * 0.69f, panelW, Align.center, false);
        font.draw(sprites, "FACIL", easyButton.x, easyButton.y + easyButton.height * 0.62f, easyButton.width, Align.center, false);
        font.draw(sprites, "NORMAL", normalButton.x, normalButton.y + normalButton.height * 0.62f, normalButton.width, Align.center, false);
        font.draw(sprites, "DIFICIL", hardButton.x, hardButton.y + hardButton.height * 0.62f, hardButton.width, Align.center, false);
        font.setColor(0.08f, 0.08f, 0.05f, 1f);
        font.draw(sprites, "JOGAR", playButton.x, playButton.y + playButton.height * 0.62f, playButton.width, Align.center, false);
        sprites.end();
    }

    private void drawButtonFill(Rectangle r, boolean active) {
        if (active) shapes.setColor(0.76f, 0.59f, 0.25f, 0.96f);
        else shapes.setColor(0.14f, 0.18f, 0.14f, 0.96f);
        shapes.rect(r.x, r.y, r.width, r.height);
    }

    private void drawGameHud(int w, int h) {
        float top = Math.max(58f, h * 0.092f);
        float bottom = Math.max(72f, h * 0.13f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.025f, 0.032f, 0.025f, 0.90f);
        shapes.rect(0, h - top, w, top);
        shapes.rect(0, 0, w, bottom);

        float bw = Math.min(178f, w * 0.17f);
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

        float mapW = Math.min(205f, w * 0.19f);
        float mapH = bottom * 0.78f;
        shapes.setColor(0.08f, 0.11f, 0.075f, 0.98f);
        shapes.rect(12f, (bottom - mapH) * 0.5f, mapW, mapH);
        for (Unit u : units) {
            float mx = 12f + ((u.pos.x + WORLD) / (WORLD * 2f)) * mapW;
            float my = (bottom - mapH) * 0.5f + ((u.pos.z + WORLD) / (WORLD * 2f)) * mapH;
            if (u.team == PLAYER) shapes.setColor(0.18f, 0.48f, 0.96f, 1f);
            else shapes.setColor(0.92f, 0.20f, 0.12f, 1f);
            shapes.circle(mx, my, 2.6f, 8);
        }
        shapes.end();

        sprites.setProjectionMatrix(uiMatrix);
        sprites.begin();
        font.setColor(0.93f, 0.89f, 0.76f, 1f);
        font.draw(sprites, "COMIDA " + (int)food + "    MADEIRA " + (int)wood + "    OURO " + (int)gold
            + "    POP " + countTeam(PLAYER) + "/60", 18f, h - top * 0.40f);
        font.setColor(0.72f, 0.79f, 0.70f, 1f);
        font.draw(sprites, Math.round(Math.min(99f, fpsSmooth)) + " FPS", w - 135f, h - top * 0.40f);

        drawHudLabel("ALDEAO\n50 C", villagerButton);
        drawHudLabel("ESPADA\n60 C 20 O", swordButton);
        drawHudLabel("ARQUEIRO\n45 M 30 O", archerButton);
        drawHudLabel("CAVALO\n90 C 60 O", cavalryButton);

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

    private void drawHudLabel(String label, Rectangle r) {
        font.setColor(0.88f, 0.84f, 0.72f, 1f);
        String[] lines = label.split("\\n");
        font.draw(sprites, lines[0], r.x, r.y + r.height * 0.68f, r.width, Align.center, false);
        font.getData().setScale(font.getData().scaleX * 0.82f);
        font.draw(sprites, lines[1], r.x, r.y + r.height * 0.31f, r.width, Align.center, false);
        font.getData().setScale(font.getData().scaleX / 0.82f);
    }

    private boolean inside(Rectangle r, float x, float y) {
        return r.contains(x, y);
    }

    private void train(UnitType type, float f, float w, float g) {
        if (countTeam(PLAYER) >= 60) return;
        if (food < f || wood < w || gold < g) return;
        food -= f;
        wood -= w;
        gold -= g;
        spawn(PLAYER, type, -20f + rng.nextFloat() * 5f, -10f + rng.nextFloat() * 4f);
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
                        if (u.team == PLAYER && u.type == hit.type && Vector2.dst2(u.pos.x, u.pos.z, hit.pos.x, hit.pos.z) < 225f) {
                            selected.add(u.id);
                        }
                    }
                } else selected.add(hit.id);
                lastTapUnit = hit.id;
                lastTapMs = now;
            } else if (!selected.isEmpty()) {
                for (Integer id : selected) {
                    Unit u = findById(id);
                    if (u != null) {
                        u.targetId = hit.id;
                        u.moving = false;
                    }
                }
            }
            return;
        }

        if (selected.isEmpty()) return;
        Ray ray = camera.getPickRay(x, y);
        if (!Intersector.intersectRayPlane(ray, groundPlane, tmp3)) return;
        List<Unit> group = new ArrayList<>();
        for (Integer id : selected) {
            Unit u = findById(id);
            if (u != null) group.add(u);
        }
        group.sort(Comparator.comparingInt(a -> a.id));
        int cols = (int)Math.ceil(Math.sqrt(group.size()));
        float spacing = 1.25f;
        for (int i = 0; i < group.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            Unit u = group.get(i);
            u.target.set(
                tmp3.x + (col - (cols - 1) * 0.5f) * spacing,
                0f,
                tmp3.z + (row - (cols - 1) * 0.5f) * spacing
            );
            u.targetId = -1;
            u.moving = true;
        }
    }

    private Unit findUnitAtScreen(float x, float inputY) {
        float best = 62f;
        Unit result = null;
        int h = Gdx.graphics.getHeight();
        for (Unit u : units) {
            tmpScreen.set(u.pos.x, u.type == UnitType.CAVALRY ? 1.7f : 1.2f, u.pos.z);
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
                    cameraDistance = clamp(pinchCameraDistance * (pinchDistance / d), 18f, 57f);
                }
                dragged = true;
                return true;
            }
            if (pointer == 0) {
                float dx = screenX - lastX;
                float dy = screenY - lastY;
                if (Vector2.dst(downX, downY, screenX, screenY) > 14f) dragged = true;
                if (dragged) {
                    float factor = cameraDistance * 0.0022f;
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
            if (keycode == Input.Keys.BACK) {
                if (mode == Mode.GAME) {
                    mode = Mode.MENU;
                    units.clear();
                    selected.clear();
                    resetMenuCamera();
                    return true;
                }
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
        for (Model m : ownedModels) m.dispose();
    }
}
