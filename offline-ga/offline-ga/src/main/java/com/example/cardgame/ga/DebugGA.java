package com.example.cardgame.ga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DebugGA {
    private int populationSize;
    private double mutationRate;
    private double crossoverRate;
    private int elitismCount;
    private static final Random random = new Random();

    public DebugGA(int populationSize, double mutationRate, double crossoverRate, int elitismCount) {
        this.populationSize = populationSize;
        this.mutationRate = mutationRate;
        this.crossoverRate = crossoverRate;
        this.elitismCount = elitismCount;
    }

    public Chromosome run(int generations) {
        System.out.println("Initializing population...");
        List<Chromosome> population = initializePopulation();
        System.out.println("Population initialized with " + population.size() + " individuals");

        for (int gen = 0; gen < generations; gen++) {
            System.out.println("\n=== Generation " + (gen + 1) + " ===");
            
            System.out.println("Evaluating " + population.size() + " individuals...");
            double[] fitnesses = new double[population.size()];
            for (int i = 0; i < population.size(); i++) {
                System.out.println("  Evaluating individual " + (i + 1) + "...");
                long start = System.currentTimeMillis();
                fitnesses[i] = FitnessEvaluator.evaluate(population.get(i));
                long end = System.currentTimeMillis();
                System.out.println("  Individual " + (i + 1) + " fitness: " + fitnesses[i] + " (" + (end - start) + " ms)");
            }

            int bestIdx = 0;
            double bestFitness = fitnesses[0];
            for (int i = 1; i < fitnesses.length; i++) {
                if (fitnesses[i] > bestFitness) {
                    bestFitness = fitnesses[i];
                    bestIdx = i;
                }
            }
            System.out.println("Generation " + (gen + 1) + ": Best Fitness = " + bestFitness);

            System.out.println("Selecting parents...");
            List<Chromosome> parents = selectParents(population, fitnesses);
            System.out.println("Selected " + parents.size() + " parents");

            System.out.println("Reproducing...");
            population = reproduce(parents);
            System.out.println("New population size: " + population.size());
        }

        System.out.println("\n=== Final evaluation ===");
        double[] fitnesses = new double[population.size()];
        for (int i = 0; i < population.size(); i++) {
            fitnesses[i] = FitnessEvaluator.evaluate(population.get(i));
        }
        return getBestIndividual(population, fitnesses);
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

    public static void main(String[] args) {
        System.out.println("=== Debug Genetic Algorithm ===");
        
        try {
            DebugGA ga = new DebugGA(2, 0.1, 0.8, 1);
            
            Chromosome best = ga.run(2);
            
            System.out.println("\n=== Optimization completed ===");
            System.out.println("Best chromosome:");
            System.out.println("  numSamples: " + best.numSamples);
            System.out.println("  topKCandidates: " + best.topKCandidates);
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
