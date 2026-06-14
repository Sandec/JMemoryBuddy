package one.jpro.jmemorybuddy;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A small, dependency-free HPROF reader that prints a strong-reference path from a GC root to
 * each not-collected object of the current test.
 *
 * <p>The objects are located in the dump via the {@code AssertCollectable} markers JMemoryBuddy
 * already creates on failure: each carries a numeric {@code id} (so only the current test's
 * objects are reported) and wraps a {@code WeakReference} whose {@code referent} is the leaked
 * object. Weak referents are never followed as part of the path.
 *
 * <p>Best-effort: on any parse problem or when the heap exceeds {@code maxObjects} the analysis is
 * skipped silently — the heap dump itself is always still written.
 */
final class HeapPath {

    private static final int TAG_STRING = 0x01;
    private static final int TAG_LOAD_CLASS = 0x02;
    private static final int TAG_HEAP_DUMP = 0x0C;
    private static final int TAG_HEAP_DUMP_SEGMENT = 0x1C;

    private static final int ROOT_JNI_GLOBAL = 0x01;
    private static final int ROOT_JNI_LOCAL = 0x02;
    private static final int ROOT_JAVA_FRAME = 0x03;
    private static final int ROOT_NATIVE_STACK = 0x04;
    private static final int ROOT_STICKY_CLASS = 0x05;
    private static final int ROOT_THREAD_BLOCK = 0x06;
    private static final int ROOT_MONITOR_USED = 0x07;
    private static final int ROOT_THREAD_OBJECT = 0x08;
    private static final int ROOT_UNKNOWN = 0xFF;
    private static final int CLASS_DUMP = 0x20;
    private static final int INSTANCE_DUMP = 0x21;
    private static final int OBJECT_ARRAY_DUMP = 0x22;
    private static final int PRIMITIVE_ARRAY_DUMP = 0x23;

    private static final String ASSERT_COLLECTABLE = JMemoryBuddy.AssertCollectable.class.getName();
    /** Show at most this many hops; longer paths are abbreviated in the middle. */
    private static final int MAX_HOPS = 50;

    private final String path;
    private final int maxObjects;
    private final long wantedId;
    private int idSize;

    private final Map<Long, String> strings = new HashMap<>();
    private final Map<Long, Long> classNameStringId = new HashMap<>();
    private final Map<Long, ClassInfo> classes = new HashMap<>();

    private final Map<Long, long[]> edges = new HashMap<>();   // objId -> [fieldNameId, targetId, ...] (-1 = array element)
    private final Map<Long, Long> objClass = new HashMap<>();   // objId -> classObjId
    private final Map<Long, String> roots = new HashMap<>();    // rootId -> kind
    private final Map<Long, Long> referentOf = new HashMap<>(); // Reference objId -> referent objId
    private final Set<Long> wantedWeakRefs = new HashSet<>();   // WeakReferences held by a matching AssertCollectable

    private HeapPath(String path, int maxObjects, long wantedId) {
        this.path = path;
        this.maxObjects = maxObjects;
        this.wantedId = wantedId;
    }

    /** Reads the dump and prints a path to a GC root for each not-collected object of test {@code id}. */
    static void printPaths(String hprofPath, int maxObjects, long id) {
        try {
            new HeapPath(hprofPath, maxObjects, id).run();
        } catch (Throwable t) {
            System.out.println("JMemoryBuddy: could not analyse the heap dump (" + t + "). The dump is at " + hprofPath);
        }
    }

    private void run() throws IOException {
        parse(true);
        parse(false);
        Set<Long> targets = new HashSet<>();
        for (Long wr : wantedWeakRefs) {
            Long t = referentOf.get(wr);
            if (t != null && t != 0) targets.add(t);
        }
        if (targets.isEmpty()) {
            System.out.println("JMemoryBuddy: no tracked reference found in the dump; cannot compute a path to the GC root.");
            return;
        }
        for (Long target : targets) printPath(target);
    }

