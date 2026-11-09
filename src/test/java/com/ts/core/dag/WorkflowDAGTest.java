package com.ts.core.dag;

import com.ts.exception.CycleDetectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WorkflowDAG}: node/edge management, predecessor lookups,
 * cycle detection/validation, and serialisation.
 */
class WorkflowDAGTest {

    private WorkflowDAG dag;

    @BeforeEach
    void setUp() {
        dag = new WorkflowDAG();
    }

    // ==================== addTask / counts / membership ====================

    @Test
    void addTask_shouldRegisterNodeAndName() {
        dag.addTask("t1", "Task One");

        assertTrue(dag.containsTask("t1"));
        assertFalse(dag.containsTask("t2"));
        assertEquals(1, dag.getTaskCount());
        assertEquals(0, dag.getEdgeCount());
        assertEquals(Set.of("t1"), dag.getAllTasks());
    }

    @Test
    void addEdge_shouldRegisterDirectedEdge() {
        dag.addTask("t1", "A");
        dag.addTask("t2", "B");
        dag.addEdge("t1", "t2");

        assertEquals(1, dag.getEdgeCount());
        assertEquals(List.of("t1"), dag.getDirectPredecessors("t2"));
        assertTrue(dag.getDirectPredecessors("t1").isEmpty());
    }

    @Test
    void addEdge_shouldAllowMultiplePredecessors() {
        dag.addTask("t1", "A");
        dag.addTask("t2", "B");
        dag.addTask("t3", "C");
        dag.addEdge("t1", "t3");
        dag.addEdge("t2", "t3");

        assertEquals(List.of("t1", "t2"), dag.getDirectPredecessors("t3"));
    }

    @Test
    void addEdge_unknownSource_shouldThrow() {
        dag.addTask("t2", "B");
        assertThrows(IllegalArgumentException.class, () -> dag.addEdge("t1", "t2"));
    }

    @Test
    void addEdge_unknownTarget_shouldThrow() {
        dag.addTask("t1", "A");
        assertThrows(IllegalArgumentException.class, () -> dag.addEdge("t1", "t2"));
    }

    @Test
    void getDirectPredecessors_unknownTask_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> dag.getDirectPredecessors("missing"));
    }

    // ==================== cycle detection ====================

    @Test
    void hasCycle_acyclicGraph_shouldBeFalse() {
        dag.addTask("t1", "A");
        dag.addTask("t2", "B");
        dag.addTask("t3", "C");
        dag.addEdge("t1", "t2");
        dag.addEdge("t2", "t3");

        assertFalse(dag.hasCycle());
    }

    @Test
    void hasCycle_simpleCycle_shouldBeTrue() {
        dag.addTask("t1", "A");
        dag.addTask("t2", "B");
        dag.addEdge("t1", "t2");
        dag.addEdge("t2", "t1");

        assertTrue(dag.hasCycle());
    }

    @Test
    void hasCycle_selfLoop_shouldBeTrue() {
        dag.addTask("t1", "A");
        dag.addEdge("t1", "t1");

        assertTrue(dag.hasCycle());
    }

    @Test
    void hasCycle_longerCycle_shouldBeTrue() {
        dag.addTask("t1", "A");
        dag.addTask("t2", "B");
        dag.addTask("t3", "C");
        dag.addEdge("t1", "t2");
        dag.addEdge("t2", "t3");
        dag.addEdge("t3", "t1");

        assertTrue(dag.hasCycle());
    }

    // ==================== validate ====================

    @Test
    void validate_acyclic_shouldNotThrow() {
        dag.addTask("t1", "A");
        dag.addTask("t2", "B");
        dag.addEdge("t1", "t2");

        assertDoesNotThrow(dag::validate);
    }

    @Test
    void validate_cycle_shouldThrow() {
        dag.addTask("t1", "A");
        dag.addTask("t2", "B");
        dag.addEdge("t1", "t2");
        dag.addEdge("t2", "t1");

        assertThrows(CycleDetectedException.class, dag::validate);
    }

    // ==================== serialisation ====================

    @Test
    void toJson_shouldProduceNodesAndEdges() {
        dag.addTask("t1", "A");
        dag.addTask("t2", "B");
        dag.addEdge("t1", "t2");

        Map<String, Object> json = dag.toJson();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) json.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) json.get("edges");

        assertEquals(2, nodes.size());
        assertEquals(1, edges.size());
        assertEquals("t1", edges.get(0).get("source"));
        assertEquals("t2", edges.get(0).get("target"));
    }

    @Test
    void toJson_emptyDag_shouldProduceEmptyArrays() {
        Map<String, Object> json = dag.toJson();
        assertEquals(0, ((List<?>) json.get("nodes")).size());
        assertEquals(0, ((List<?>) json.get("edges")).size());
    }
}
