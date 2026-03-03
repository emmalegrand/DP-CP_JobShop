/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package JobShop;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.scheduling.NoOverlap;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.algebra.bool.Eq;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

/**
 * The JobShop Problem.
 * <a href="https://en.wikipedia.org/wiki/Job_shop_scheduling">Wikipedia.</a>
 *
 * 
 */
public class OSCP {

    public static CPIntervalVar[] flatten(CPIntervalVar[][] x) {
        return Arrays.stream(x).flatMap(Arrays::stream).toArray(CPIntervalVar[]::new);
    }

    public static int main(String arg) {
        // Reading data:
        try {
            FileInputStream istream = new FileInputStream(arg);
            BufferedReader in = new BufferedReader(new InputStreamReader(istream));
            StringTokenizer tokenizer = new StringTokenizer(in.readLine());
            int nJobs = Integer.parseInt(tokenizer.nextToken());
            int nMachines = Integer.parseInt(tokenizer.nextToken());
            int[][] duration = new int[nJobs][nMachines];
            int[][] machine = new int[nJobs][nMachines];

            int horizon = 0;
            for (int i = 0; i < nJobs; i++) {
                tokenizer = new StringTokenizer(in.readLine());
                for (int j = 0; j < nMachines; j++) {
                    machine[i][j] = j;
                    duration[i][j] = Integer.parseInt(tokenizer.nextToken());

                    horizon += duration[i][j];
                }
            }

            CPSolver cp = makeSolver();

            CPIntervalVar[][] activities = new CPIntervalVar[nJobs][nMachines];
            ArrayList<CPIntVar>[] startOnMachine = new ArrayList[nMachines];
            ArrayList<Integer>[] durationsOnMachine = new ArrayList[nMachines];
            ArrayList<CPIntervalVar>[] activitiesOnMachine = new ArrayList[nMachines];
            ArrayList<CPIntervalVar>[] activitiesOnJob = new ArrayList[nJobs];

            for (int m = 0; m < nMachines; m++) {
                startOnMachine[m] = new ArrayList<CPIntVar>();
                durationsOnMachine[m] = new ArrayList<Integer>();
                activitiesOnMachine[m] = new ArrayList<>();
            }
            for( int j = 0; j < nJobs; j++ ) {
                activitiesOnJob[j] = new ArrayList<>();
            }

            CPIntervalVar[] lasts = new CPIntervalVar[nJobs*nMachines];
            for (int i = 0; i < nJobs; i++) {
                for (int j = 0; j < nMachines; j++) {

                    activities[i][j] = makeIntervalVar(cp);
                    activities[i][j].setLengthMax(duration[i][j]);
                    activities[i][j].setLengthMin(duration[i][j]);
                    activities[i][j].setPresent();
                    activities[i][j].setEndMax(horizon);

                    int m = machine[i][j];
                    activitiesOnMachine[m].add(activities[i][j]);
                    activitiesOnJob[i].add(activities[i][j]);
                    lasts[i*nMachines+j] = activities[i][j];

                }

            }

            ArrayList<CPBoolVar> precedences = new ArrayList<>();

            for (int m = 0; m < nMachines; m++) {
                NoOverlap nonOverlap = noOverlap(activitiesOnMachine[m].toArray(new CPIntervalVar[0]));
                cp.post(nonOverlap);
                precedences.addAll(Arrays.asList(nonOverlap.precedenceVars()));
            }
            for (int j = 0; j < nJobs; j++) {
                NoOverlap nonOverlap = noOverlap(activitiesOnJob[j].toArray(new CPIntervalVar[0]));
                cp.post(nonOverlap);
            }

            CPIntVar makespan = makespan(lasts);

            Objective obj = cp.minimize(makespan);

            Supplier<Runnable[]> fixMakespan = () -> {
                if (makespan.isFixed())
                    return EMPTY;
                return branch(() -> {
                    makespan.getModelProxy().add(new Eq(makespan, makespan.min()));
                });
            };

            CPIntervalVar[] allActivities = flatten(activities);

            DFSearch dfs = CPFactory.makeDfs(cp, setTimes(allActivities));
            AtomicInteger bestMakespan = new AtomicInteger();
            dfs.onSolution(() -> {
                bestMakespan.set(makespan.min());

            });

            SearchStatistics stats = dfs.optimize(obj);
            return bestMakespan.get();

        } catch (IOException | InconsistencyException e) {
            e.printStackTrace();
            return -1;
        }
    }
}
