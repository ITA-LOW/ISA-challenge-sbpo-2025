package org.sbpo2025.challenge;

import org.apache.commons.lang3.time.StopWatch;

import java.util.*;
import java.util.concurrent.*;

public class ChallengeSolver {
    private final long MAX_RUNTIME = 600000; // milliseconds; 10 minutes

    protected List<Map<Integer, Integer>> orders;
    protected List<Map<Integer, Integer>> aisles;
    protected int nItems;
    protected int waveSizeLB;
    protected int waveSizeUB;

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

public ChallengeSolver(List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) {
    this.orders = orders;
    this.aisles = aisles;
    this.nItems = nItems;
    this.waveSizeLB = waveSizeLB;
    this.waveSizeUB = waveSizeUB;
}


public ChallengeSolution solve(StopWatch stopWatch) {
    // Parâmetros do algoritmo genético
    final int populationSize = 100;
    final int generations = 100;
    final double crossoverRate = 0.8;
    final double mutationRate = 0.05;
    final int nOrders = orders.size();
    
    // Cria a população inicial (cada indivíduo é um vetor booleano que indica se o pedido está selecionado)
    List<Individual> population = new ArrayList<>();
    for (int i = 0; i < populationSize; i++) {
        Individual ind = new Individual(nOrders);
        ind.randomize();
        population.add(ind);
    }
    
    // Avalia a população em paralelo (usando 8 threads, se desejado; aqui a versão sem paralelismo é utilizada)
    evaluatePopulation(population);
    
    // Evolução por um número de gerações
    for (int gen = 0; gen < generations; gen++) {
        List<Individual> newPopulation = new ArrayList<>();
        // Elitismo: preserva o melhor indivíduo
        Individual best = selectBestIndividual(population);
        newPopulation.add(best.copy());
        
        // Gera nova população via seleção, crossover e mutação
        while (newPopulation.size() < populationSize) {
            Individual parent1 = tournamentSelection(population);
            Individual parent2 = tournamentSelection(population);
            Individual child;
            if (Math.random() < crossoverRate) {
                child = crossover(parent1, parent2);
            } else {
                child = parent1.copy();
            }
            mutate(child, mutationRate);
            repair(child); // Tenta ajustar o indivíduo para melhorar a viabilidade
            newPopulation.add(child);
        }
        population = newPopulation;
        evaluatePopulation(population);
    }
    
    // Seleciona o melhor indivíduo viável da população.
    // Se nenhum indivíduo viável (fitness >= 0) for encontrado, usa o melhor indivíduo disponível e aplica repair.
    Individual bestFeasible = selectBestFeasible(population);
    if (bestFeasible == null) {
        bestFeasible = selectBestIndividual(population);
        repair(bestFeasible);
        System.out.println("Warning: Nenhum indivíduo viável encontrado. Usando o melhor indivíduo disponível.");
    }
    
    // Converte o melhor indivíduo em uma ChallengeSolution:
    Set<Integer> solOrders = new HashSet<>();
    for (int i = 0; i < nOrders; i++) {
        if (bestFeasible.genotype[i]) {
            solOrders.add(i);
        }
    }
    // Deriva os corredores visitados: para cada pedido selecionado, inclui o primeiro corredor que forneça cada item
    Set<Integer> solAisles = new HashSet<>();
    for (int orderIdx : solOrders) {
        for (Integer item : orders.get(orderIdx).keySet()) {
            int quantity = orders.get(orderIdx).get(item);
            for (int a = 0; a < aisles.size(); a++) {
                if (aisles.get(a).getOrDefault(item, 0) >= quantity) {
                    solAisles.add(a);
                    break;
                }
            }
        }
    }
    
    ChallengeSolution solution = new ChallengeSolution(solOrders, solAisles);
    if (!isSolutionFeasible(solution)) {
        System.out.println("Warning: A solução gerada não atende completamente os critérios de viabilidade. Retornando solução mesmo assim.");
    }
    return solution;
}

/* 
 * Classe interna para representar um indivíduo da população.
 * Cada indivíduo possui um vetor booleano "genotype" de tamanho nOrders
 * e um valor de fitness (maior é melhor).
 */
private class Individual {
    boolean[] genotype;
    double fitness;
    
    Individual(int n) {
        genotype = new boolean[n];
        fitness = -Double.MAX_VALUE;
    }
    
