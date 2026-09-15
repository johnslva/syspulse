from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "core/src/main/java/com/imperiumvale/core"

src = (CORE / "ImperiumGameV4.java").read_text(encoding="utf-8")


def rep(old, new, n=1):
    global src
    if src.count(old) < n:
        raise RuntimeError("missing target: " + old[:100])
    src = src.replace(old, new, n)


rep("public final class ImperiumGameV4 extends ApplicationAdapter {", "public final class ImperiumGameV6 extends ApplicationAdapter {")
rep('private static final String VERSION = "v0.7.0-alpha";', 'private static final String VERSION = "v0.9.0-alpha";')
rep("private enum ResourceType { FOOD, WOOD, GOLD }", "private enum ResourceType { FOOD, WOOD, GOLD, STONE }")

rep("    private Model goldNodeModel;\n    private Model goldDetailModel;",
    "    private Model goldNodeModel;\n    private Model goldDetailModel;\n    private Model stoneNodeModel;\n    private Model stoneDetailModel;")
rep("    private float gold = 430f;\n    private float aiAccumulator;",
'''    private float gold = 430f;
    private float stone = 360f;
    private float aiFood = 620f;
    private float aiWood = 560f;
    private float aiGold = 390f;
    private float aiStone = 320f;
    private float aiAccumulator;''')

rep("        goldNodeModel = own(mb.createSphere(2.25f, 1.45f, 1.95f, 10, 7, material(0.45f, 0.43f, 0.36f), ATTR));\n        goldDetailModel = own(mb.createSphere(0.58f, 0.42f, 0.48f, 8, 5, material(0.91f, 0.66f, 0.10f), ATTR));",
'''        goldNodeModel = own(mb.createSphere(2.25f, 1.45f, 1.95f, 10, 7, material(0.45f, 0.43f, 0.36f), ATTR));
        goldDetailModel = own(mb.createSphere(0.58f, 0.42f, 0.48f, 8, 5, material(0.91f, 0.66f, 0.10f), ATTR));
        stoneNodeModel = own(mb.createSphere(2.55f, 1.52f, 2.15f, 10, 7, material(0.40f, 0.42f, 0.43f), ATTR));
        stoneDetailModel = own(mb.createSphere(0.78f, 0.58f, 0.70f, 8, 5, material(0.66f, 0.67f, 0.67f), ATTR));''')

rep("        food = 660f; wood = 650f; gold = 430f; aiAccumulator = 0f;",
    "        food = 660f; wood = 650f; gold = 430f; stone = 360f;\n        aiFood = 620f; aiWood = 560f; aiGold = 390f; aiStone = 320f; aiAccumulator = 0f;")

rep('''    private void createResourceNodes() {
        addResource(ResourceType.FOOD, -31f, -7f, 760f);
        addResource(ResourceType.WOOD, -14f, -22f, 950f);
        addResource(ResourceType.GOLD, -12f, -8f, 700f);
        addResource(ResourceType.FOOD, 31f, 7f, 760f);
        addResource(ResourceType.WOOD, 14f, 22f, 950f);
        addResource(ResourceType.GOLD, 12f, 8f, 700f);
        addResource(ResourceType.FOOD, -3f, 16f, 900f);
        addResource(ResourceType.GOLD, 16f, -14f, 850f);
    }''', '''    private void createResourceNodes() {
        addResource(ResourceType.FOOD, -31f, -7f, 780f);
        addResource(ResourceType.WOOD, -14f, -22f, 980f);
        addResource(ResourceType.GOLD, -12f, -8f, 720f);
        addResource(ResourceType.STONE, -33f, -25f, 900f);
        addResource(ResourceType.FOOD, 31f, 7f, 780f);
        addResource(ResourceType.WOOD, 14f, 22f, 980f);
        addResource(ResourceType.GOLD, 12f, 8f, 720f);
        addResource(ResourceType.STONE, 33f, 25f, 900f);
        addResource(ResourceType.FOOD, -3f, 16f, 900f);
        addResource(ResourceType.GOLD, 16f, -14f, 850f);
        addResource(ResourceType.STONE, -5f, -28f, 820f);
        addResource(ResourceType.STONE, 6f, 27f, 820f);
    }''')

