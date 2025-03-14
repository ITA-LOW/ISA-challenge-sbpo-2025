package org.sbpo2025.challenge;

import org.apache.commons.lang3.time.StopWatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ChallengeSolver {
    private final long MAX_RUNTIME = 600000; // milliseconds; 10 minutes

    protected List<Map<Integer, Integer>> orders;
    protected List<Map<Integer, Integer>> aisles;
    protected int nItems;
    protected int waveSizeLB;
    protected int waveSizeUB;

    public ChallengeSolver(
            List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) {
        this.orders = orders;
        this.aisles = aisles;
        this.nItems = nItems;
        this.waveSizeLB = waveSizeLB;
        this.waveSizeUB = waveSizeUB;
    }

    public ChallengeSolution solve(StopWatch stopWatch) {
        // Conjuntos para armazenar os pedidos e corredores selecionados
        Set<Integer> selectedOrders = new HashSet<>();
        Set<Integer> visitedAisles = new HashSet<>();
        int totalUnits = 0;
        
        // Lista de todos os índices de pedidos (candidatos)
        List<Integer> candidateOrders = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            candidateOrders.add(i);
        }
        
        boolean orderAdded = true;
        // Enquanto houver algum pedido que possamos adicionar
        while (orderAdded) {
            orderAdded = false;
            double bestRatio = -1.0;
            int bestOrder = -1;
            Set<Integer> bestNewAisles = new HashSet<>();
            int bestPotentialUnits = 0;
            
            // Itera sobre os pedidos candidatos
            for (int i : candidateOrders) {
                // Se o pedido já foi selecionado, pula
                if (selectedOrders.contains(i)) continue;
                
                int potentialUnits = 0;
                Set<Integer> requiredNewAisles = new HashSet<>();
                boolean canSatisfy = true;
                
                // Para cada item do pedido
                for (Map.Entry<Integer, Integer> entry : orders.get(i).entrySet()) {
                    int item = entry.getKey();
                    int quantity = entry.getValue();
                    boolean satisfied = false;
                    
                    // Primeiro, verifica se algum corredor já visitado pode suprir o item
                    for (int a : visitedAisles) {
                        if (aisles.get(a).getOrDefault(item, 0) >= quantity) {
                            satisfied = true;
                            break;
                        }
                    }
                    
                    // Se não for satisfeito pelos corredores já visitados, procura um novo corredor
                    if (!satisfied) {
                        for (int j = 0; j < aisles.size(); j++) {
                            // Se o corredor já foi visitado, já foi verificado
                            if (visitedAisles.contains(j)) continue;
                            if (aisles.get(j).getOrDefault(item, 0) >= quantity) {
                                requiredNewAisles.add(j);
                                satisfied = true;
                                break;
                            }
                        }
                    }
                    
                    // Se não houver nenhum corredor (novo ou já visitado) que forneça o item, o pedido não pode ser satisfeito
                    if (!satisfied) {
                        canSatisfy = false;
                        break;
                    } else {
                        potentialUnits += quantity;
                    }
                } // Fim de cada item do pedido
                
                if (!canSatisfy) {
                    continue; // Pula para o próximo pedido
                }
                
                // Verifica se a adição desse pedido ultrapassaria o limite superior
                if (totalUnits + potentialUnits > waveSizeUB) {
                    continue;
                }
                
                // Calcula a razão: quantos itens adiciona por corredor novo necessário
                double ratio = potentialUnits / (requiredNewAisles.size() + 1e-6); // Adiciona um pequeno valor para evitar divisão por zero
                
                // Escolhe o pedido com a melhor razão
                if (ratio > bestRatio) {
                    bestRatio = ratio;
                    bestOrder = i;
                    bestNewAisles = requiredNewAisles;
                    bestPotentialUnits = potentialUnits;
                }
            } // Fim da iteração dos candidatos
            
            // Se encontramos um pedido que melhora a solução
            if (bestOrder != -1) {
                selectedOrders.add(bestOrder);
                // Adiciona os corredores novos necessários para esse pedido
                visitedAisles.addAll(bestNewAisles);
                totalUnits += bestPotentialUnits;
                orderAdded = true;
            }
        } // Fim do while
        
        // Se, ao final, o total de itens não alcançou o limite inferior, a solução é inválida
        if (totalUnits < waveSizeLB) {
            return null;
        }
        
        // Retorna a solução com os pedidos selecionados e os corredores visitados
        return new ChallengeSolution(selectedOrders, visitedAisles);
    }
    
    // Método auxiliar para calcular o total de itens de um pedido
    //private int getTotalItems(Map<Integer, Integer> order) {
    //    return order.values().stream().mapToInt(Integer::intValue).sum();
    //}

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