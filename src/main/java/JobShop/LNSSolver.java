package JobShop;

import org.ddolib.astar.core.solver.AStarSolver;
import org.ddolib.common.dominance.DominanceChecker;
import org.ddolib.common.solver.SearchStatistics;
import org.ddolib.common.solver.SearchStatus;
import org.ddolib.common.solver.Solution;
import org.ddolib.modeling.AcsModel;
import org.ddolib.modeling.Model;
import org.ddolib.modeling.Problem;
import org.ddolib.modeling.Solvers;
import org.ddolib.util.io.SolutionPrinter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

public class LNSSolver {

    public static SearchStatistics solve(JSInstance instance, long timeLimit, int seed, int obj){
        long t0 = System.currentTimeMillis();
        ArrayList<int[]> ubs = new ArrayList<>();
        int nFail = 0;
        int bestUb = Integer.MAX_VALUE;
        HashMap<Integer, HashSet<Integer>> predCritcalPath = new HashMap<>();
        JSProblem problem = new JSProblem(instance);
        for (int i = 0; i < instance.getnJobs(); i++) {
            for (int j = 0; j < instance.getnMachines(); j++) {
                for( int k = j+1; k < instance.getnMachines(); k++) {
                    if (problem.addPrecedence(i * instance.getnMachines() + j, i * instance.getnMachines() + k)==-1){
                        return new SearchStatistics(SearchStatus.OPTIMAL, -1, -1, timeLimit, bestUb, -1, 0, -1,-1,ubs);

                    }
                }
            }
        }
        final AcsModel<JSState> model = new AcsModel<>() {

            @Override
            public Problem<JSState> problem() {
                return problem;
            }
            @Override
            public  JSFastLowerBound lowerBound() {
                return  new JSFastLowerBound(problem);
            }
            @Override
            public DominanceChecker<JSState> dominance(){
                return new AstarDominanceChecker<>(new JSDominance(problem), problem.nbVars() );
            }
        };
        Solution bestSolution = Solvers.minimizeAcs(model, s -> s.nbSols()==1, (sol, s) -> {
        });
        bestUb = (int) bestSolution.value();
        ubs.add(new int[]{(int) bestSolution.statistics().incumbent(), (int) ( System.currentTimeMillis() - t0)});
        if (bestSolution.statistics().incumbent()==obj||bestSolution.statistics().status()== SearchStatus.OPTIMAL){
            return bestSolution.statistics();
        }
        int[] sol = bestSolution.solution();
        int[][] solByMachine = new int[instance.getnMachines()][instance.getnJobs()];
        for(int i=0; i<instance.getnMachines(); i++){
            int idx=0;
            for(int j=0; j<sol.length; j++){
                if (instance.getMachine()[sol[j]/ instance.getnMachines()][sol[j]% instance.getnMachines()]==i){
                    solByMachine[i][idx]=sol[j];
                    idx++;
                }
            }
        }
        AstarDominanceChecker<JSState> dominance =  new AstarDominanceChecker<>(new JSDominance(problem), problem.nbVars() );
        final Model<JSState> lnsmodel = new AcsModel<>() {

            @Override
            public Problem<JSState> problem() {
                return problem;
            }
            @Override
            public  JSFastLowerBound lowerBound() {
                return  new JSFastLowerBound(problem);
            }
            @Override
            public DominanceChecker<JSState> dominance(){
                return dominance;
            }
        };
        problem.setUB(bestUb-1);
        Random random = new Random(seed);
        double perc = 0.7;
        while(System.currentTimeMillis() - t0 < timeLimit) {
            int add = 0;
            while (add <perc*instance.getnMachines() * (instance.getnJobs()-1)) {
                for (int i = 0; i < instance.getnMachines(); i++) {
                    for (int j = 0; j < instance.getnJobs() - 1; j++) {
                        if ((double) Math.abs(random.nextInt()) / Integer.MAX_VALUE > 0.5) {
                            int ret = problem.addPrecedence(solByMachine[i][j], solByMachine[i][j + 1]);
                            if (ret == 0) {
                                add++;
                            }
                        }
                        if (add >= perc * instance.getnMachines() * (instance.getnJobs() - 1)) {
                            break;
                        }
                    }
                    if (add >= perc * instance.getnMachines() * (instance.getnJobs() - 1)) {
                        break;
                    }
                }
            }

            AStarSolver<JSState> astar = new AStarSolver<>(lnsmodel);
            astar.setBestUB(bestUb-1);
            bestSolution = astar.minimize(s-> false, (solu, s) -> {
                }
            );

            if(bestSolution.statistics().nbSols()>0) {
                bestUb = (int) bestSolution.value();
                ubs.add(new int[]{(int) bestSolution.statistics().incumbent(), (int) ( System.currentTimeMillis() - t0)});
                problem.setUB(bestUb-1);
                sol = bestSolution.solution();
                for (int i = 0; i < instance.getnMachines(); i++) {
                    int idx = 0;
                    for (int j = 0; j < sol.length; j++) {
                        if (instance.getMachine()[sol[j] / instance.getnMachines()][sol[j] % instance.getnMachines()] == i) {
                            solByMachine[i][idx] = sol[j];
                            idx++;
                        }
                    }
                }
                nFail=0;
                perc = 0.7;
            }else{
                nFail+=1;
            }
            if (nFail>100){
                perc-=0.05;
                if (perc<0.2){
                    perc=0.7;
                }
                nFail=0;
            }

            if (problem.clear()==-1){
                return new SearchStatistics(SearchStatus.OPTIMAL, -1, -1, timeLimit, bestUb, -1, 0, -1,-1,ubs);
            }
            dominance.reset();
            for (int i = 0; i < instance.getnJobs(); i++) {
                for (int j = 0; j < instance.getnMachines(); j++) {
                    for( int k = j+1; k < instance.getnMachines(); k++) {
                        if (problem.addPrecedence(i * instance.getnMachines() + j, i * instance.getnMachines() + k)==-1){
                            return new SearchStatistics(SearchStatus.OPTIMAL, -1, -1, timeLimit, bestUb, -1, 0, -1,-1,ubs);

                        }
                    }
                }
            }
            if (bestSolution.statistics().incumbent()==obj){
                return new SearchStatistics(SearchStatus.UNKNOWN, -1, -1, timeLimit, bestUb, -1, 0, -1,-1,ubs);
            }

        }
        return new SearchStatistics(SearchStatus.UNKNOWN, -1, -1, timeLimit, bestUb, -1, 0, -1,-1,ubs);
    }
}
