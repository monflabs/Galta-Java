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
package tests.javascript.util;

import java.lang.reflect.Field;

import org.monflabs.galtajs.jsonfactory.JSArrayImpl;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArray;
import org.monflabs.galtajs.util.SparseList;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Tests for JSArrayImpl's three-tier storage:
 *   DENSE  — no holes, no SparseList
 *   HOLEY  — interior HOLE sentinels, no SparseList (new tier)
 *   SPARSE — SparseList, only for gaps > MAX_HOLEY_GAP or index >= MAX_INT
 */
public class JSArrayImplTest extends JavaScriptStrictTestCase {

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    private static final Field f_sparseArray;
    private static final Field f_firstItem;
    private static final Field f_lastItem;

    static {
        try {
            f_sparseArray = JSArrayImpl.class.getDeclaredField("sparseArray");
            f_sparseArray.setAccessible(true);
            f_firstItem = JSArrayImpl.class.getDeclaredField("firstItem");
            f_firstItem.setAccessible(true);
            f_lastItem = JSArrayImpl.class.getDeclaredField("lastItem");
            f_lastItem.setAccessible(true);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private SparseList<Object> sparse(JSArrayImpl a) {
        try { return (SparseList<Object>) f_sparseArray.get(a); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private long firstItem(JSArrayImpl a) {
        try { return f_firstItem.getLong(a); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private long lastItem(JSArrayImpl a) {
        try { return f_lastItem.getLong(a); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    /** True when no SparseList is in use (array is dense or holey). */
    private boolean isSparse(JSArrayImpl a) {
        return sparse(a) != null;
    }
    /** True when no SparseList AND no interior holes (all arrayHas checks pass for real indices). */
    private boolean isPurelyDense(JSArrayImpl a) {
        if(isSparse(a)) return false;
        long fi = firstItem(a);
        long len = a.arrayLength();
        for(long i=fi; i<len; i++) {
            if(!a.arrayHas(i)) return false;
        }
        return true;
    }
    /** True when no SparseList but at least one hole exists in the array range. */
    private boolean isHoley(JSArrayImpl a) {
        if(isSparse(a)) return false;
        long len = a.arrayLength();
        for(long i=0; i<len; i++) {
            if(!a.arrayHas(i)) return true;
        }
        return false;
    }

    private BuiltinArray array(Object... values) {
        BuiltinArray a = new BuiltinArray(getEnvironment());
        for (Object v : values) a.arrayAdd(v);
        return a;
    }

    // -------------------------------------------------------------------------
    // Baseline: normal dense array (firstItem=0, lastItem=0, no holes)
    // -------------------------------------------------------------------------

    public void testNormalDenseArray() {
        BuiltinArray a = array("a", "b", "c");

        assertFalse(isSparse(a));
        assertEquals(0L, firstItem(a));
        assertEquals(0L, lastItem(a));
        assertTrue(isPurelyDense(a));

        assertEquals(3L, a.arrayLength());
        assertEquals(3, a.size());
        assertEquals("a", a.arrayGet(0, null));
        assertEquals("b", a.arrayGet(1, null));
        assertEquals("c", a.arrayGet(2, null));
        assertTrue(a.arrayHas(0));
        assertTrue(a.arrayHas(2));
        assertFalse(a.arrayHas(3));
    }

    public void testArraySet() {
        BuiltinArray a = array("a", "b", "c");
        a.arraySet(1, "B");

        assertFalse(isSparse(a));
        assertEquals("B", a.arrayGet(1, null));
        assertEquals(3L, a.arrayLength());
    }

    public void testArraySetAppend() {
        BuiltinArray a = array("a", "b");
        a.arraySet(2, "c"); // adjacent to end — stays dense

        assertFalse(isSparse(a));
        assertEquals(3L, a.arrayLength());
        assertEquals("c", a.arrayGet(2, null));
    }

    public void testArraySetPrepend() {
        // firstItem=6, set index 5 — adjacent prepend, must stay dense
        BuiltinArray a = new BuiltinArray(getEnvironment());
        a.arraySetLength(10000, DESC_CHECK.NONE);
        a.arraySet(6, "d", DESC_CHECK.NONE); // firstItem=6, data=["d"]
        a.arraySet(5, "c", DESC_CHECK.NONE); // adjacent prepend: index == firstItem-1

        assertFalse("prepend adjacent to firstItem must not go sparse", isSparse(a));
        assertEquals(5L, firstItem(a));
        assertEquals(10000L, a.arrayLength()); // JS length unchanged
        assertTrue(a.arrayHas(5));
        assertTrue(a.arrayHas(6));
        assertFalse(a.arrayHas(4));
        assertFalse(a.arrayHas(7));
        assertEquals("c", a.arrayGet(5, null));
        assertEquals("d", a.arrayGet(6, null));
    }

    public void testArraySetPrependChain() {
        // Repeated adjacent prepends: firstItem should keep decrementing
        BuiltinArray a = new BuiltinArray(getEnvironment());
        a.arraySetLength(10000, DESC_CHECK.NONE);
        a.arraySet(3, "d", DESC_CHECK.NONE); // firstItem=3
        a.arraySet(2, "c", DESC_CHECK.NONE); // firstItem=2
        a.arraySet(1, "b", DESC_CHECK.NONE); // firstItem=1
        a.arraySet(0, "a", DESC_CHECK.NONE); // firstItem=0

        assertFalse(isSparse(a));
        assertEquals(0L, firstItem(a));
        assertEquals(10000L, a.arrayLength());
        assertEquals("a", a.arrayGet(0, null));
        assertEquals("b", a.arrayGet(1, null));
        assertEquals("c", a.arrayGet(2, null));
        assertEquals("d", a.arrayGet(3, null));
    }

    public void testArraySetNonAdjacentBelowFirstItem_SmallGapStaysHoley() {
        // firstItem=6, set at index 4: gap=1 at position 5 — stays holey (< MAX_HOLEY_GAP)
        BuiltinArray a = new BuiltinArray(getEnvironment());
        a.arraySetLength(10000, DESC_CHECK.NONE);
        a.arraySet(6, "d", DESC_CHECK.NONE); // firstItem=6
        a.arraySet(4, "b", DESC_CHECK.NONE); // gap of 1 at JS[5]

        assertFalse("small leading gap must stay in holey mode (no SparseList)", isSparse(a));
        assertTrue(isHoley(a));
        assertEquals(4L, firstItem(a));
        assertEquals("b", a.arrayGet(4, null));
        assertFalse(a.arrayHas(5));   // interior hole
        assertEquals("d", a.arrayGet(6, null));
        assertEquals(RuntimeUtil.UNDEFINED, a.arrayGet(5, RuntimeUtil.UNDEFINED));
        assertEquals(10000L, a.arrayLength());
    }

    public void testArraySetNonAdjacentBelowFirstItem_LargeGapGoeSparse() {
        // Gap larger than MAX_HOLEY_GAP → must go sparse
        BuiltinArray a = new BuiltinArray(getEnvironment());
        a.arraySet(2000, "d", DESC_CHECK.NONE); // firstItem=2000
        a.arraySet(0, "b", DESC_CHECK.NONE);    // gap=1999 > MAX_HOLEY_GAP=1024

        assertTrue("large leading gap must trigger SparseList", isSparse(a));
        assertEquals("d", a.arrayGet(2000, null));
        assertEquals("b", a.arrayGet(0, null));
        assertEquals(RuntimeUtil.UNDEFINED, a.arrayGet(1000, RuntimeUtil.UNDEFINED));
    }

    public void testSpliceInsertAdjacentBelowFirstItemTriggersSparse() {
        // arrayAdd (splice) at firstItem-1 shifts the leading hole into a gap → sparse
        BuiltinArray a = new BuiltinArray(getEnvironment());
        a.arraySetLength(10000, DESC_CHECK.NONE);
        a.arraySet(6, "d", DESC_CHECK.NONE); // firstItem=6, data=["d"]
        a.arrayAdd(5L, "c", DESC_CHECK.NONE); // splice at 5: hole[5] shifts to [6] → gap

        assertTrue("splice at firstItem-1 must trigger SparseList (shifted leading hole)", isSparse(a));
        assertEquals("c", a.arrayGet(5, null));
        assertEquals(RuntimeUtil.UNDEFINED, a.arrayGet(6, RuntimeUtil.UNDEFINED)); // the shifted hole
        assertEquals("d", a.arrayGet(7, null)); // data shifted up
        assertEquals(10001L, a.arrayLength());
    }

    // -------------------------------------------------------------------------
    // arraySet on empty-data array (new Array(N) pattern) — must stay dense
    // -------------------------------------------------------------------------

    public void testSetOnPreallocatedArray() {
        // new Array(10000) then a[2] = xxx — the classic case
        BuiltinArray a = new BuiltinArray(getEnvironment());
        a.arraySetLength(10000, DESC_CHECK.NONE); // lastItem=10000, super.size()=0

        a.arraySet(2, "x", DESC_CHECK.NONE);

        assertFalse("set on empty dense block must stay dense", isSparse(a));
        assertEquals(2L, firstItem(a));
        assertEquals(10000L, a.arrayLength());
        assertFalse(a.arrayHas(0));
        assertFalse(a.arrayHas(1));
        assertTrue(a.arrayHas(2));
        assertFalse(a.arrayHas(3));
        assertEquals("x", a.arrayGet(2, null));
        assertEquals(RuntimeUtil.UNDEFINED, a.arrayGet(0, RuntimeUtil.UNDEFINED));
    }

    public void testSetOnPreallocatedArrayThenExtend() {
        // new Array(10000), a[2]='x', a[3]='y' — contiguous, stays dense
        BuiltinArray a = new BuiltinArray(getEnvironment());
        a.arraySetLength(10000, DESC_CHECK.NONE);
        a.arraySet(2, "x", DESC_CHECK.NONE); // firstItem=2
        a.arraySet(3, "y", DESC_CHECK.NONE); // adjacent → stays dense

        assertFalse(isSparse(a));
        assertEquals(2L, firstItem(a));
        assertEquals(10000L, a.arrayLength());
        assertEquals("x", a.arrayGet(2, null));
        assertEquals("y", a.arrayGet(3, null));
    }

    public void testSetOnPreallocatedArrayThenSmallGap() {
        // new Array(10000), a[2]='x', a[5]='y' — gap [3..4] → holey (gap=2 < MAX_HOLEY_GAP)
        BuiltinArray a = new BuiltinArray(getEnvironment());
        a.arraySetLength(10000, DESC_CHECK.NONE);
        a.arraySet(2, "x", DESC_CHECK.NONE); // firstItem=2, data=[x]
        a.arraySet(5, "y", DESC_CHECK.NONE); // gap [3..4] — stays holey

        assertFalse("small gap must stay in holey tier", isSparse(a));
        assertTrue(isHoley(a));
        assertEquals(10000L, a.arrayLength());
        assertEquals("x", a.arrayGet(2, null));
        assertFalse(a.arrayHas(3));
        assertFalse(a.arrayHas(4));
        assertEquals("y", a.arrayGet(5, null));
    }

    public void testSetWithLargeGapTriggersSparse() {
        // Gap larger than MAX_HOLEY_GAP → must go to SparseList
        BuiltinArray a = array("a", "b");
        a.arraySet(2000, "x", DESC_CHECK.NONE); // gap=1998 > 1024

        assertTrue("gap > MAX_HOLEY_GAP must trigger SparseList", isSparse(a));
        assertEquals(2001L, a.arrayLength());
        assertEquals("x", a.arrayGet(2000, null));
        assertEquals(RuntimeUtil.UNDEFINED, a.arrayGet(100, RuntimeUtil.UNDEFINED));
    }

    public void testSetOnEmptyArrayNoLength() {
        // Bare empty array: a[5] = 'x' — no prior setLength, firstItem should shift
        BuiltinArray a = new BuiltinArray(getEnvironment());
        a.arraySet(5, "x", DESC_CHECK.NONE);

        assertFalse(isSparse(a));
        assertEquals(5L, firstItem(a));
        assertEquals(6L, a.arrayLength()); // length = firstItem + size = 5+1
        assertFalse(a.arrayHas(0));
        assertFalse(a.arrayHas(4));
        assertTrue(a.arrayHas(5));
        assertEquals("x", a.arrayGet(5, null));
    }

    public void testSetPreservesLengthFromDeletedFirstItems() {
        // Delete all front items (super.size()→0, firstItem>0), then set below firstItem
        BuiltinArray a = array("a", "b");  // firstItem=0, lastItem=0, data=[a,b], length=2
        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1, data=[b], length=2
        a.arrayDelete(1, DESC_CHECK.NONE); // firstItem=2, data=[], length=2

        // Now super.size()==0, firstItem=2, arrayLength()=2
        a.arraySet(0, "z", DESC_CHECK.NONE); // index=0, super.size()==0 → firstItem=0, preserve length=2

        assertFalse(isSparse(a));
        assertEquals(0L, firstItem(a));
        assertEquals(2L, a.arrayLength()); // JS length preserved
        assertTrue(a.arrayHas(0));
        assertFalse(a.arrayHas(1)); // still a hole
        assertEquals("z", a.arrayGet(0, null));
    }

    public void testSpliceInsertOnPreallocatedArray() {
        // new Array(10000), then arrayAdd(2, 'x') — splice insert at index 2
        BuiltinArray a = new BuiltinArray(getEnvironment());
        a.arraySetLength(10000, DESC_CHECK.NONE);
        a.arrayAdd(2L, "x", DESC_CHECK.NONE);

        assertFalse("splice insert on empty dense block must stay dense", isSparse(a));
        assertEquals(2L, firstItem(a));
        assertEquals(10001L, a.arrayLength()); // length grew by 1
        assertTrue(a.arrayHas(2));
        assertFalse(a.arrayHas(3));
        assertEquals("x", a.arrayGet(2, null));
    }

    // -------------------------------------------------------------------------
    // arrayDelete: first element — no SparseList, no HOLEs
    // -------------------------------------------------------------------------

    public void testDeleteFirstElement() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(0, DESC_CHECK.NONE);

        assertFalse("should stay in no-SparseList mode after deleting first element", isSparse(a));
        assertEquals(1L, firstItem(a));
        assertEquals(3L, a.arrayLength());
        assertEquals(3, a.size());

        assertFalse(a.arrayHas(0));
        assertTrue(a.arrayHas(1));
        assertTrue(a.arrayHas(2));
        assertEquals(RuntimeUtil.UNDEFINED, a.arrayGet(0, RuntimeUtil.UNDEFINED));
        assertEquals("b", a.arrayGet(1, null));
        assertEquals("c", a.arrayGet(2, null));
    }

    public void testDeleteFirstElementRepeated() {
        BuiltinArray a = array("a", "b", "c", "d", "e");
        a.arrayDelete(0, DESC_CHECK.NONE);
        a.arrayDelete(1, DESC_CHECK.NONE);
        a.arrayDelete(2, DESC_CHECK.NONE);

        assertFalse("should stay in no-SparseList mode after three front deletions", isSparse(a));
        assertEquals(3L, firstItem(a));
        assertEquals(5L, a.arrayLength());

        assertFalse(a.arrayHas(0));
        assertFalse(a.arrayHas(1));
        assertFalse(a.arrayHas(2));
        assertTrue(a.arrayHas(3));
        assertTrue(a.arrayHas(4));
        assertEquals("d", a.arrayGet(3, null));
        assertEquals("e", a.arrayGet(4, null));
    }

    public void testListGetAfterFirstItemShift() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1

        // List.get() uses JS indices
        assertEquals(RuntimeUtil.UNDEFINED, a.get(0));
        assertEquals("b", a.get(1));
        assertEquals("c", a.get(2));
    }

    // -------------------------------------------------------------------------
    // arrayDelete: last element — no SparseList, length preserved via lastItem
    // -------------------------------------------------------------------------

    public void testDeleteLastElement() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(2, DESC_CHECK.NONE);

        assertFalse("should stay in no-SparseList mode after deleting last element", isSparse(a));
        assertEquals(0L, firstItem(a));
        assertEquals(3L, lastItem(a)); // JS length preserved
        assertEquals(3L, a.arrayLength());
        assertEquals(3, a.size());

        assertTrue(a.arrayHas(0));
        assertTrue(a.arrayHas(1));
        assertFalse(a.arrayHas(2));
        assertEquals("a", a.arrayGet(0, null));
        assertEquals("b", a.arrayGet(1, null));
        assertEquals(RuntimeUtil.UNDEFINED, a.arrayGet(2, RuntimeUtil.UNDEFINED));
    }

    public void testDeleteLastElementThenFirst() {
        BuiltinArray a = array("a", "b", "c", "d");
        a.arrayDelete(3, DESC_CHECK.NONE); // delete last → lastItem=4, ArrayList=[a,b,c]
        a.arrayDelete(0, DESC_CHECK.NONE); // delete first → firstItem=1, ArrayList=[b,c]

        assertFalse(isSparse(a));
        assertEquals(1L, firstItem(a));
        assertEquals(4L, a.arrayLength());

        assertFalse(a.arrayHas(0));
        assertTrue(a.arrayHas(1));
        assertTrue(a.arrayHas(2));
        assertFalse(a.arrayHas(3));
        assertEquals("b", a.arrayGet(1, null));
        assertEquals("c", a.arrayGet(2, null));
    }

    // -------------------------------------------------------------------------
    // arrayDelete: middle element — now stays in holey tier (no SparseList)
    // -------------------------------------------------------------------------

    public void testDeleteMiddleElementStaysHoley() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(1, DESC_CHECK.NONE); // interior → HOLE, no SparseList

        assertFalse("middle delete must NOT trigger SparseList", isSparse(a));
        assertTrue("middle delete must produce a holey array", isHoley(a));
        assertEquals(3L, a.arrayLength());

        assertTrue(a.arrayHas(0));
        assertFalse(a.arrayHas(1));
        assertTrue(a.arrayHas(2));
        assertEquals("a", a.arrayGet(0, null));
        assertEquals(RuntimeUtil.UNDEFINED, a.arrayGet(1, RuntimeUtil.UNDEFINED));
        assertEquals("c", a.arrayGet(2, null));
    }

    public void testDeleteFirstThenMiddleStaysHoley() {
        // After deleting index 0 (firstItem=1, data=[b,c,d]),
        // deleting index 2 (middle of [1,2,3]) should go holey, not sparse.
        BuiltinArray a = array("a", "b", "c", "d");
        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1, dense data=[b,c,d]
        assertFalse(isSparse(a));

        a.arrayDelete(2, DESC_CHECK.NONE); // JS[2]=c is the middle of dense [1,2,3]
        assertFalse("middle delete after first-delete must NOT trigger SparseList", isSparse(a));
        assertTrue(isHoley(a));

        assertEquals(4L, a.arrayLength());
        assertEquals("b",  a.arrayGet(1, null));
        assertEquals(RuntimeUtil.UNDEFINED, a.arrayGet(2, RuntimeUtil.UNDEFINED));
        assertEquals("d",  a.arrayGet(3, null));
    }

    public void testDeleteHoleThenHoleIsNoOp() {
        // Deleting a HOLE position is a no-op.
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(1, DESC_CHECK.NONE); // interior → HOLE
        a.arrayDelete(1, DESC_CHECK.NONE); // already a hole — no-op

        assertFalse(isSparse(a));
        assertEquals(3L, a.arrayLength());
        assertFalse(a.arrayHas(1));
    }

    public void testDeleteAllMiddleElementsCompacts() {
        // Delete both interior elements of [a,b,c,d]; only a and d remain.
        BuiltinArray a = array("a", "b", "c", "d");
        a.arrayDelete(1, DESC_CHECK.NONE);
        a.arrayDelete(2, DESC_CHECK.NONE);

        assertFalse(isSparse(a));
        assertEquals(4L, a.arrayLength());
        assertTrue(a.arrayHas(0));
        assertFalse(a.arrayHas(1));
        assertFalse(a.arrayHas(2));
        assertTrue(a.arrayHas(3));
    }

    public void testDeleteLastThenLastBecomesHoleCompact() {
        // When we delete the last real element, trailing HOLEs are compacted
        // and lastItem preserves the JS length.
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(1, DESC_CHECK.NONE); // interior HOLE → [a, HOLE, c]
        a.arrayDelete(2, DESC_CHECK.NONE); // last element → compacts trailing HOLEs → [a], lastItem=3

        assertFalse(isSparse(a));
        assertEquals(3L, a.arrayLength()); // JS length preserved
        assertTrue(a.arrayHas(0));
        assertFalse(a.arrayHas(1));
        assertFalse(a.arrayHas(2));
        assertEquals("a", a.arrayGet(0, null));
    }

    // -------------------------------------------------------------------------
    // arraySetLength: extend — just sets lastItem, no SparseList
    // -------------------------------------------------------------------------

    public void testSetLengthExtend() {
        BuiltinArray a = array("a", "b", "c");
        a.arraySetLength(100, DESC_CHECK.NONE);

        assertFalse("setLength extend should stay in no-SparseList mode", isSparse(a));
        assertEquals(100L, lastItem(a));
        assertEquals(100L, a.arrayLength());
        assertEquals(100, a.size());

        assertTrue(a.arrayHas(0));
        assertTrue(a.arrayHas(2));
        assertFalse(a.arrayHas(3));
        assertFalse(a.arrayHas(99));
        assertEquals("a", a.arrayGet(0, null));
        assertEquals(RuntimeUtil.UNDEFINED, a.arrayGet(50, RuntimeUtil.UNDEFINED));
    }

    public void testSetLengthExact() {
        BuiltinArray a = array("a", "b", "c");
        a.arraySetLength(3, DESC_CHECK.NONE); // same as current length

        assertFalse(isSparse(a));
        assertEquals(3L, a.arrayLength());
    }

    public void testSetLengthShrink() {
        BuiltinArray a = array("a", "b", "c", "d", "e");
        a.arraySetLength(3, DESC_CHECK.NONE);

        assertFalse(isSparse(a));
        assertEquals(0L, lastItem(a)); // reset
        assertEquals(3L, a.arrayLength());
        assertEquals(3, a.size());

        assertEquals("a", a.arrayGet(0, null));
        assertEquals("c", a.arrayGet(2, null));
        assertEquals(RuntimeUtil.UNDEFINED, a.arrayGet(3, RuntimeUtil.UNDEFINED));
    }

    public void testSetLengthShrinkBelowFirstItem() {
        // firstItem=3 (data at [3,4]), then shrink to length 2
        BuiltinArray a = array("a", "b", "c", "d", "e");
        a.arrayDelete(0, DESC_CHECK.NONE);
        a.arrayDelete(1, DESC_CHECK.NONE);
        a.arrayDelete(2, DESC_CHECK.NONE); // firstItem=3, data=[d,e]
        assertFalse(isSparse(a));

        a.arraySetLength(2, DESC_CHECK.NONE); // new length < firstItem

        // All data (at JS indices 3-4) is beyond the new length=2, so it is removed.
        // The JS length is 2, preserved in lastItem.
        assertFalse(isSparse(a));
        assertEquals(0L, firstItem(a));
        assertEquals(2L, lastItem(a));
        assertEquals(2, a.size()); // size() == arrayLength() == 2
        assertEquals(2L, a.arrayLength());
        assertFalse(a.arrayHas(0));
        assertFalse(a.arrayHas(1));
        assertFalse(a.arrayHas(3));
    }

    public void testSetLengthToZero() {
        BuiltinArray a = array("a", "b", "c");
        a.arraySetLength(0, DESC_CHECK.NONE);

        assertFalse(isSparse(a));
        assertEquals(0L, a.arrayLength());
        assertEquals(0, a.size());
    }

    // -------------------------------------------------------------------------
    // Push with trailing holes — holey for small gap, sparse for large gap
    // -------------------------------------------------------------------------

    public void testPushAfterSetLengthSmallGapStaysHoley() {
        // lastItem=10 with 3 real elements → gap=7 < MAX_HOLEY_GAP → holey after push
        BuiltinArray a = array("a", "b", "c");
        a.arraySetLength(10, DESC_CHECK.NONE); // trailing holes [3..9]
        assertTrue("before push: still no SparseList", !isSparse(a));

        a.arrayAdd("d", DESC_CHECK.NONE); // push fills [3..9] with HOLE, appends "d"

        assertFalse("push with small trailing gap must stay in holey mode", isSparse(a));
        assertTrue(isHoley(a));
        assertEquals(11L, a.arrayLength());
        assertEquals("d", a.arrayGet(10, null));
        assertFalse(a.arrayHas(3)); // hole from the filled gap
        assertFalse(a.arrayHas(9)); // hole from the filled gap
    }

    public void testPushAfterSetLengthLargeGapCreatesSparse() {
        // Gap > MAX_HOLEY_GAP → SparseList
        BuiltinArray a = array("a", "b", "c");
        a.arraySetLength(5000, DESC_CHECK.NONE); // gap=4997 > 1024

        a.arrayAdd("d", DESC_CHECK.NONE);

        assertTrue("push with large trailing gap must trigger SparseList", isSparse(a));
        assertEquals(5001L, a.arrayLength());
        assertEquals("d", a.arrayGet(5000, null));
    }

    public void testPushNormalNoSparse() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayAdd("d", DESC_CHECK.NONE);

        assertFalse(isSparse(a));
        assertEquals(4L, a.arrayLength());
        assertEquals("d", a.arrayGet(3, null));
    }

    // -------------------------------------------------------------------------
    // arraySet with gap — holey for small gap, sparse for large gap
    // -------------------------------------------------------------------------

    public void testSetWithSmallGapStaysHoley() {
        BuiltinArray a = array("a", "b");
        a.arraySet(5, "x", DESC_CHECK.NONE); // gap=3 < MAX_HOLEY_GAP → holey

        assertFalse("small gap must stay in holey mode", isSparse(a));
        assertTrue(isHoley(a));
        assertEquals(6L, a.arrayLength());
        assertEquals("x", a.arrayGet(5, null));
        assertEquals(RuntimeUtil.UNDEFINED, a.arrayGet(2, RuntimeUtil.UNDEFINED));
        assertEquals(RuntimeUtil.UNDEFINED, a.arrayGet(3, RuntimeUtil.UNDEFINED));
        assertFalse(a.arrayHas(2));
        assertFalse(a.arrayHas(3));
        assertFalse(a.arrayHas(4));
    }

    public void testSetBelowFirstItem_SmallGapStaysHoley() {
        // firstItem=2 after two front deletions, then set at 0: gap=1 → holey
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1, data=[b,c]
        a.arrayDelete(1, DESC_CHECK.NONE); // firstItem=2, data=[c]
        assertFalse(isSparse(a));

        a.arraySet(0, "X", DESC_CHECK.NONE); // index=0 < firstItem=2, gap=1 → holey

        assertFalse("small leading gap must stay in holey mode", isSparse(a));
        assertTrue(isHoley(a));
        assertEquals("X", a.arrayGet(0, null));
        assertFalse(a.arrayHas(1)); // hole at 1
        assertEquals("c", a.arrayGet(2, null));
    }

    // -------------------------------------------------------------------------
    // arrayRemove (splice-like — shifts elements)
    // -------------------------------------------------------------------------

    public void testArrayRemove() {
        BuiltinArray a = array("a", "b", "c", "d");
        a.arrayRemove(1, DESC_CHECK.NONE); // remove "b", shift [c,d] down

        assertFalse(isSparse(a));
        assertEquals(3L, a.arrayLength());
        assertEquals("a", a.arrayGet(0, null));
        assertEquals("c", a.arrayGet(1, null));
        assertEquals("d", a.arrayGet(2, null));
    }

    public void testArrayRemoveFirstItem() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1, data=[b,c], length=3
        a.arrayRemove(1, DESC_CHECK.NONE); // splice out JS[1]=b → [c], length=2

        assertFalse(isSparse(a));
        assertEquals(2L, a.arrayLength());
        assertEquals("c", a.arrayGet(1, null));
    }

    public void testArrayRemoveDecrementsLastItem() {
        BuiltinArray a = array("a", "b", "c");
        a.arraySetLength(10, DESC_CHECK.NONE); // lastItem=10
        a.arrayRemove(1, DESC_CHECK.NONE);     // splice shifts → lastItem should decrement

        assertFalse(isSparse(a));
        assertEquals(9L, lastItem(a));
        assertEquals(9L, a.arrayLength());
    }

    // -------------------------------------------------------------------------
    // arrayAdd at index (insert/splice-in)
    // -------------------------------------------------------------------------

    public void testArrayAddAtIndex() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayAdd(1L, "X", DESC_CHECK.NONE); // insert at JS[1]

        assertFalse(isSparse(a));
        assertEquals(4L, a.arrayLength());
        assertEquals("a", a.arrayGet(0, null));
        assertEquals("X", a.arrayGet(1, null));
        assertEquals("b", a.arrayGet(2, null));
        assertEquals("c", a.arrayGet(3, null));
    }

    public void testArrayAddAtIndexWithFirstItem() {
        BuiltinArray a = array("a", "b", "c", "d");
        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1, data=[b,c,d], length=4
        a.arrayAdd(2L, "X", DESC_CHECK.NONE); // insert at JS[2], within dense range

        assertFalse(isSparse(a));
        assertEquals(5L, a.arrayLength());
        assertEquals("b", a.arrayGet(1, null));
        assertEquals("X", a.arrayGet(2, null));
        assertEquals("c", a.arrayGet(3, null));
        assertEquals("d", a.arrayGet(4, null));
    }

    public void testArrayAddAtIndexWithLastItemIncrement() {
        BuiltinArray a = array("a", "b", "c");
        a.arraySetLength(10, DESC_CHECK.NONE); // lastItem=10
        // Insert at JS[1] within dense data range
        a.arrayAdd(1L, "X", DESC_CHECK.NONE);

        assertFalse(isSparse(a));
        assertEquals(11L, lastItem(a)); // trailing holes shift by 1
        assertEquals(11L, a.arrayLength());
    }

    public void testArrayAddAtIndexBelowFirstItemTriggersSparse() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1
        a.arrayAdd(0L, "X", DESC_CHECK.NONE); // index < firstItem → sparse

        assertTrue("splice insert below firstItem must trigger SparseList", isSparse(a));
    }