rep('''        if (type == ResourceType.FOOD) {
            n.primary = new ModelInstance(foodNodeModel); n.detail = new ModelInstance(foodDetailModel);
        } else if (type == ResourceType.WOOD) {
            n.primary = new ModelInstance(woodNodeModel); n.detail = new ModelInstance(woodDetailModel);
        } else {
            n.primary = new ModelInstance(goldNodeModel); n.detail = new ModelInstance(goldDetailModel);
        }''', '''        if (type == ResourceType.FOOD) {
            n.primary = new ModelInstance(foodNodeModel); n.detail = new ModelInstance(foodDetailModel);
        } else if (type == ResourceType.WOOD) {
            n.primary = new ModelInstance(woodNodeModel); n.detail = new ModelInstance(woodDetailModel);
        } else if (type == ResourceType.GOLD) {
            n.primary = new ModelInstance(goldNodeModel); n.detail = new ModelInstance(goldDetailModel);
        } else {
            n.primary = new ModelInstance(stoneNodeModel); n.detail = new ModelInstance(stoneDetailModel);
        }''')

rep('''        } else {
            n.primary.transform.setToTranslation(n.pos.x, 0.65f, n.pos.z).scale(s, s, s);
            n.detail.transform.setToTranslation(n.pos.x + 0.55f, 1.05f, n.pos.z - 0.30f).scale(s, s, s);
        }
    }

    private Building createBuilding''', '''        } else if (n.type == ResourceType.GOLD) {
            n.primary.transform.setToTranslation(n.pos.x, 0.65f, n.pos.z).scale(s, s, s);
            n.detail.transform.setToTranslation(n.pos.x + 0.55f, 1.05f, n.pos.z - 0.30f).scale(s, s, s);
        } else {
            n.primary.transform.setToTranslation(n.pos.x, 0.72f, n.pos.z).scale(s, s, s);
            n.detail.transform.setToTranslation(n.pos.x - 0.62f, 1.08f, n.pos.z + 0.35f).scale(s, s, s);
        }
    }

    private Building createBuilding''')

rep("            if (u.type == UnitType.VILLAGER && u.team == PLAYER && u.gatherNode != null) {",
    "            if (u.type == UnitType.VILLAGER && u.gatherNode != null) {")

rep('''                if (n != null) {
                    if (n.type == ResourceType.FOOD) food += u.carried;
                    else if (n.type == ResourceType.WOOD) wood += u.carried;
                    else gold += u.carried;
                }''', '''                if (n != null) {
                    if (u.team == PLAYER) {
                        if (n.type == ResourceType.FOOD) food += u.carried;
                        else if (n.type == ResourceType.WOOD) wood += u.carried;
                        else if (n.type == ResourceType.GOLD) gold += u.carried;
                        else stone += u.carried;
                    } else {
                        if (n.type == ResourceType.FOOD) aiFood += u.carried;
                        else if (n.type == ResourceType.WOOD) aiWood += u.carried;
                        else if (n.type == ResourceType.GOLD) aiGold += u.carried;
                        else aiStone += u.carried;
                    }
                }''')
rep("            float d = Vector2.dst(u.pos.x, u.pos.z, playerStronghold.pos.x, playerStronghold.pos.z);\n            if (d > 5.1f) moveToward(u, playerStronghold.pos.x, playerStronghold.pos.z, dt);",
'''            Stronghold home = stronghold(u.team);
            float d = Vector2.dst(u.pos.x, u.pos.z, home.pos.x, home.pos.z);
            if (d > 5.1f) moveToward(u, home.pos.x, home.pos.z, dt);''')
