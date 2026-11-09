/*
 * Description: Thrown when a cyclic dependency is found in a workflow DAG
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.exception;
/**
 * Thrown when DAG validation discovers a cyclic dependency in a workflow definition.
 */
public class CycleDetectedException extends WorkflowException {
    /**
     * Constructs a new CycleDetectedException instance.
     *
     * @param message  descriptive message
     */
    public CycleDetectedException(String message) {
        super(message);
    }
}