package com.example.cardgame.ga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GeneticAlgorithm {
    private int populationSize;
    private double mutationRate;
    private double crossoverRate;
    private int elitismCount;
    private static final Random random = new Random();

    public GeneticAlgorithm(int populationSize, double mutationRate, double crossoverRate, int elitismCount) {
        this.populationSize = populationSize;
        this.mutationRate = mutationRate;
        this.crossoverRate = crossoverRate;
        this.elitismCount = elitismCount;
    }

    public List<Chromosome> initializePopulation() {
        List<Chromosome> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            population.add(Chromosome.randomChromosome());
        }
        return population;
    }

    public double evaluate(Chromosome chrom) {
        return FitnessEvaluator.evaluate(chrom);
    }

    public List<Chromosome> selectParents(List<Chromosome> population, double[] fitnesses) {
        List<Chromosome> parents = new ArrayList<>();

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < population.size(); i++) {
            indices.add(i);
        }
        indices.sort((a, b) -> Double.compare(fitnesses[b], fitnesses[a]));

        for (int i = 0; i < elitismCount && i < indices.size(); i++) {
            parents.add(population.get(indices.get(i)));
        }

        while (parents.size() < populationSize) {
            Chromosome parent = tournamentSelection(population, fitnesses);
            parents.add(parent);
        }

        return parents;
    }

    private Chromosome tournamentSelection(List<Chromosome> population, double[] fitnesses) {
        int tournamentSize = 3;
        int bestIndex = -1;
        double bestFitness = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < tournamentSize; i++) {
            int randomIndex = random.nextInt(population.size());
            if (fitnesses[randomIndex] > bestFitness) {
                bestFitness = fitnesses[randomIndex];
                bestIndex = randomIndex;
            }
        }
        return population.get(bestIndex);
    }

    public List<Chromosome> reproduce(List<Chromosome> parents) {
        List<Chromosome> newPopulation = new ArrayList<>();

        for (int i = 0; i < elitismCount && i < parents.size(); i++) {
            newPopulation.add(Chromosome.fromArray(parents.get(i).toArray()));
        }

        while (newPopulation.size() < populationSize) {
            int parent1Index = random.nextInt(parents.size());
            int parent2Index = random.nextInt(parents.size());

            while (parent2Index == parent1Index) {
                parent2Index = random.nextInt(parents.size());
            }

            Chromosome parent1 = parents.get(parent1Index);
            Chromosome parent2 = parents.get(parent2Index);

            Chromosome child;
            if (random.nextDouble() < crossoverRate) {
                child = Chromosome.crossover(parent1, parent2);
            } else {
                child = Chromosome.fromArray(parent1.toArray());
            }

            child.mutate(mutationRate);
            newPopulation.add(child);
        }

        return newPopulation;
    }

    public Chromosome getBestIndividual(List<Chromosome> population, double[] fitnesses) {
        int bestIndex = -1;
        double bestFitness = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < fitnesses.length; i++) {
            if (fitnesses[i] > bestFitness) {
                bestFitness = fitnesses[i];
                bestIndex = i;
            }
        }
        return population.get(bestIndex);
    }

    public Chromosome run(int generations) {
        List<Chromosome> population = initializePopulation();

        for (int gen = 0; gen < generations; gen++) {
            System.out.printf("Evaluating generation %d...%n", gen + 1);
            
            double[] fitnesses = new double[population.size()];
            for (int i = 0; i < population.size(); i++) {
                fitnesses[i] = evaluate(population.get(i));
            }

            int bestIdx = 0;
            double bestFitness = fitnesses[0];
            for (int i = 1; i < fitnesses.length; i++) {
                if (fitnesses[i] > bestFitness) {
                    bestFitness = fitnesses[i];
                    bestIdx = i;
                }
            }
            System.out.printf("Generation %d: Best Fitness = %.2f%n", gen + 1, bestFitness);

            List<Chromosome> parents = selectParents(population, fitnesses);
            population = reproduce(parents);
        }

        double[] fitnesses = new double[population.size()];
        for (int i = 0; i < population.size(); i++) {
            fitnesses[i] = evaluate(population.get(i));
        }
        return getBestIndividual(population, fitnesses);
    }
}