    // ---- HPROF parsing ------------------------------------------------------

    private void parse(boolean firstPass) throws IOException {
        try (Counter in = new Counter(new DataInputStream(new BufferedInputStream(new FileInputStream(path), 1 << 16)))) {
            readHeader(in);
            while (true) {
                int tag;
                try { tag = in.u1(); } catch (EOFException eof) { break; }
                in.u4();
                long length = in.u4() & 0xFFFFFFFFL;
                if (tag == TAG_STRING && firstPass) {
                    long id = in.id();
                    strings.put(id, new String(in.bytes((int) (length - idSize)), java.nio.charset.StandardCharsets.UTF_8));
                } else if (tag == TAG_LOAD_CLASS && firstPass) {
                    in.u4();
                    long classObjId = in.id();
                    in.u4();
                    classNameStringId.put(classObjId, in.id());
                } else if (tag == TAG_HEAP_DUMP || tag == TAG_HEAP_DUMP_SEGMENT) {
                    parseHeapDump(in, length, firstPass);
                } else {
                    in.skip(length);
                }
            }
        }
    }

    private void readHeader(Counter in) throws IOException {
        while (in.u1() != 0) { /* format string */ }
        idSize = in.u4();
        in.u4();
        in.u4();
    }

    private void parseHeapDump(Counter in, long length, boolean firstPass) throws IOException {
        long end = in.pos + length;
        while (in.pos < end) {
            int sub = in.u1();
            switch (sub) {
                case ROOT_JNI_GLOBAL:    addRoot(in.id(), firstPass, "JNI global"); in.id(); break;
                case ROOT_JNI_LOCAL:     addRoot(in.id(), firstPass, "JNI local"); in.u4(); in.u4(); break;
                case ROOT_JAVA_FRAME:    addRoot(in.id(), firstPass, "Java frame"); in.u4(); in.u4(); break;
                case ROOT_NATIVE_STACK:  addRoot(in.id(), firstPass, "native stack"); in.u4(); break;
                case ROOT_STICKY_CLASS:  addRoot(in.id(), firstPass, "sticky class"); break;
                case ROOT_THREAD_BLOCK:  addRoot(in.id(), firstPass, "thread block"); in.u4(); break;
                case ROOT_MONITOR_USED:  addRoot(in.id(), firstPass, "monitor"); break;
                case ROOT_THREAD_OBJECT: addRoot(in.id(), firstPass, "thread object"); in.u4(); in.u4(); break;
                case ROOT_UNKNOWN:       addRoot(in.id(), firstPass, "unknown root"); break;
                case CLASS_DUMP:         parseClassDump(in, firstPass); break;
                case INSTANCE_DUMP:      parseInstanceDump(in, firstPass); break;
                case OBJECT_ARRAY_DUMP:  parseObjectArray(in, firstPass); break;
                case PRIMITIVE_ARRAY_DUMP: parsePrimitiveArray(in); break;
                default: throw new IOException("Unknown heap sub-record 0x" + Integer.toHexString(sub) + " at " + in.pos);
            }
        }
    }

    private void addRoot(long id, boolean firstPass, String kind) {
        if (!firstPass && id != 0) roots.putIfAbsent(id, kind);
    }

    private void parseClassDump(Counter in, boolean firstPass) throws IOException {
        long classObjId = in.id();
        in.u4();
        long superId = in.id();
        in.id(); in.id(); in.id(); in.id(); in.id();
        in.u4();
        int cpCount = in.u2();
        for (int i = 0; i < cpCount; i++) { in.u2(); int t = in.u1(); in.skip(typeSizeWithId(t)); }
        int staticCount = in.u2();
        for (int i = 0; i < staticCount; i++) { in.id(); int t = in.u1(); in.skip(typeSizeWithId(t)); }
        int fieldCount = in.u2();
        long[] fields = new long[fieldCount * 2];
        for (int i = 0; i < fieldCount; i++) { fields[i * 2] = in.id(); fields[i * 2 + 1] = in.u1(); }
        if (firstPass) classes.put(classObjId, new ClassInfo(superId, fields));
    }

