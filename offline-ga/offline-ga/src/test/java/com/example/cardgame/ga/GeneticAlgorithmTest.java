
package com.example.cardgame.ga;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GeneticAlgorithmTest {

    @Test
    public void testAIParameters() {
        AIParameters params = new AIParameters();
        assertNotNull(params);
        assertEquals(0.5, params.getAggression());
    }

    @Test
    public void testIndividualCreation() {
        Individual individual = new Individual();
        assertNotNull(individual);
        assertNotNull(individual.getParameters());
    }

    @Test
    public void testRandomIndividual() {
        Individual individual = Individual.randomIndividual();
        assertNotNull(individual);
        assertNotNull(individual.getParameters());
    }

    @Test
    public void testFitnessEvaluator() {
        FitnessEvaluator evaluator = new FitnessEvaluator(10);
        Individual individual = Individual.randomIndividual();
        double fitness = evaluator.evaluate(individual);
        assertTrue(fitness &gt;= 0);
    }
}

