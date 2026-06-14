package one.jpro.jmemorybuddy;

import java.lang.ref.WeakReference;

/**
 * A marker {@link WeakReference} used so the not-collected object can be located inside a
 * heap dump (by this class name) and its {@code referent} followed — without ever holding a
 * strong reference to it. See {@link HeapPath}.
 */
final class TrackedWeakReference extends WeakReference<Object> {
    TrackedWeakReference(Object referent) {
        super(referent);
    }
}