    private void parseInstanceDump(Counter in, boolean firstPass) throws IOException {
        long objId = in.id();
        in.u4();
        long classObjId = in.id();
        long nBytes = in.u4() & 0xFFFFFFFFL;
        if (firstPass) { in.skip(nBytes); return; }

        byte[] data = in.bytes((int) nBytes);
        objClass.put(objId, classObjId);
        boolean isAssertCollectable = ASSERT_COLLECTABLE.equals(className(classObjId));
        long acWeakRef = 0;
        long acId = 0;

        List<long[]> refs = new ArrayList<>();
        int off = 0;
        long cls = classObjId;
        while (cls != 0) {
            ClassInfo ci = classes.get(cls);
            if (ci == null) break;
            String clsName = className(cls);
            for (int i = 0; i < ci.fields.length; i += 2) {
                long nameId = ci.fields[i];
                int type = (int) ci.fields[i + 1];
                String fieldName = strings.get(nameId);
                if (type == 2) {
                    long ref = readId(data, off);
                    boolean isReferent = "java.lang.ref.Reference".equals(clsName) && "referent".equals(fieldName);
                    if (isReferent) {
                        if (ref != 0) referentOf.put(objId, ref); // weak: a bridge, never a strong edge
                    } else {
                        if (isAssertCollectable && "assertCollectable".equals(fieldName)) acWeakRef = ref;
                        if (ref != 0) refs.add(new long[]{nameId, ref});
                    }
                    off += idSize;
                } else {
                    if (isAssertCollectable && type == 11 && "id".equals(fieldName)) acId = readLong(data, off);
                    off += typeSize(type);
                }
            }
            cls = ci.superId;
        }
        if (isAssertCollectable && acId == wantedId && acWeakRef != 0) wantedWeakRefs.add(acWeakRef);
        storeEdges(objId, refs);
    }

