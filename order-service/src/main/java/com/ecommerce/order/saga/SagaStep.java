package com.ecommerce.order.saga;

import com.ecommerce.order.entity.ReturnSaga;

/**
 * Strategy interface for individual saga steps.
 * Each step knows how to execute its action and how to compensate (undo) it.
 *
 * Design Pattern: Strategy — each step encapsulates a different algorithm
 * for interacting with a participant service.
 */
public interface SagaStep {

    /**
     * Execute the forward action for this saga step.
     */
    void execute(ReturnSaga saga);

    /**
     * Compensate (undo) the action if a later step fails.
     */
    void compensate(ReturnSaga saga);
}
