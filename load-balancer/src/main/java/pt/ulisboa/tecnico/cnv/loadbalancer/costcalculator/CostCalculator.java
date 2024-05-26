package pt.ulisboa.tecnico.cnv.loadbalancer.costcalculator;

import java.util.ArrayList;

public interface CostCalculator {
    public int estimateCost(ArrayList<Integer> features);

    public int calculateCost(Integer[] metrics);
}