    private void parseObjectArray(Counter in, boolean firstPass) throws IOException {
        long objId = in.id();
        in.u4();
        int n = in.u4();
        long arrayClass = in.id();
        if (firstPass) { in.skip((long) n * idSize); return; }
        objClass.put(objId, arrayClass);
        List<long[]> refs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long ref = in.id();
            if (ref != 0) refs.add(new long[]{-1, ref});
        }
        storeEdges(objId, refs);
    }

    private void parsePrimitiveArray(Counter in) throws IOException {
        in.id();
        in.u4();
        int n = in.u4();
        int t = in.u1();
        in.skip((long) n * typeSize(t));
    }

    private void storeEdges(long objId, List<long[]> refs) {
        if (objClass.size() > maxObjects) throw new RuntimeException("heap too large (> " + maxObjects + " objects) for in-process analysis");
        if (refs.isEmpty()) { edges.put(objId, EMPTY); return; }
        long[] flat = new long[refs.size() * 2];
        for (int i = 0; i < refs.size(); i++) { flat[i * 2] = refs.get(i)[0]; flat[i * 2 + 1] = refs.get(i)[1]; }
        edges.put(objId, flat);
    }

    private static final long[] EMPTY = new long[0];

    // ---- BFS + printing -----------------------------------------------------

    private void printPath(long target) {
        Map<Long, long[]> parent = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>(roots.keySet());
        queue.addAll(roots.keySet());
        boolean found = roots.containsKey(target);
        while (!queue.isEmpty() && !found) {
            long cur = queue.poll();
            long[] e = edges.get(cur);
            if (e == null) continue;
            for (int i = 0; i < e.length; i += 2) {
                long child = e[i + 1];
                if (visited.add(child)) {
                    parent.put(child, new long[]{cur, e[i]});
                    if (child == target) { found = true; break; }
                    queue.add(child);
                }
            }
        }

        String leak = simpleName(className(objClass.get(target))) + " @" + Long.toHexString(target);
        if (!found) {
            System.out.println("JMemoryBuddy: " + leak + " is retained, but no strong path to a GC root was found.");
            return;
        }

        List<Long> chain = new ArrayList<>();
        for (long node = target; ; ) {
            chain.add(node);
            long[] p = parent.get(node);
            if (p == null) break;
            node = p[0];
        }
        java.util.Collections.reverse(chain);

        int hops = chain.size() - 1;
        StringBuilder sb = new StringBuilder("\nJMemoryBuddy — path to GC root (" + hops + " hops) for leak: " + leak + ":\n");
        long root = chain.get(0);
        sb.append("  GC root [").append(roots.getOrDefault(root, "root")).append("] ")
                .append(simpleName(className(objClass.get(root)))).append('\n');
        int last = chain.size() - 1;
        for (int i = 1; i <= last; i++) {
            if (hops > MAX_HOPS && i == MAX_HOPS / 2) {            // abbreviate the middle of very long chains
                int resume = last - MAX_HOPS / 2;
                sb.append("    … ").append(resume - i + 1).append(" hops omitted …\n");
                i = resume;
            }
            long child = chain.get(i);
            long fieldNameId = parent.get(child)[1];
            String edge = fieldNameId == -1 ? "[]" : "." + strings.getOrDefault(fieldNameId, "?");
            sb.append("    ↓ ").append(simpleName(className(objClass.get(chain.get(i - 1))))).append(edge).append('\n');
            sb.append("  ").append(simpleName(className(objClass.get(child)))).append(i == last ? "   ← leaked" : "").append('\n');
        }
        System.out.println(sb);
    }

    // ---- helpers ------------------------------------------------------------

    private String className(Long classObjId) {
        if (classObjId == null) return "?";
        Long nameId = classNameStringId.get(classObjId);
        String n = nameId == null ? null : strings.get(nameId);
        return n == null ? "?" : n.replace('/', '.');
    }

    private static String simpleName(String n) {
        if (n == null) return "?";
        int i = n.lastIndexOf('.');
        return i < 0 ? n : n.substring(i + 1);
    }

    private long readId(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < idSize; i++) v = (v << 8) | (b[off + i] & 0xFFL);
        return v;
    }

    private static long readLong(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (b[off + i] & 0xFFL);
        return v;
    }

    private static int typeSize(int type) {
        switch (type) {
            case 2: return -1;          // object -> idSize via typeSizeWithId
            case 4: case 8: return 1;   // boolean, byte
            case 5: case 9: return 2;   // char, short
            case 6: case 10: return 4;  // float, int
            case 7: case 11: return 8;  // double, long
            default: throw new IllegalArgumentException("bad type " + type);
        }
    }

    private int typeSizeWithId(int type) { return type == 2 ? idSize : typeSize(type); }

    private static final class ClassInfo {
        final long superId;
        final long[] fields; // [nameStringId, type, ...]
        ClassInfo(long superId, long[] fields) { this.superId = superId; this.fields = fields; }
    }

    private final class Counter implements AutoCloseable {
        final DataInputStream in;
        long pos;
        Counter(DataInputStream in) { this.in = in; }
        int u1() throws IOException { int v = in.readUnsignedByte(); pos += 1; return v; }
        int u2() throws IOException { int v = in.readUnsignedShort(); pos += 2; return v; }
        int u4() throws IOException { int v = in.readInt(); pos += 4; return v; }
        long id() throws IOException {
            long v = 0;
            for (int i = 0; i < idSize; i++) v = (v << 8) | (in.readUnsignedByte() & 0xFFL);
            pos += idSize;
            return v;
        }
        byte[] bytes(int n) throws IOException { byte[] b = new byte[n]; in.readFully(b); pos += n; return b; }
        void skip(long n) throws IOException {
            long left = n;
            while (left > 0) { long s = in.skip(left); if (s <= 0) { if (in.read() < 0) throw new EOFException(); s = 1; } left -= s; }
            pos += n;
        }
        public void close() throws IOException { in.close(); }
    }
}
