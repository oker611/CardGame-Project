package com.example.cardgame.ga;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class SelfPlayGeneticAlgorithm {
    private int populationSize;
    private double mutationRate;
    private double crossoverRate;
    private int elitismCount;
    private static final Random random = new Random();

    public SelfPlayGeneticAlgorithm(int populationSize, double mutationRate,
                                    double crossoverRate, int elitismCount) {
        this.populationSize = populationSize;
        this.mutationRate = mutationRate;
        this.crossoverRate = crossoverRate;
        this.elitismCount = elitismCount;
    }

    public Chromosome run(int generations) {
        List<Chromosome> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            population.add(Chromosome.randomChromosome());
        }

        Chromosome bestEver = population.get(0).clone();
        double bestEverFitness = Double.NEGATIVE_INFINITY;

        for (int gen = 0; gen < generations; gen++) {
            System.out.printf("%n=== Generation %d/%d ===%n", gen + 1, generations);

            SelfPlayFitnessEvaluator.FitnessResult[] results =
                SelfPlayFitnessEvaluator.evaluatePopulation(population);

            for (SelfPlayFitnessEvaluator.FitnessResult r : results) {
                System.out.printf("  Fitness=%.4f, Rank=%d/%d, AvgScore=%.2f, Aggression=%.2f, Bomb=%.2f, Two=%.2f%n",
                    r.getFitness(), r.rank, r.totalPlayers, r.avgScore, r.avgAggression, r.avgBomb, r.avgTwo);
            }

            List<Chromosome> newPopulation = new ArrayList<>();

            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < results.length; i++) indices.add(i);
            indices.sort((a, b) -> Double.compare(results[b].getFitness(), results[a].getFitness()));

            for (int i = 0; i < elitismCount && i < indices.size(); i++) {
                Chromosome elite = results[indices.get(i)].chromosome.clone();
                newPopulation.add(elite);
            }

            if (results[indices.get(0)].getFitness() > bestEverFitness) {
                bestEver = results[indices.get(0)].chromosome.clone();
                bestEverFitness = results[indices.get(0)].getFitness();
            }

            while (newPopulation.size() < populationSize) {
                Chromosome p1 = tournamentSelect(population, results);
                Chromosome p2 = tournamentSelect(population, results);
                Chromosome child;
                if (random.nextDouble() < crossoverRate) {
                    child = Chromosome.crossover(p1, p2);
                } else {
                    child = random.nextBoolean() ? p1.clone() : p2.clone();
                }
                child.mutate(mutationRate);
                newPopulation.add(child);
            }

            population = newPopulation;
        }

        return bestEver;
    }

    private Chromosome tournamentSelect(List<Chromosome> population,
                                       SelfPlayFitnessEvaluator.FitnessResult[] results) {
        int tournamentSize = 3;
        int bestIdx = -1;
        double bestFitness = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < tournamentSize; i++) {
            int idx = random.nextInt(population.size());
            double fit = results[idx].getFitness();
            if (fit > bestFitness) {
                bestFitness = fit;
                bestIdx = idx;
            }
        }
        return population.get(bestIdx);
    }
}
