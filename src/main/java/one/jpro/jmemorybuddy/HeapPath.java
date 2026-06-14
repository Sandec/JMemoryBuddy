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
 * A small, dependency-free HPROF reader that prints a strong-reference path from a
 * GC root to each not-collected object. The objects are located in the dump via
 * {@link TrackedWeakReference} instances (their {@code referent} field), so no strong
 * reference to the leaked object is needed and weak references are excluded from the path.
 *
 * <p>Best-effort: on any parse problem or when the heap exceeds {@code maxObjects} the
 * analysis is skipped silently — the heap dump itself is always still written.
 */
final class HeapPath {

    // HPROF top-level record tags
    private static final int TAG_STRING = 0x01;
    private static final int TAG_LOAD_CLASS = 0x02;
    private static final int TAG_HEAP_DUMP = 0x0C;
    private static final int TAG_HEAP_DUMP_SEGMENT = 0x1C;

    // HPROF heap-dump sub-record tags
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

    private static final String TRACKED_REF = TrackedWeakReference.class.getName();

    private final String path;
    private final int maxObjects;
    private int idSize;

    private final Map<Long, String> strings = new HashMap<>();        // stringId -> text
    private final Map<Long, Long> classNameStringId = new HashMap<>(); // classObjId -> stringId
    private final Map<Long, ClassInfo> classes = new HashMap<>();      // classObjId -> info

    // graph + metadata, built in pass 2
    private final Map<Long, long[]> edges = new HashMap<>();     // objId -> [fieldNameId, targetId, ...] (fieldNameId -1 = array element)
    private final Map<Long, Long> objClass = new HashMap<>();    // objId -> classObjId
    private final Map<Long, String> roots = new HashMap<>();     // rootId -> root kind
    private final Set<Long> targets = new HashSet<>();           // referents of TrackedWeakReference

    private HeapPath(String path, int maxObjects) {
        this.path = path;
        this.maxObjects = maxObjects;
    }

    /** Reads the dump and prints a path to a GC root for each tracked, not-collected object. */
    static void printPaths(String hprofPath, int maxObjects) {
        try {
            new HeapPath(hprofPath, maxObjects).run();
        } catch (Throwable t) {
            System.out.println("JMemoryBuddy: could not analyse the heap dump (" + t + "). The dump is at " + hprofPath);
        }
    }

    private void run() throws IOException {
        parse(true);   // classes + strings
        parse(false);  // graph + roots + targets
        if (targets.isEmpty()) {
            System.out.println("JMemoryBuddy: no tracked references found in the dump; cannot compute a path to the GC root.");
            return;
        }
        for (Long target : targets) {
            printPath(target);
        }
    }

    // ---- HPROF parsing ------------------------------------------------------

    private void parse(boolean firstPass) throws IOException {
        try (Counter in = new Counter(new DataInputStream(new BufferedInputStream(new FileInputStream(path), 1 << 16)))) {
            readHeader(in);
            while (true) {
                int tag;
                try {
                    tag = in.u1();
                } catch (EOFException eof) {
                    break;
                }
                in.u4();                 // timestamp delta
                long length = in.u4() & 0xFFFFFFFFL;
                if (tag == TAG_STRING && firstPass) {
                    long id = in.id();
                    strings.put(id, new String(in.bytes((int) (length - idSize)), java.nio.charset.StandardCharsets.UTF_8));
                } else if (tag == TAG_LOAD_CLASS && firstPass) {
                    in.u4();             // class serial
                    long classObjId = in.id();
                    in.u4();             // stack serial
                    long nameId = in.id();
                    classNameStringId.put(classObjId, nameId);
                } else if (tag == TAG_HEAP_DUMP || tag == TAG_HEAP_DUMP_SEGMENT) {
                    parseHeapDump(in, length, firstPass);
                } else {
                    in.skip(length);
                }
            }
        }
    }

