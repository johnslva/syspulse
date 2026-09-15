from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "core/src/main/java/com/imperiumvale/core"
p = CORE / "ImperiumGameV6.java"
s = p.read_text(encoding="utf-8")


def rep(old, new, n=1):
    global s
    if s.count(old) < n:
        raise RuntimeError("v14 missing target: " + old[:120])
    s = s.replace(old, new, n)


rep('private static final String VERSION = "v0.13.0-alpha";', 'private static final String VERSION = "v0.14.0-alpha";')

# The old nearly-coplanar circular ground patches caused z-fighting once real shadows arrived.
# Keep terrain variation in textures/vegetation instead.
rep('''        createGroundDetail(mb);
        createRoad(mb);''', '''        // Ground variation is now texture-driven to avoid z-fighting/self-shadow artifacts.
        createRoad(mb);''')

# Higher-resolution runtime materials remain tiny on disk but read much better at the closer camera.
rep('''        grassTexture = makeTexture(96, 0.26f, 0.46f, 0.18f, 0.16f, 0);
        dirtTexture = makeTexture(96, 0.47f, 0.34f, 0.19f, 0.18f, 1);
        stoneTexture = makeTexture(96, 0.56f, 0.56f, 0.53f, 0.12f, 2);
        timberTexture = makeTexture(96, 0.48f, 0.31f, 0.15f, 0.14f, 3);
        roofTexture = makeTexture(96, 0.38f, 0.12f, 0.07f, 0.12f, 4);''', '''        grassTexture = makeTexture(192, 0.25f, 0.43f, 0.17f, 0.15f, 0);
        dirtTexture = makeTexture(192, 0.45f, 0.32f, 0.18f, 0.16f, 1);
        stoneTexture = makeTexture(192, 0.55f, 0.55f, 0.52f, 0.11f, 2);
        timberTexture = makeTexture(192, 0.46f, 0.29f, 0.14f, 0.13f, 3);
        roofTexture = makeTexture(192, 0.36f, 0.105f, 0.06f, 0.105f, 4);''')

# Add broad low-frequency detail to procedural materials so they stop reading as flat color noise.
rep('''                float n = ((h & 2047) / 2047f - 0.5f) * variation;
                float r = clamp(br + n, 0f, 1f);
                float g = clamp(bg + n, 0f, 1f);
                float b = clamp(bb + n, 0f, 1f);''', '''                float n = ((h & 2047) / 2047f - 0.5f) * variation;
                float broad = (float)(Math.sin(x * 0.055 + style) * Math.cos(y * 0.047 - style)) * variation * 0.20f;
                float r = clamp(br + n + broad, 0f, 1f);
                float g = clamp(bg + n + broad * 0.92f, 0f, 1f);
                float b = clamp(bb + n + broad * 0.70f, 0f, 1f);''')

# Denser shadow texels over a tighter gameplay volume: sharper without changing low-quality fallback cost.
rep('''shadowLight = new DirectionalShadowLight(768, 768, 68f, 68f, 1f, 105f);''',
    '''shadowLight = new DirectionalShadowLight(1024, 1024, 60f, 60f, 1f, 96f);''')

p.write_text(s, encoding="utf-8")

# V7 art layer: higher-resolution terrain skin plus animated translucent water highlights.
v7p = CORE / "ImperiumGameV7.java"
v = v7p.read_text(encoding="utf-8")
v = v.replace('import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;',
              'import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;\nimport com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;')
v = v.replace('''    private final Array<ModelInstance> scenery = new Array<>();''', '''    private final Array<ModelInstance> scenery = new Array<>();
    private final Array<ModelInstance> waterShimmers = new Array<>();''')
v = v.replace('''    private float hdGrace;''', '''    private float hdGrace;
    private float waterTime;''')
v = v.replace('''        int size = 192;''', '''        int size = 256;''')

