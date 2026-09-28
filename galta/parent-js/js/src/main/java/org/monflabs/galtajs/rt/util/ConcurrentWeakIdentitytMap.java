/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.monflabs.galtajs.rt.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.util.Console;

public class ConcurrentWeakIdentitytMap<K, V> {

    private static final int MINIMAL_SIZE = 16;
    private static final int MINIMAL_RESIZE = 1024;
    private static final float LOAD_FACTOR = 0.75f;
    private static final int STRIPE_COUNT = 16;
    private static final int STRIPE_MASK = STRIPE_COUNT - 1;
    private static final int DRAIN_INTERVAL = 32;

    private final ReentrantLock[] locks;
    private final ReferenceQueue<K> referenceQueue = new ReferenceQueue<>();
    private final AtomicInteger writeCounter = new AtomicInteger();

    private volatile AtomicReferenceArray<Node<K, V>> table;
    // Updated under different stripe locks concurrently: must be atomic
    private final AtomicInteger elementCount = new AtomicInteger();
    // Collected references whose bucket belongs to a stripe that couldn't be locked
    // when they were polled - removed later by a thread holding (or getting) that stripe
    private final java.util.concurrent.ConcurrentLinkedQueue<Node<K, V>> pendingRemovals = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile int threshold;

    private static final class Node<K, V> extends WeakReference<K> implements Map.Entry<K, V> {
        final int hash;
        volatile V value;
        volatile Node<K, V> next;

        Node(int hash, K key, V value, ReferenceQueue<K> queue, Node<K, V> next) {
            super(key, queue);
            this.hash = hash;
            this.value = value;
            this.next = next;
        }