    // -------------------------------------------------------------------------
    // makeSparse(): correct JS-index mapping when firstItem > 0 and HOLEs present
    // -------------------------------------------------------------------------

    public void testMakeSparsePreservesJsIndices() {
        // Build: [a,b,c,d], delete first → firstItem=1, data=[b,c,d]
        // Then force sparse via a large gap
        BuiltinArray a = array("a", "b", "c", "d");
        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1, JS: 1→b, 2→c, 3→d
        a.makeSparse(); // force conversion via public API

        assertTrue(isSparse(a));
        SparseList<Object> sl = sparse(a);
        assertNotNull(sl);
        assertEquals(4L, sl.size()); // JS length preserved

        assertEquals("b", sl.get(1));
        assertEquals("c", sl.get(2));
        assertEquals("d", sl.get(3));
        assertFalse(sl.has(0)); // was leading hole before sparse
    }

    public void testMakeSparseSkipsHoles() {
        // Middle delete creates HOLE; makeSparse must skip it
        BuiltinArray a = array("a", "b", "c", "d");
        a.arrayDelete(1, DESC_CHECK.NONE); // interior HOLE at JS[1]
        assertFalse(isSparse(a)); // still holey, not sparse

        a.makeSparse(); // explicit conversion

        assertTrue(isSparse(a));
        SparseList<Object> sl = sparse(a);
        assertEquals(4L, sl.size());
        assertEquals("a", sl.get(0));
        assertFalse(sl.has(1)); // hole not transferred
        assertEquals("c", sl.get(2));
        assertEquals("d", sl.get(3));
    }

