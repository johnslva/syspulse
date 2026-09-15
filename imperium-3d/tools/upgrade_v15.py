from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "core/src/main/java/com/imperiumvale/core"
p = CORE / "ImperiumGameV6.java"
s = p.read_text(encoding="utf-8")


def rep(old, new, n=1):
    global s
    if s.count(old) < n:
        raise RuntimeError("v15 missing target: " + old[:120])
    s = s.replace(old, new, n)


rep('private static final String VERSION = "v0.14.0-alpha";', 'private static final String VERSION = "v0.15.0-alpha";')
rep('import java.util.Iterator;', 'import java.util.Iterator;\nimport java.util.Arrays;\nimport java.util.PriorityQueue;')

rep('''    private static final int CHUNK_COUNT = 6;''', '''    private static final int CHUNK_COUNT = 6;
    private static final int NAV_N = 41;
    private static final float NAV_CELL = 2f;''')

# Per-unit cached path. Replans are throttled, so dozens of troops do not run A* every frame.
rep('''        ModelInstance rightLeg;

        Unit(int id, int team, UnitType type, float x, float z) {''', '''        ModelInstance rightLeg;
        final Array<Vector2> navPath = new Array<>();
        int navIndex;
        float navGoalX = Float.NaN;
        float navGoalZ = Float.NaN;
        float navCooldown;

        Unit(int id, int team, UnitType type, float x, float z) {''')

# Compact priority-queue record and reusable A* scratch buffers.
insert = '''    private static final class Stronghold {'''
nav_class = '''    private static final class NavRecord implements Comparable<NavRecord> {
        final int idx;
        final float f;
        NavRecord(int idx, float f) { this.idx = idx; this.f = f; }
        @Override public int compareTo(NavRecord other) { return Float.compare(f, other.f); }
    }

'''
rep(insert, nav_class + insert)

rep('''    private final Set<Integer> selected = new HashSet<>();''', '''    private final Set<Integer> selected = new HashSet<>();
    private final float[] navG = new float[NAV_N * NAV_N];
    private final int[] navParent = new int[NAV_N * NAV_N];
    private final boolean[] navClosed = new boolean[NAV_N * NAV_N];''')

# Navigation helper suite replaces the old hard-coded river waypoint.
old_move = '''    private void moveToward(Unit u, float x, float z, float dt) {
        float navX = x, navZ = z;
        boolean crossingEast = u.pos.x < -0.6f && x > 9.0f;
        boolean crossingWest = u.pos.x > 9.0f && x < -0.6f;
        if ((crossingEast || crossingWest) && Math.abs(u.pos.z - 0.3f) > 3.0f) {
            navX = crossingEast ? 0.2f : 8.2f;
            navZ = 0.3f;
        }
        float dx = navX - u.pos.x, dz = navZ - u.pos.z;
        float d = (float)Math.sqrt(dx * dx + dz * dz);
        if (d < 0.12f) { u.moving = false; return; }
        faceToward(u, navX, navZ);
        u.moving = true;
        float step = Math.min(d, u.speed * dt);
        u.pos.x += dx / d * step; u.pos.z += dz / d * step;
        u.pos.x = clamp(u.pos.x, -WORLD, WORLD); u.pos.z = clamp(u.pos.z, -WORLD, WORLD);
    }'''