        @Override
        public K getKey() {
            return get();
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            V old = this.value;
            this.value = value;
            return old;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Map.Entry<?, ?> entry)) {
                return false;
            }
            Object key = get();
            return key == entry.getKey() && Objects.equals(value, entry.getValue());
        }

        @Override
        public int hashCode() {
            return hash + (value == null ? 0 : value.hashCode());
        }
    }

    public ConcurrentWeakIdentitytMap() {
        locks = new ReentrantLock[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            locks[i] = new ReentrantLock();
        }
        table = new AtomicReferenceArray<>(MINIMAL_SIZE);
        threshold = (int) (MINIMAL_SIZE * LOAD_FACTOR);
    }

    private ReentrantLock lockFor(int hash) {
        return locks[hash & STRIPE_MASK];
    }

    private void lockAll() {
        for (ReentrantLock lock : locks) {
            lock.lock();
        }
    }

    private void unlockAll() {
        for (int i = STRIPE_COUNT - 1; i >= 0; i--) {
            locks[i].unlock();
        }
    }

    public int size() {
        return elementCount.get();
    }

    public boolean isEmpty() {
        return elementCount.get() == 0;
    }

    public boolean containsKey(@NonNull Object key) {
        return getEntry(key) != null;
    }

    public boolean containsValue(Object value) {
        if (value == null) {
            return false;
        }

        AtomicReferenceArray<Node<K, V>> tab = table;
        for (int i = 0; i < tab.length(); i++) {
            for (Node<K, V> e = tab.get(i); e != null; e = e.next) {
                K k = e.get();
                if (k != null && Objects.equals(e.value, value)) {
                    return true;
                }
            }
        }
        return false;
    }

    public V get(@NonNull Object key) {
        Node<K, V> entry = getEntry(key);
        return entry == null ? null : entry.getValue();
    }

    public V getOrDefault(@NonNull Object key, V defaultValue) {
        Node<K, V> entry = getEntry(key);
        return entry == null ? defaultValue : entry.getValue();
    }

    public V getOrCreate(K key, Supplier<V> supplier) {
        Objects.requireNonNull(supplier, "supplier");

        Node<K, V> entry = getEntry(key);
        if (entry != null) {
            return entry.getValue();
        }

        int h = hash(key);
        ReentrantLock lock = lockFor(h);
        lock.lock();
        try {
            maybeDrain(h);
            entry = getEntry(key);
            if (entry != null) {
                return entry.getValue();
            }
            V value = Objects.requireNonNull(supplier.get(), "supplier returned null");
            insertLocked(h, key, value);
            return value;
        } finally {
            lock.unlock();
        }
    }

    protected void create(@NonNull K key, Supplier<V> lazyInitializer) {
        getOrCreate(key, lazyInitializer);
    }

    public V put(@NonNull K key, @NonNull V value) {
        int h = hash(key);
        ReentrantLock lock = lockFor(h);
        lock.lock();
        try {
            maybeDrain(h);
            Node<K, V> entry = getEntry(key);
            if (entry != null) {
                V old = entry.value;
                entry.value = value;
                return old;
            }
            insertLocked(h, key, value);
            return null;
        } finally {
            lock.unlock();
        }
    }

    public V putIfAbsent(@NonNull K key, @NonNull V value) {
        Node<K, V> entry = getEntry(key);
        if (entry != null) {
            return entry.getValue();
        }

        int h = hash(key);
        ReentrantLock lock = lockFor(h);
        lock.lock();
        try {
            maybeDrain(h);
            entry = getEntry(key);
            if (entry != null) {
                return entry.getValue();
            }
            insertLocked(h, key, value);
            return null;
        } finally {
            lock.unlock();
        }
    }

    public boolean remove(@NonNull Object key, @NonNull Object value) {
        if (value == null) {
            return false;
        }

        int h = hash(key);
        ReentrantLock lock = lockFor(h);
        lock.lock();
        try {
            maybeDrain(h);
            Node<K, V> entry = getEntry(key);
            if (entry != null && Objects.equals(entry.getValue(), value)) {
                return removeEntryLocked(entry);
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public V remove(@NonNull Object key) {
        int h = hash(key);
        ReentrantLock lock = lockFor(h);
        lock.lock();
        try {
            maybeDrain(h);
            Node<K, V> entry = getEntry(key);
            if (entry == null) {
                return null;
            }
            V old = entry.value;
            removeEntryLocked(entry);
            return old;
        } finally {
            lock.unlock();
        }
    }

    public boolean replace(@NonNull K key, @NonNull V oldValue, @NonNull V newValue) {
        int h = hash(key);
        ReentrantLock lock = lockFor(h);
        lock.lock();
        try {
            maybeDrain(h);
            Node<K, V> entry = getEntry(key);
            if (entry != null && Objects.equals(entry.getValue(), oldValue)) {
                entry.value = newValue;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public V replace(@NonNull K key, @NonNull V value) {
        int h = hash(key);
        ReentrantLock lock = lockFor(h);
        lock.lock();
        try {
            maybeDrain(h);
            Node<K, V> entry = getEntry(key);
            if (entry == null) {
                return null;
            }
            V old = entry.value;
            entry.value = value;
            return old;
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lockAll();
        try {
            table = new AtomicReferenceArray<>(MINIMAL_SIZE);
            threshold = (int) (MINIMAL_SIZE * LOAD_FACTOR);
            elementCount.set(0);
            while (referenceQueue.poll() != null) {
                // drain
            }
            pendingRemovals.clear();
        } finally {
            unlockAll();
        }
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    public V computeIfAbsent(@NonNull K key, @NonNull Function<? super K, ? extends V> mappingFunction) {
        Node<K, V> entry = getEntry(key);
        if (entry != null) {
            return entry.getValue();
        }

        int h = hash(key);
        ReentrantLock lock = lockFor(h);
        lock.lock();
        try {
            maybeDrain(h);
            entry = getEntry(key);
            if (entry != null) {
                return entry.getValue();
            }

            V value = mappingFunction.apply(key);
            if (value == null) {
                return null;
            }

            insertLocked(h, key, value);
            return value;
        } finally {
            lock.unlock();
        }
    }

    public V computeIfPresent(@NonNull K key, @NonNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        int h = hash(key);
        ReentrantLock lock = lockFor(h);
        lock.lock();
        try {
            maybeDrain(h);
            Node<K, V> entry = getEntry(key);
            if (entry == null) {
                return null;
            }

            V oldValue = entry.getValue();
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue == null) {
                removeEntryLocked(entry);
                return null;
            }

            entry.value = newValue;
            return newValue;
        } finally {
            lock.unlock();
        }
    }

    public V compute(@NonNull K key, @NonNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        int h = hash(key);
        ReentrantLock lock = lockFor(h);
        lock.lock();
        try {
            maybeDrain(h);
            Node<K, V> entry = getEntry(key);
            V oldValue = entry == null ? null : entry.getValue();
            V newValue = remappingFunction.apply(key, oldValue);

            if (newValue == null) {
                if (entry != null) {
                    removeEntryLocked(entry);
                }
                return null;
            }

            if (entry != null) {
                entry.value = newValue;
            } else {
                insertLocked(h, key, newValue);
            }
            return newValue;
        } finally {
            lock.unlock();
        }
    }

    public V merge(@NonNull K key, @NonNull V value, @NonNull BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        int h = hash(key);
        ReentrantLock lock = lockFor(h);
        lock.lock();
        try {
            maybeDrain(h);
            Node<K, V> entry = getEntry(key);
            if (entry == null) {
                insertLocked(h, key, value);
                return value;
            }

            V oldValue = entry.getValue();
            V newValue = remappingFunction.apply(oldValue, value);
            if (newValue == null) {
                removeEntryLocked(entry);
                return null;
            }

            entry.value = newValue;
            return newValue;
        } finally {
            lock.unlock();
        }
    }

    private Node<K, V> getEntry(Object key) {
        AtomicReferenceArray<Node<K, V>> tab = table;
        int index = hash(key) & (tab.length() - 1);
        Node<K, V> entry = tab.get(index);
        while (entry != null) {
            if (entry.get() == key) {
                return entry;
            }
            entry = entry.next;
        }
        return null;
    }

    private void insertLocked(int keyHashCode, K key, V value) {
        AtomicReferenceArray<Node<K, V>> tab = table;
        int index = keyHashCode & (tab.length() - 1);

        Node<K, V> head = tab.get(index);
        Node<K, V> entry = new Node<>(keyHashCode, key, value, referenceQueue, head);
        tab.set(index, entry);
        int count = elementCount.incrementAndGet();

        if (count > threshold) {
            rehashGlobal(count, true);
        }
    }

    private boolean removeEntryLocked(Node<K, V> toRemove) {
        AtomicReferenceArray<Node<K, V>> tab = table;
        if (toRemove == null) {
            return false;
        }

        int index = toRemove.hash & (tab.length() - 1);
        Node<K, V> last = null;
        Node<K, V> entry = tab.get(index);

        while (entry != null) {
            if (toRemove == entry) {
                if (last == null) {
                    tab.set(index, entry.next);
                } else {
                    last.next = entry.next;
                }
                elementCount.decrementAndGet();
                return true;
            }
            last = entry;
            entry = entry.next;
        }
        return false;
    }

    // Called with the lock of the stripe of hash h held. A bucket belongs to exactly one
    // stripe (the table length is a multiple of the stripe count), so a collected node
    // can only be unlinked under ITS stripe's lock: other stripes are only tried, never
    // waited on (waiting while holding a lock could deadlock), and are deferred if busy.
    @SuppressWarnings("unchecked")
    private void maybeDrain(int h) {
        if (writeCounter.incrementAndGet() % DRAIN_INTERVAL != 0) {
            return;
        }
        Node<K, V> polled;
        while ((polled = (Node<K, V>) referenceQueue.poll()) != null) {
            pendingRemovals.add(polled);
        }
        int held = h & STRIPE_MASK;
        for (int n = pendingRemovals.size(); n > 0; n--) {
            Node<K, V> toRemove = pendingRemovals.poll();
            if (toRemove == null) {
                break;
            }
            int stripe = toRemove.hash & STRIPE_MASK;
            if (stripe == held) {
                removeEntryLocked(toRemove);
            } else if (locks[stripe].tryLock()) {
                try {
                    removeEntryLocked(toRemove);
                } finally {
                    locks[stripe].unlock();
                }
            } else {
                pendingRemovals.add(toRemove);
            }
        }
    }

//    @SuppressWarnings("unchecked")
//    private void drainQueueGlobal() {
//        Node<K, V> toRemove;
//        while ((toRemove = (Node<K, V>) referenceQueue.poll()) != null) {
//            removeEntryLocked(toRemove);
//        }
//
//        AtomicReferenceArray<Node<K, V>> tab = table;
//        int length = tab.length();
//        if (elementCount > threshold) {
//            rehashGlobal(elementCount, true);
//        } else if (length > MINIMAL_SIZE && elementCount < (int) (length * (LOAD_FACTOR / 2f))) {
//            rehashGlobal(elementCount, false);
//        }
//    }

    // Called while holding one stripe lock: blocking on the others (in any order) could
    // deadlock with another thread doing the same, or with clear(). So the other stripes
    // are only tried; if one is busy the resize is skipped - the map stays correct with
    // longer chains, and the next insertion past the threshold tries again.
    private boolean tryLockAll() {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            if (!locks[i].tryLock()) {
                for (int j = i - 1; j >= 0; j--) {
                    locks[j].unlock();
                }
                return false;
            }
        }
        return true;
    }

    private void rehashGlobal(int newSize, boolean resize) {
        if (!tryLockAll()) {
            return;
        }
        try {
            AtomicReferenceArray<Node<K, V>> oldTab = table;
            int oldLength = oldTab.length();

            int targetLength = Math.max(
                    resize ? MINIMAL_RESIZE : MINIMAL_SIZE,
                    powerOfTwo(Math.max(1, (int) (newSize / LOAD_FACTOR)))
            );

            if (oldLength == targetLength) {
                return;
            }

            AtomicReferenceArray<Node<K, V>> newTab = new AtomicReferenceArray<>(targetLength);
            int liveCount = 0;

            for (int i = 0; i < oldTab.length(); i++) {
                for (Node<K, V> e = oldTab.get(i); e != null; e = e.next) {
                    K key = e.get();
                    if (key == null) {
                        continue;
                    }

                    int index = e.hash & (targetLength - 1);
                    Node<K, V> head = newTab.get(index);
                    Node<K, V> copy = new Node<>(e.hash, key, e.value, referenceQueue, head);
                    newTab.set(index, copy);
                    liveCount++;
                }
            }

            table = newTab;
            elementCount.set(liveCount);
            // The nodes were copied: references still pending point to the old nodes
            pendingRemovals.clear();
            threshold = (int) (targetLength * LOAD_FACTOR);
        } finally {
            unlockAll();
        }
    }

    private static int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = System.identityHashCode(key)) ^ (h >>> 16);
    }

    private int powerOfTwo(int number) {
        if (number <= 1) {
            return 1;
        }
        return 1 << (32 - Integer.numberOfLeadingZeros(number - 1));
    }

    public void dumpStats() {
        AtomicReferenceArray<Node<K, V>> tab = table;
        int total = 0;
        int min = 0;
        int max = 0;
        double avg = 0;

        if (tab.length() > 0) {
            min = Integer.MAX_VALUE;
            max = Integer.MIN_VALUE;

            for (int i = 0; i < tab.length(); i++) {
                int c = 0;
                for (Node<K, V> e = tab.get(i); e != null; e = e.next) {
                    if (e.get() != null) {
                        c++;
                    }
                }
                min = Math.min(min, c);
                max = Math.max(max, c);
                total += c;
            }
            avg = ((double) total) / tab.length();
        }

        Console.log("Map count={0}", elementCount.get());
        Console.log("  Total live entries={0}", total);
        Console.log("  Map slots={0}", tab.length());
        Console.log("  Avg slot={0}", avg);
        Console.log("  Min slot={0}", min);
        Console.log("  Max slot={0}", max);
    }
}