# Slight ambient lift prevents hard shadow areas from crushing to black on phone displays.
v = v.replace('environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.24f, 0.27f, 0.24f, 1f));',
              'environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.275f, 0.295f, 0.27f, 1f));')

# Add alpha material helper.
anchor = '''    private Material mat(float r, float g, float b) {'''
helper = '''    private Material alphaMat(float r, float g, float b, float a) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, a)),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, a));
    }

'''
if anchor not in v:
    raise RuntimeError('v14 missing material anchor')
v = v.replace(anchor, helper + anchor, 1)

v = v.replace('''        buildMixedTrees(mb);
        buildMicroVegetation(mb);''', '''        buildMixedTrees(mb);
        buildMicroVegetation(mb);
        buildWaterShimmers(mb);''')

anchor2 = '''    private void buildMicroVegetation(ModelBuilder mb) {'''
water_method = '''    private void buildWaterShimmers(ModelBuilder mb) {
        Model shimmerA = own(mb.createBox(5.7f, 0.018f, 1.15f, alphaMat(0.18f, 0.58f, 0.72f, 0.24f), ATTR));
        Model shimmerB = own(mb.createBox(4.8f, 0.016f, 0.72f, alphaMat(0.45f, 0.78f, 0.86f, 0.16f), ATTR));
        for (int i = -5; i <= 5; i++) {
            float z = i * 7.8f;
            float x = 4.2f + (float)Math.sin(i * 0.74f) * 1.55f;
            ModelInstance a = new ModelInstance(shimmerA);
            a.userData = Integer.valueOf(i * 2 + 40);
            a.transform.setToTranslation(x, 0.065f, z - 1.45f).rotate(Vector3.Y, -4f);
            waterShimmers.add(a);
            ModelInstance b = new ModelInstance(shimmerB);
            b.userData = Integer.valueOf(i * 2 + 41);
            b.transform.setToTranslation(x + 0.35f, 0.075f, z + 1.55f).rotate(Vector3.Y, -4f);
            waterShimmers.add(b);
        }
    }

'''
if anchor2 not in v:
    raise RuntimeError('v14 missing vegetation anchor')
v = v.replace(anchor2, water_method + anchor2, 1)

# Animate the highlight height/scale and render after cached opaque scenery.
v = v.replace('''        float dt = Math.min(0.05f, Math.max(0f, Gdx.graphics.getDeltaTime()));
        updateHdGrace(dt);
        game.render();''', '''        float dt = Math.min(0.05f, Math.max(0f, Gdx.graphics.getDeltaTime()));
        waterTime += dt;
        updateHdGrace(dt);
        game.render();
        for (int idx = 0; idx < waterShimmers.size; idx++) {
            ModelInstance wi = waterShimmers.get(idx);
            int seed = wi.userData instanceof Integer ? (Integer)wi.userData : idx;
            float z = ((seed - 40) / 2 - 5) * 7.8f + ((seed & 1) == 0 ? -1.45f : 1.55f);
            float x = 4.2f + (float)Math.sin(((seed - 40) / 2 - 5) * 0.74f) * 1.55f + ((seed & 1) == 0 ? 0f : 0.35f);
            float pulse = 0.94f + (float)Math.sin(waterTime * 1.65f + seed * 0.43f) * 0.10f;
            wi.transform.setToTranslation(x, 0.068f + (float)Math.sin(waterTime * 1.9f + seed) * 0.012f, z)
                .rotate(Vector3.Y, -4f).scale(pulse, 1f, 1f);
        }''')
v = v.replace('''        batch.begin(camera);
        batch.render(cache, environment);
        batch.end();''', '''        batch.begin(camera);
        batch.render(cache, environment);
        for (ModelInstance wi : waterShimmers) batch.render(wi, environment);
        batch.end();''')

v7p.write_text(v, encoding="utf-8")
print("Applied v0.14 terrain/shadow cleanup, richer materials and animated water")
