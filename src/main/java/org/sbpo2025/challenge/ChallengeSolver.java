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
        
        // Cria uma lista de índices dos pedidos
        List<Integer> orderIndices = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            orderIndices.add(i);
        }
        // Ordena os pedidos de forma decrescente com base no total de itens do pedido
        orderIndices.sort((a, b) -> Integer.compare(getTotalItems(orders.get(b)), getTotalItems(orders.get(a))));
        
        // Itera pelos pedidos ordenados
        for (int i : orderIndices) {
            // Calcula quantos itens o pedido "i" tem e quais corredores (aisles) podem atendê-lo
            int potentialUnits = 0;
            Set<Integer> requiredAisles = new HashSet<>();
            
            for (Map.Entry<Integer, Integer> entry : orders.get(i).entrySet()) {
                int item = entry.getKey();
                int quantity = entry.getValue();
                
                // Procura um corredor que tenha estoque suficiente para esse item
                for (int j = 0; j < aisles.size(); j++) {
                    if (aisles.get(j).getOrDefault(item, 0) >= quantity) {
                        requiredAisles.add(j);
                        potentialUnits += quantity;
                        break; // Para esse item, já encontrou um corredor adequado
                    }
                }
            }
            
            // Se a adição desse pedido ultrapassar o limite superior, não o adiciona
            if (totalUnits + potentialUnits > waveSizeUB) {
                continue;
            }
            
            // Adiciona o pedido e os corredores necessários
            selectedOrders.add(i);
            visitedAisles.addAll(requiredAisles);
            totalUnits += potentialUnits;
            
            // Se atingirmos ou excedermos o limite inferior, encerramos a seleção
            if (totalUnits >= waveSizeLB) {
                break;
            }
        }
        
        // Se, ao final, o total não for suficiente (menor que LB), a solução é inviável
        if (totalUnits < waveSizeLB) {
            return null;
        }
        
        // Retorna a solução onde:
        // - selectedOrders: pedidos que garantem que o total de itens está entre LB e UB.
        // - visitedAisles: corredores que foram efetivamente necessários para atender esses pedidos.
        return new ChallengeSolution(selectedOrders, visitedAisles);
    }
    
    // Método auxiliar para calcular o total de itens de um pedido
    private int getTotalItems(Map<Integer, Integer> order) {
        return order.values().stream().mapToInt(Integer::intValue).sum();
    }

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