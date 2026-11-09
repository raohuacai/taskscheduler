package com.ts.core.dag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WorkflowDAGBuilder}: fluent construction and parameter guards.
 */
class WorkflowDAGBuilderTest {

    @Test
    void build_emptyBuilder_shouldProduceEmptyDag() {
        WorkflowDAG dag = new WorkflowDAGBuilder().build();
        assertEquals(0, dag.getTaskCount());
        assertEquals(0, dag.getEdgeCount());
    }

    @Test
    void addTaskAndEdge_shouldBuildGraph() {
        WorkflowDAG dag = new WorkflowDAGBuilder()
                .addTask("t1", "A")
                .addTask("t2", "B")
                .addEdge("t1", "t2")
                .build();

        assertEquals(2, dag.getTaskCount());
        assertEquals(1, dag.getEdgeCount());
        assertTrue(dag.containsTask("t1"));
        assertFalse(dag.hasCycle());
    }

    @Test
    void addTask_nullId_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDAGBuilder().addTask(null, "A"));
    }

    @Test
    void addTask_nullName_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDAGBuilder().addTask("t1", null));
    }

    @Test
    void addEdge_nullSource_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkflowDAGBuilder().addEdge(null, "t2"));
    }

    @Test
    void addEdge_nullTarget_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkflowDAGBuilder().addEdge("t1", null));
    }

    @Test
    void addEdge_unknownTask_shouldThrow() {
        WorkflowDAGBuilder builder = new WorkflowDAGBuilder().addTask("t1", "A");
        // t2 was never added
        assertThrows(IllegalArgumentException.class, () -> builder.addEdge("t1", "t2"));
    }
}