    private void readHeader(Counter in) throws IOException {
        // null-terminated format string, e.g. "JAVA PROFILE 1.0.2"
        while (in.u1() != 0) { /* consume */ }
        idSize = in.u4();
        in.u4();                         // timestamp high
        in.u4();                         // timestamp low
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
        in.u4();                         // stack serial
        long superId = in.id();
        in.id(); in.id(); in.id(); in.id(); in.id(); // loader, signers, protection domain, reserved x2
        in.u4();                         // instance size
        int cpCount = in.u2();
        for (int i = 0; i < cpCount; i++) { in.u2(); int t = in.u1(); in.skip(typeSizeWithId(t)); }
        int staticCount = in.u2();
        for (int i = 0; i < staticCount; i++) { in.id(); int t = in.u1(); in.skip(typeSizeWithId(t)); }
        int fieldCount = in.u2();
        long[] fields = new long[fieldCount * 2]; // [nameId, type, ...]
        for (int i = 0; i < fieldCount; i++) {
            long nameId = in.id();
            int type = in.u1();
            fields[i * 2] = nameId;
            fields[i * 2 + 1] = type;
        }
        if (firstPass) classes.put(classObjId, new ClassInfo(superId, fields));
    }

    private void parseInstanceDump(Counter in, boolean firstPass) throws IOException {
        long objId = in.id();
        in.u4();                         // stack serial
        long classObjId = in.id();
        long nBytes = in.u4() & 0xFFFFFFFFL;
        if (firstPass) { in.skip(nBytes); return; }

        byte[] data = in.bytes((int) nBytes);
        objClass.put(objId, classObjId);
        boolean tracked = TRACKED_REF.equals(className(classObjId));
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
                if (type == 2) { // object
                    long ref = readId(data, off);
                    boolean isReferent = "java.lang.ref.Reference".equals(clsName) && "referent".equals(strings.get(nameId));
                    if (isReferent) {
                        if (tracked && ref != 0) targets.add(ref); // weak: a target, never a strong edge
                    } else if (ref != 0) {
                        refs.add(new long[]{nameId, ref});
                    }
                    off += idSize;
                } else {
                    off += typeSize(type);
                }
            }
            cls = ci.superId;
        }
        storeEdges(objId, refs);
    }

    private void parseObjectArray(Counter in, boolean firstPass) throws IOException {
        long objId = in.id();
        in.u4();                         // stack serial
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
        in.id();                         // array id
        in.u4();                         // stack serial
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
        // BFS from all roots; record parent edges until we reach the target.
        Map<Long, long[]> parent = new HashMap<>(); // childId -> [parentId, fieldNameId]
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

        String leak = "leak: " + simpleName(className(objClass.get(target))) + " @" + Long.toHexString(target);
        if (!found) {
            System.out.println("JMemoryBuddy: " + leak + " is retained, but no strong path to a GC root was found "
                    + "(it may be held only by a thread stack the dump doesn't expose).");
            return;
        }

        // reconstruct root -> target
        List<Long> chain = new ArrayList<>();
        long node = target;
        while (true) {
            chain.add(node);
            long[] p = parent.get(node);
            if (p == null) break;
            node = p[0];
        }
        java.util.Collections.reverse(chain);

        StringBuilder sb = new StringBuilder();
        sb.append("\nJMemoryBuddy — path to GC root for ").append(leak).append(":\n");
        long root = chain.get(0);
        sb.append("  GC root [").append(roots.getOrDefault(root, "root")).append("] ")
                .append(simpleName(className(objClass.get(root)))).append('\n');
        for (int i = 1; i < chain.size(); i++) {
            long child = chain.get(i);
            long fieldNameId = parent.get(child)[1];
            String edge = fieldNameId == -1 ? "[]" : "." + strings.getOrDefault(fieldNameId, "?");
            sb.append("    ↓ ").append(simpleName(className(objClass.get(chain.get(i - 1))))).append(edge).append('\n');
            sb.append("  ").append(simpleName(className(objClass.get(child))))
                    .append(i == chain.size() - 1 ? "   ← leaked" : "").append('\n');
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

    private static int typeSize(int type) {
        switch (type) {
            case 2: return -1;  // object -> idSize (patched by caller via typeSize w/ idSize); handled below
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

    /** DataInputStream wrapper that tracks the byte position and resolves id-sized reads. */
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