rep("        if (u.type != UnitType.VILLAGER || u.team != PLAYER) return;",
    "        if (u.type != UnitType.VILLAGER) return;")

# Give the AI villagers and assign both factions across all four resource types.
rep('''        if (resources.size() >= 3) {
            for (int i = 0; i < villagers.size(); i++) assignGather(villagers.get(i), resources.get(i < 3 ? 0 : i < 5 ? 1 : 2));
        }

        int extra = difficulty * 2;''', '''        if (resources.size() >= 4) {
            for (int i = 0; i < villagers.size(); i++) {
                int ri = i < 2 ? 0 : i < 4 ? 1 : i < 6 ? 2 : 3;
                assignGather(villagers.get(i), resources.get(ri));
            }
        }

        List<Unit> enemyVillagers = new ArrayList<>();
        for (int i = 0; i < 7; i++) enemyVillagers.add(spawn(ENEMY, UnitType.VILLAGER, 27f - i * 1.05f, 11.8f - (i & 1) * 1.0f));
        if (resources.size() >= 8) {
            for (int i = 0; i < enemyVillagers.size(); i++) {
                int ri = i < 2 ? 4 : i < 4 ? 5 : i < 6 ? 6 : 7;
                assignGather(enemyVillagers.get(i), resources.get(ri));
            }
        }

        int extra = difficulty * 2;''')

# Stone minimap and top HUD.
rep('''            if (n.type == ResourceType.GOLD) shapes.setColor(0.95f, 0.70f, 0.12f, 1f);
            else if (n.type == ResourceType.WOOD) shapes.setColor(0.30f, 0.62f, 0.20f, 1f);
            else shapes.setColor(0.78f, 0.30f, 0.28f, 1f);''', '''            if (n.type == ResourceType.GOLD) shapes.setColor(0.95f, 0.70f, 0.12f, 1f);
            else if (n.type == ResourceType.WOOD) shapes.setColor(0.30f, 0.62f, 0.20f, 1f);
            else if (n.type == ResourceType.STONE) shapes.setColor(0.72f, 0.74f, 0.76f, 1f);
            else shapes.setColor(0.78f, 0.30f, 0.28f, 1f);''')
rep('''        font.draw(sprites, "COMIDA " + (int)food + "    MADEIRA " + (int)wood + "    OURO " + (int)gold + "    POP " + countTeam(PLAYER) + "/" + populationCap,
            18f, h - top * 0.40f);''', '''        font.draw(sprites, "COMIDA " + (int)food + "   MADEIRA " + (int)wood + "   OURO " + (int)gold + "   PEDRA " + (int)stone + "   POP " + countTeam(PLAYER) + "/" + populationCap,
            18f, h - top * 0.40f);''')
rep('font.draw(sprites, "TORRE 120M 80O", towerButton.x, towerButton.y + bh * 0.60f, bw, Align.center, false);',
    'font.draw(sprites, "TORRE 60M 160P", towerButton.x, towerButton.y + bh * 0.60f, bw, Align.center, false);')
rep('''        } else {
            if (wood < 120f || gold < 80f) return false; wood -= 120f; gold -= 80f;
        }''', '''        } else {
            if (wood < 60f || stone < 160f) return false; wood -= 60f; stone -= 160f;
        }''')

# AI must pay for units; harder AI is smarter/faster, not magical free spawning.
old_ai = '''    private void updateAi(float dt) {
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
    }'''