new_move = '''    private int navCoord(float v) {
        return Math.max(0, Math.min(NAV_N - 1, Math.round((v + WORLD) / NAV_CELL)));
    }

    private float navWorld(int c) {
        return -WORLD + c * NAV_CELL;
    }

    private float riverCenterX(float z) {
        float segment = z / 7.8f;
        return 4.2f + (float)Math.sin(segment * 0.74f) * 1.55f;
    }

    private boolean navBlocked(float x, float z, float goalX, float goalZ) {
        if (x <= -WORLD + 0.5f || x >= WORLD - 0.5f || z <= -WORLD + 0.5f || z >= WORLD - 0.5f) return true;

        // River is impassable except at the timber bridge around z=0.
        float riverX = riverCenterX(z);
        if (Math.abs(x - riverX) < 3.45f && Math.abs(z - 0.3f) > 3.15f) return true;

        for (Building b : buildings) {
            if (b.hp <= 0f) continue;
            float radius = switch (b.type) {
                case HOUSE -> 2.35f;
                case BARRACKS -> 3.15f;
                case TOWER -> 2.10f;
                case WALL -> 2.45f;
            };
            // The exact requested destination may intentionally be next to a structure.
            if (Vector2.dst2(x, z, goalX, goalZ) < NAV_CELL * NAV_CELL * 0.60f) continue;
            if (Vector2.dst2(x, z, b.pos.x, b.pos.z) < radius * radius) return true;
        }
        return false;
    }

    private boolean segmentBlocked(float ax, float az, float bx, float bz) {
        float dist = Vector2.dst(ax, az, bx, bz);
        int steps = Math.max(1, (int)Math.ceil(dist / 1.1f));
        for (int i = 1; i < steps; i++) {
            float t = i / (float)steps;
            float x = ax + (bx - ax) * t;
            float z = az + (bz - az) * t;
            if (navBlocked(x, z, bx, bz)) return true;
        }
        return false;
    }

    private float navHeuristic(int x, int z, int gx, int gz) {
        int dx = Math.abs(x - gx), dz = Math.abs(z - gz);
        int diagonal = Math.min(dx, dz);
        int straight = dx + dz - diagonal * 2;
        return diagonal * 1.41421356f + straight;
    }

    private void buildPath(Unit u, float goalX, float goalZ) {
        u.navPath.clear();
        u.navIndex = 0;
        int sx = navCoord(u.pos.x), sz = navCoord(u.pos.z);
        int gx = navCoord(goalX), gz = navCoord(goalZ);
        int start = sz * NAV_N + sx, goal = gz * NAV_N + gx;
        if (start == goal) return;

        Arrays.fill(navG, Float.POSITIVE_INFINITY);
        Arrays.fill(navParent, -1);
        Arrays.fill(navClosed, false);
        PriorityQueue<NavRecord> open = new PriorityQueue<>();
        navG[start] = 0f;
        open.add(new NavRecord(start, navHeuristic(sx, sz, gx, gz)));
        final int[] dirs = {-1,-1, 0,-1, 1,-1, -1,0, 1,0, -1,1, 0,1, 1,1};
        int expansions = 0;

        while (!open.isEmpty() && expansions < 1500) {
            NavRecord rec = open.poll();
            int idx = rec.idx;
            if (navClosed[idx]) continue;
            navClosed[idx] = true;
            expansions++;
            if (idx == goal) break;
            int cx = idx % NAV_N, cz = idx / NAV_N;
            for (int d = 0; d < dirs.length; d += 2) {
                int nx = cx + dirs[d], nz = cz + dirs[d + 1];
                if (nx < 0 || nx >= NAV_N || nz < 0 || nz >= NAV_N) continue;
                int ni = nz * NAV_N + nx;
                if (navClosed[ni]) continue;
                float wx = navWorld(nx), wz = navWorld(nz);
                if (ni != goal && navBlocked(wx, wz, goalX, goalZ)) continue;
                // Prevent diagonal corner cutting between two blocked cells.
                if (dirs[d] != 0 && dirs[d + 1] != 0) {
                    float wxA = navWorld(cx + dirs[d]), wzA = navWorld(cz);
                    float wxB = navWorld(cx), wzB = navWorld(cz + dirs[d + 1]);
                    if (navBlocked(wxA, wzA, goalX, goalZ) && navBlocked(wxB, wzB, goalX, goalZ)) continue;
                }
                float cost = (dirs[d] == 0 || dirs[d + 1] == 0) ? 1f : 1.41421356f;
                float ng = navG[idx] + cost;
                if (ng >= navG[ni]) continue;
                navG[ni] = ng;
                navParent[ni] = idx;
                open.add(new NavRecord(ni, ng + navHeuristic(nx, nz, gx, gz)));
            }
        }

        if (navParent[goal] < 0) return;
        Array<Vector2> reversed = new Array<>();
        int at = goal;
        while (at != start && at >= 0 && reversed.size < NAV_N * NAV_N) {
            int cx = at % NAV_N, cz = at / NAV_N;
            reversed.add(new Vector2(navWorld(cx), navWorld(cz)));
            at = navParent[at];
        }
        for (int i = reversed.size - 1; i >= 0; i--) u.navPath.add(reversed.get(i));
        // Preserve the precise formation/attack destination after the grid path.
        u.navPath.add(new Vector2(goalX, goalZ));
    }

    private void ensurePath(Unit u, float x, float z, float dt) {
        u.navCooldown -= dt;
        boolean goalChanged = Float.isNaN(u.navGoalX) || Vector2.dst2(u.navGoalX, u.navGoalZ, x, z) > 2.25f;
        boolean pathEnded = u.navIndex >= u.navPath.size;
        if ((goalChanged || pathEnded) && u.navCooldown <= 0f) {
            u.navGoalX = x; u.navGoalZ = z;
            u.navCooldown = 0.65f + (u.id % 5) * 0.055f;
            if (segmentBlocked(u.pos.x, u.pos.z, x, z)) buildPath(u, x, z);
            else { u.navPath.clear(); u.navIndex = 0; }
        }
    }

    private void moveToward(Unit u, float x, float z, float dt) {
        ensurePath(u, x, z, dt);
        float navX = x, navZ = z;
        while (u.navIndex < u.navPath.size) {
            Vector2 point = u.navPath.get(u.navIndex);
            if (Vector2.dst2(u.pos.x, u.pos.z, point.x, point.y) < 0.60f) { u.navIndex++; continue; }
            navX = point.x; navZ = point.y;
            break;
        }
        if (u.navIndex >= u.navPath.size && !u.navPath.isEmpty()) {
            navX = x; navZ = z;
        }

        float dx = navX - u.pos.x, dz = navZ - u.pos.z;
        float d = (float)Math.sqrt(dx * dx + dz * dz);
        if (d < 0.12f) { u.moving = false; return; }
        faceToward(u, navX, navZ);
        u.moving = true;
        float step = Math.min(d, u.speed * dt);
        float nextX = u.pos.x + dx / d * step;
        float nextZ = u.pos.z + dz / d * step;
        if (navBlocked(nextX, nextZ, x, z) && Vector2.dst2(nextX, nextZ, x, z) > 1.2f) {
            u.navCooldown = 0f;
            buildPath(u, x, z);
            return;
        }
        u.pos.x = clamp(nextX, -WORLD, WORLD);
        u.pos.z = clamp(nextZ, -WORLD, WORLD);
    }'''
rep(old_move, new_move)

# Clear stale navigation when a direct command changes intent.
rep('''    private void clearTargets(Unit u) {
        u.targetUnitId = -1; u.targetStronghold = -1;
    }''', '''    private void clearTargets(Unit u) {
        u.targetUnitId = -1; u.targetStronghold = -1;
        u.navPath.clear(); u.navIndex = 0; u.navGoalX = Float.NaN; u.navGoalZ = Float.NaN; u.navCooldown = 0f;
    }''')

p.write_text(s, encoding="utf-8")
print("Applied v0.15 A-star navigation grid and dynamic obstacle routing")