    // Inicializa aleatoriamente o vetor com 0s e 1s
    void randomize() {
        Random rand = new Random();
        for (int i = 0; i < genotype.length; i++) {
            genotype[i] = rand.nextBoolean();
        }
    }
    
    // Cria uma cópia do indivíduo
    Individual copy() {
        Individual clone = new Individual(genotype.length);
        System.arraycopy(this.genotype, 0, clone.genotype, 0, genotype.length);
        clone.fitness = this.fitness;
        return clone;
    }
}

// Avalia a população (versão sem paralelismo)
private void evaluatePopulation(List<Individual> population) {
    for (Individual ind : population) {
        ind.fitness = evaluateIndividual(ind);
    }
}

/*
 * Função que avalia um indivíduo.
 * Converte o genotype em conjuntos de pedidos e derivam os corredores.
 * Se a solução for inviável (por exemplo, total de unidades fora dos limites ou falta de estoque),
 * retorna um valor de fitness negativo; caso contrário, retorna a razão total de itens / número de corredores.
 */
private double evaluateIndividual(Individual ind) {
    Set<Integer> selOrders = new HashSet<>();
    for (int i = 0; i < ind.genotype.length; i++) {
        if (ind.genotype[i]) {
            selOrders.add(i);
        }
    }
    int totalUnitsPicked = 0;
    for (int order : selOrders) {
        totalUnitsPicked += orders.get(order).values().stream().mapToInt(Integer::intValue).sum();
    }
    // Deriva os corredores visitados (primeiro corredor que supre cada item de cada pedido)
    Set<Integer> selAisles = new HashSet<>();
    for (int order : selOrders) {
        for (Map.Entry<Integer, Integer> entry : orders.get(order).entrySet()) {
            int item = entry.getKey();
            int qty = entry.getValue();
            for (int a = 0; a < aisles.size(); a++) {
                if (aisles.get(a).getOrDefault(item, 0) >= qty) {
                    selAisles.add(a);
                    break;
                }
            }
        }
    }
    // Verifica as restrições:
    if (totalUnitsPicked < waveSizeLB || totalUnitsPicked > waveSizeUB) {
        return -1e6; // Penaliza fortemente soluções com total fora dos limites
    }
    // Verifica disponibilidade: para cada item, a soma das demandas deve ser <= soma dos estoques nos corredores selecionados.
    for (int order : selOrders) {
        for (Map.Entry<Integer, Integer> entry : orders.get(order).entrySet()) {
            int item = entry.getKey();
            int demand = entry.getValue();
            int available = 0;
            for (int a : selAisles) {
                available += aisles.get(a).getOrDefault(item, 0);
            }
            if (demand > available) {
                return -1e6;
            }
        }
    }
    // Se viável, retorna a razão (fitness) = totalUnitsPicked / (número de corredores usados)
    if (selAisles.isEmpty()) return -1e6;
    return (double) totalUnitsPicked / selAisles.size();
}

// Seleção por torneio: seleciona um indivíduo dentre um grupo aleatório de 3
private Individual tournamentSelection(List<Individual> population) {
    Random rand = new Random();
    Individual best = null;
    for (int i = 0; i < 3; i++) {
        Individual ind = population.get(rand.nextInt(population.size()));
        if (best == null || ind.fitness > best.fitness) {
            best = ind;
        }
    }
    return best;
}

// Crossover de um ponto (ou uniforme) entre dois pais
private Individual crossover(Individual parent1, Individual parent2) {
    int n = parent1.genotype.length;
    Individual child = new Individual(n);
    Random rand = new Random();
    for (int i = 0; i < n; i++) {
        // Crossover uniforme: cada gene é escolhido aleatoriamente dos pais
        child.genotype[i] = rand.nextBoolean() ? parent1.genotype[i] : parent2.genotype[i];
    }
    return child;
}

// Mutação: inverte cada gene com probabilidade igual a mutationRate
private void mutate(Individual ind, double mutationRate) {
    Random rand = new Random();
    for (int i = 0; i < ind.genotype.length; i++) {
        if (rand.nextDouble() < mutationRate) {
            ind.genotype[i] = !ind.genotype[i];
        }
    }
}

// Método para "reparar" um indivíduo, tentando ajustar a solução para que ela seja viável.
// Por exemplo, se o total de itens for menor que waveSizeLB, ativa alguns pedidos aleatórios.
private void repair(Individual ind) {
    int total = 0;
    for (int i = 0; i < ind.genotype.length; i++) {
        if (ind.genotype[i]) {
            total += getTotalItems(orders.get(i));
        }
    }
    Random rand = new Random();
    while (total < waveSizeLB) {
        int idx = rand.nextInt(ind.genotype.length);
        if (!ind.genotype[idx]) {
            ind.genotype[idx] = true;
            total += getTotalItems(orders.get(idx));
        }
    }
}

// Seleciona o melhor indivíduo (com maior fitness) da população
private Individual selectBestIndividual(List<Individual> population) {
    return population.stream().max(Comparator.comparingDouble(ind -> ind.fitness)).orElse(null);
}

// Seleciona o melhor indivíduo viável (fitness não negativo) da população
private Individual selectBestFeasible(List<Individual> population) {
    return population.stream().filter(ind -> ind.fitness >= 0).max(Comparator.comparingDouble(ind -> ind.fitness)).orElse(null);
}

// Método auxiliar para calcular o total de itens de um pedido
private int getTotalItems(Map<Integer, Integer> order) {
    return order.values().stream().mapToInt(Integer::intValue).sum();
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////



    /*
     * Get the remaining time in seconds
     */
    protected long getRemainingTime(StopWatch stopWatch) {
        return Math.max(
                TimeUnit.SECONDS.convert(MAX_RUNTIME - stopWatch.getTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS),
                0);
    }

    protected boolean isSolutionFeasible(ChallengeSolution challengeSolution) {
        Set<Integer> selectedOrders = challengeSolution.orders();
        Set<Integer> visitedAisles = challengeSolution.aisles();
        if (selectedOrders == null || visitedAisles == null || selectedOrders.isEmpty() || visitedAisles.isEmpty()) {
            return false;
        }

        int[] totalUnitsPicked = new int[nItems];
        int[] totalUnitsAvailable = new int[nItems];

        // Calculate total units picked
        for (int order : selectedOrders) {
            for (Map.Entry<Integer, Integer> entry : orders.get(order).entrySet()) {
                totalUnitsPicked[entry.getKey()] += entry.getValue();
            }
        }

        // Calculate total units available
        for (int aisle : visitedAisles) {
            for (Map.Entry<Integer, Integer> entry : aisles.get(aisle).entrySet()) {
                totalUnitsAvailable[entry.getKey()] += entry.getValue();
            }
        }

        // Check if the total units picked are within bounds
        int totalUnits = Arrays.stream(totalUnitsPicked).sum();
        if (totalUnits < waveSizeLB || totalUnits > waveSizeUB) {
            return false;
        }

        // Check if the units picked do not exceed the units available
        for (int i = 0; i < nItems; i++) {
            if (totalUnitsPicked[i] > totalUnitsAvailable[i]) {
                return false;
            }
        }

        return true;
    }

    protected double computeObjectiveFunction(ChallengeSolution challengeSolution) {
        Set<Integer> selectedOrders = challengeSolution.orders();
        Set<Integer> visitedAisles = challengeSolution.aisles();
        if (selectedOrders == null || visitedAisles == null || selectedOrders.isEmpty() || visitedAisles.isEmpty()) {
            return 0.0;
        }
        int totalUnitsPicked = 0;

        // Calculate total units picked
        for (int order : selectedOrders) {
            totalUnitsPicked += orders.get(order).values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        }

        // Calculate the number of visited aisles
        int numVisitedAisles = visitedAisles.size();

        // Objective function: total units picked / number of visited aisles
        return (double) totalUnitsPicked / numVisitedAisles;
    }
}

 /*
    * Avalia a população em paralelo usando um ExecutorService com 8 threads.
    * A função de avaliação computa a fitness de um indivíduo:
    * Se a solução for viável, fitness = total_units / num_aisles;
    * caso contrário, atribui um valor de fitness muito baixo (penaliza soluções inviáveis).
    */
    /* private void evaluatePopulation(List<Individual> population) {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        
        for (Individual ind : population) {
            futures.add(executor.submit(() -> {
                ind.fitness = evaluateIndividual(ind);
            }));
        }
        // Aguarda a conclusão de todas as tarefas
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        executor.shutdown();
    } */