    public void testMakeSparsePreservesLastItem() {
        BuiltinArray a = array("a", "b", "c", "d");
        a.arraySetLength(10, DESC_CHECK.NONE); // lastItem=10, trailing holes
        a.makeSparse();

        assertTrue(isSparse(a));
        assertEquals(10L, a.arrayLength()); // JS length preserved through sparse conversion
    }

    // -------------------------------------------------------------------------
    // indexOf / lastIndexOf with firstItem offset
    // -------------------------------------------------------------------------

    public void testIndexOfWithOffset() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1, data=[b,c]

        assertEquals(1, a.indexOf("b")); // JS index, not ArrayList index
        assertEquals(2, a.indexOf("c"));
        assertEquals(-1, a.indexOf("a")); // removed
        assertEquals(-1, a.indexOf("z"));
    }

    public void testLastIndexOfWithOffset() {
        BuiltinArray a = array("x", "b", "c", "b");
        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1, data=[b,c,b] → JS[1..3]

        assertEquals(3, a.lastIndexOf("b"));
        assertEquals(2, a.lastIndexOf("c"));
        assertEquals(-1, a.lastIndexOf("x"));
    }

    // -------------------------------------------------------------------------
    // size() and isEmpty()
    // -------------------------------------------------------------------------

    public void testSizeMatchesArrayLength() {
        BuiltinArray a = array("a", "b", "c");
        assertEquals(3, a.size());
        assertFalse(a.isEmpty());

        a.arraySetLength(100, DESC_CHECK.NONE);
        assertEquals(100, a.size());

        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1
        assertEquals(100, a.size()); // still 100

        a.arrayDelete(2, DESC_CHECK.NONE); // delete last data → lastItem set
        // arrayLength = max(100, 1+1) = 100
        assertEquals(100, a.size());
    }

    public void testIsEmpty() {
        BuiltinArray a = new BuiltinArray(getEnvironment());
        assertTrue(a.isEmpty());
        a.arrayAdd("x");
        assertFalse(a.isEmpty());
        a.arraySetLength(0, DESC_CHECK.NONE);
        assertTrue(a.isEmpty());
    }

    // -------------------------------------------------------------------------
    // clear() resets firstItem and lastItem
    // -------------------------------------------------------------------------

    public void testClearResetsFields() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1
        a.arrayDelete(2, DESC_CHECK.NONE); // lastItem=3
        a.clear();

        assertFalse(isSparse(a));
        assertEquals(0L, firstItem(a));
        assertEquals(0L, lastItem(a));
        assertEquals(0L, a.arrayLength());
        assertEquals(0, a.size());
        assertTrue(a.isEmpty());
    }

    // -------------------------------------------------------------------------
    // clone() preserves firstItem / lastItem
    // -------------------------------------------------------------------------

    public void testClonePreservesFields() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1
        a.arraySetLength(10, DESC_CHECK.NONE); // lastItem=10

        BuiltinArray clone = (BuiltinArray) a.clone();

        assertFalse(isSparse(clone));
        assertEquals(firstItem(a), firstItem(clone));
        assertEquals(lastItem(a), lastItem(clone));
        assertEquals(a.arrayLength(), clone.arrayLength());
        assertEquals("b", clone.arrayGet(1, null));
        assertEquals("c", clone.arrayGet(2, null));
    }

    public void testClonePreservesHoles() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(1, DESC_CHECK.NONE); // interior HOLE

        BuiltinArray clone = (BuiltinArray) a.clone();

        assertFalse(isSparse(clone));
        assertTrue(isHoley(clone));
        assertTrue(clone.arrayHas(0));
        assertFalse(clone.arrayHas(1));
        assertTrue(clone.arrayHas(2));
    }

    // -------------------------------------------------------------------------
    // sort() doesn't corrupt firstItem / lastItem
    // -------------------------------------------------------------------------

    public void testSortWithOffset() {
        BuiltinArray a = array("z", "c", "b", "a");
        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1, data=[c,b,a]

        a.arraySort((x, y) -> x.toString().compareTo(y.toString()), DESC_CHECK.NONE);

        assertEquals(4L, a.arrayLength()); // JS length unchanged

        // Per spec (23.1.3.30), sort snapshots only the PRESENT elements,
        // sorts them, writes them back starting at index 0, and relocates
        // any hole to the end - the original hole at index 0 does not stay
        // there (this replaces a pre-existing assertion that encoded the
        // old, spec-incorrect in-place-sort-around-the-hole behavior).
        assertEquals("a", a.arrayGet(0, null));
        assertEquals("b", a.arrayGet(1, null));
        assertEquals("c", a.arrayGet(2, null));
        assertNull(a.arrayGet(3, null));
    }

    // -------------------------------------------------------------------------
    // forEach must not expose HOLE sentinel to callers
    // -------------------------------------------------------------------------

    public void testForEachHoleyArrayReturnsUndefinedForHoles() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(1, DESC_CHECK.NONE); // interior HOLE at JS[1]

        // forEach over the ArrayList contents: HOLE must appear as UNDEFINED
        java.util.List<Object> seen = new java.util.ArrayList<>();
        a.forEach(seen::add);

        // The ArrayList has 2 real elements [a, c]; HOLE was compacted (delete
        // last element compacts trailing HOLEs, but here 'b' is middle and 'c' follows).
        // ArrayList = [a, HOLE, c] → forEach should give [a, UNDEFINED, c].
        assertEquals(3, seen.size());
        assertEquals("a", seen.get(0));
        assertEquals(RuntimeUtil.UNDEFINED, seen.get(1));
        assertEquals("c", seen.get(2));
    }

    public void testGetHoleyArrayReturnsUndefined() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(1, DESC_CHECK.NONE); // HOLE at JS[1]

        // List.get(int) must return UNDEFINED, not the HOLE sentinel
        assertEquals("a", a.get(0));
        assertEquals(RuntimeUtil.UNDEFINED, a.get(1));
        assertEquals("c", a.get(2));
    }

    // -------------------------------------------------------------------------
    // createSparse constructor — bypasses dense path
    // -------------------------------------------------------------------------

    public void testCreateSparseConstructor() {
        SparseList<Object> sl = new SparseList<>();
        sl.put(5L, "hello");
        sl.put(10L, "world");

        BuiltinArray a = new BuiltinArray(getEnvironment(),sl);

        assertTrue(isSparse(a));
        assertEquals(11L, a.arrayLength());
        assertEquals("hello", a.arrayGet(5, null));
        assertEquals("world", a.arrayGet(10, null));
        assertFalse(a.arrayHas(0));
    }

    // -------------------------------------------------------------------------
    // Edge case: delete out-of-range index is a no-op
    // -------------------------------------------------------------------------

    public void testDeleteOutOfRangeIsNoOp() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(5, DESC_CHECK.NONE); // beyond length

        assertFalse(isSparse(a));
        assertEquals(0L, firstItem(a));
        assertEquals(0L, lastItem(a));
        assertEquals(3L, a.arrayLength());
    }

    public void testDeleteHoleRangeIsNoOp() {
        BuiltinArray a = array("a", "b", "c");
        a.arraySetLength(10, DESC_CHECK.NONE); // trailing holes [3..9]
        a.arrayDelete(7, DESC_CHECK.NONE);     // deleting a trailing hole

        assertFalse("deleting a trailing hole must not create SparseList", isSparse(a));
        assertEquals(10L, a.arrayLength());
    }

    public void testDeleteLeadingHoleIsNoOp() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(0, DESC_CHECK.NONE); // firstItem=1
        a.arrayDelete(0, DESC_CHECK.NONE); // JS[0] is now a leading hole — no-op

        assertFalse(isSparse(a));
        assertEquals(1L, firstItem(a)); // unchanged
        assertEquals(3L, a.arrayLength());
    }

    // -------------------------------------------------------------------------
    // toString() — must report holes the same way SparseList does
    // -------------------------------------------------------------------------

    public void testToStringDense() {
        assertEquals("[ \"a\", \"b\", \"c\" ]", array("a", "b", "c").toString());
    }

    public void testToStringEmpty() {
        assertEquals("[]", new BuiltinArray(getEnvironment()).toString());
    }

    public void testToStringLeadingHoles() {
        // firstItem=3 → 3 leading holes then the value
        BuiltinArray a = new BuiltinArray(getEnvironment());
        a.arraySet(3, "x", DESC_CHECK.NONE);
        assertEquals("[ <3 empty items>, \"x\" ]", a.toString());
    }

    public void testToStringSingleLeadingHole() {
        BuiltinArray a = new BuiltinArray(getEnvironment());
        a.arraySet(1, "x", DESC_CHECK.NONE);
        assertEquals("[ <1 empty item>, \"x\" ]", a.toString());
    }

    public void testToStringTrailingHoles() {
        BuiltinArray a = array("a", "b");
        a.arraySetLength(5, DESC_CHECK.NONE); // trailing holes [2..4]
        assertEquals("[ \"a\", \"b\", <3 empty items> ]", a.toString());
    }

    public void testToStringInteriorHole() {
        BuiltinArray a = array("a", "b", "c");
        a.arrayDelete(1, DESC_CHECK.NONE); // HOLE at JS[1]
        assertEquals("[ \"a\", <1 empty item>, \"c\" ]", a.toString());
    }

    public void testToStringInteriorHoles() {
        BuiltinArray a = array("a", "b", "c", "d", "e");
        a.arrayDelete(1, DESC_CHECK.NONE);
        a.arrayDelete(2, DESC_CHECK.NONE); // HOLEs at JS[1] and JS[2]
        assertEquals("[ \"a\", <2 empty items>, \"d\", \"e\" ]", a.toString());
    }

    public void testToStringMixed() {
        // leading hole + value + interior hole + value + trailing hole
        BuiltinArray a = new BuiltinArray(getEnvironment());
        a.arraySet(1, "x", DESC_CHECK.NONE); // firstItem=1, data=["x"]
        a.arraySet(2, "y", DESC_CHECK.NONE); // adjacent → ["x","y"]
        a.arraySet(5, "z", DESC_CHECK.NONE); // trailing gap → ["x","y",HOLE,HOLE,"z"]
        a.arraySetLength(8, DESC_CHECK.NONE); // lastItem=8
        // JS: hole at 0, "x" at 1, "y" at 2, holes at 3-4, "z" at 5, holes at 6-7
        assertEquals("[ <1 empty item>, \"x\", \"y\", <2 empty items>, \"z\", <2 empty items> ]", a.toString());
    }

    public void testToStringSparse() {
        // Sparse arrays delegate to SparseList.toString() — same format
        BuiltinArray a = array("a", "b");
        a.arraySet(2000, "z", DESC_CHECK.NONE); // large gap → SparseList
        assertTrue(isSparse(a));
        String s = a.toString();
        assertTrue("sparse toString must contain 'a'", s.contains("\"a\""));
        assertTrue("sparse toString must contain empty items", s.contains("empty item"));
        assertTrue("sparse toString must contain 'z'", s.contains("\"z\""));
    }
}