new_ai = '''    private void updateAi(float dt) {
        aiAccumulator += dt;
        float interval = difficulty == 0 ? 5.0f : difficulty == 1 ? 3.2f : 2.15f;
        if (aiAccumulator < interval) return;
        aiAccumulator = 0f;

        // Reassign idle enemy villagers to whichever strategic resource is lowest.
        for (Unit u : units) {
            if (u.team != ENEMY || u.type != UnitType.VILLAGER || u.gatherNode != null) continue;
            ResourceType wanted = aiFood < 160f ? ResourceType.FOOD
                : aiWood < 120f ? ResourceType.WOOD
                : aiGold < 100f ? ResourceType.GOLD : ResourceType.STONE;
            ResourceNode node = nearestResource(wanted, u.pos.x, u.pos.z);
            if (node != null) assignGather(u, node);
        }

        int enemyCount = countTeam(ENEMY);
        int military = countMilitary(ENEMY);
        int limit = 24 + difficulty * 10;
        if (enemyCount < limit && enemyStronghold != null && enemyStronghold.hp > 0f) {
            UnitType type;
            if (military < 7 && aiFood >= 60f && aiGold >= 20f) type = UnitType.SWORD;
            else {
                int r = rng.nextInt(100);
                type = r < 42 ? UnitType.SWORD : r < 73 ? UnitType.ARCHER : UnitType.CAVALRY;
            }
            if (aiCanTrain(type)) {
                aiSpend(type);
                spawn(ENEMY, type, 20f + rng.nextFloat() * 7f, 12f + rng.nextFloat() * 8f);
            }
        }

        float aggression = difficulty == 0 ? 0.28f : difficulty == 1 ? 0.62f : 0.90f;
        if (military < (difficulty == 0 ? 8 : difficulty == 1 ? 11 : 14)) aggression *= 0.35f;
        for (Unit u : units) {
            if (u.team != ENEMY || u.type == UnitType.VILLAGER || rng.nextFloat() > aggression) continue;
            Unit nearest = nearestEnemyForTeam(u, PLAYER, 18f);
            if (nearest != null) { u.targetUnitId = nearest.id; u.targetStronghold = -1; }
            else { u.targetUnitId = -1; u.targetStronghold = PLAYER; }
        }
    }

    private boolean aiCanTrain(UnitType type) {
        return switch (type) {
            case VILLAGER -> aiFood >= 50f;
            case SWORD -> aiFood >= 60f && aiGold >= 20f;
            case ARCHER -> aiWood >= 45f && aiGold >= 30f;
            case CAVALRY -> aiFood >= 90f && aiGold >= 60f;
        };
    }

    private void aiSpend(UnitType type) {
        switch (type) {
            case VILLAGER -> aiFood -= 50f;
            case SWORD -> { aiFood -= 60f; aiGold -= 20f; }
            case ARCHER -> { aiWood -= 45f; aiGold -= 30f; }
            case CAVALRY -> { aiFood -= 90f; aiGold -= 60f; }
        }
    }

    private int countMilitary(int team) {
        int n = 0;
        for (Unit u : units) if (u.team == team && u.type != UnitType.VILLAGER) n++;
        return n;
    }

    private ResourceNode nearestResource(ResourceType type, float x, float z) {
        ResourceNode bestNode = null;
        float best = Float.MAX_VALUE;
        for (ResourceNode n : resources) {
            if (n.type != type || n.amount <= 0f) continue;
            float d2 = Vector2.dst2(x, z, n.pos.x, n.pos.z);
            if (d2 < best) { best = d2; bestNode = n; }
        }
        return bestNode;
    }'''
rep(old_ai, new_ai)

# Save upgraded core.
(CORE / "ImperiumGameV6.java").write_text(src, encoding="utf-8")

# Reuse the improved V5 art layer on top of the new simulation core.
v5 = (CORE / "ImperiumGameV5.java").read_text(encoding="utf-8")
v7 = v5.replace("public final class ImperiumGameV5", "public final class ImperiumGameV7")
v7 = v7.replace("ImperiumGameV4", "ImperiumGameV6")
v7 = v7.replace("V5 visual art-direction layer", "V7 visual art-direction layer")
v7 = v7.replace("V5 art layer", "V7 art layer")
(CORE / "ImperiumGameV7.java").write_text(v7, encoding="utf-8")

print("Generated ImperiumGameV6 + ImperiumGameV7")
