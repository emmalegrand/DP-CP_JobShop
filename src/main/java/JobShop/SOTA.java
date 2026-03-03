package JobShop;

import org.ddolib.common.solver.SearchStatistics;
import org.ddolib.common.solver.SearchStatus;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.maxicp.cp.CPFactory.*;

public class SOTA {
    private final JSInstance data;
    private final long timeLimit;
    private final int ub;
    public int[] q;
    private CPSolver cp;
    private CPIntervalVar[][] activities;


    public SOTA(JSInstance data, long timeLimit, int ub) {
        this.data = data;
        this.timeLimit = timeLimit;
        this.ub = ub;
        q = new int[data.getnMachines() * data.getnJobs()];
        for (int i = 0; i < data.getnJobs(); i++) {
            q[i * data.getnMachines() + data.getnMachines() - 1] = 0;
            for (int j = data.getnMachines() - 2; j >= 0; j--) {
                q[i * data.getnMachines() + j] = q[i * data.getnMachines() + j + 1] + data.getDuration()[i][j + 1];
            }
        }
        initCPModel();
    }

    public class State {
        int[][] est;                                                        //2D vector of the earliest starting time of each operation
        int lastFilledMachine;                                              //The machine of the last scheduled operation
        BitSet eta;
        int lb;
        HashMap<Integer, ArrayList<Integer>> preds;

        public State() {
            est = new int[data.getnJobs()][data.getnMachines()];
            eta = new BitSet(data.getnJobs());
            for (int i = 0; i < data.getnJobs(); i++) {
                est[i][0] = 0;
                for (int j = 1; j < data.getnMachines(); j++) {
                    est[i][j] = est[i][j - 1] + data.getDuration()[i][j - 1];
                }
            }
            lastFilledMachine = -1;
            preds = new HashMap<>();
            lb = Integer.MAX_VALUE;
        }

        public State(int[][] est) {
            this.est = new int[data.getnJobs()][data.getnMachines()];
            for (int i = 0; i < data.getnJobs(); i++) {
                System.arraycopy(est[i], 0, this.est[i], 0, data.getnMachines());
            }
            this.eta = new BitSet(data.getnJobs());
            lastFilledMachine = -1;
            preds = new HashMap<>();
            lb = Integer.MAX_VALUE;
        }

        @Override
        public String toString() {
            return "Earliest start :" + Arrays.deepToString(this.est) + "\n" + "Eta: " + eta.toString() + "\n" + "Last Filled machine: " + lastFilledMachine ;
        }
    }

    public SearchStatistics solve(){
        long t0 = System.currentTimeMillis();
        int nbExploredTot = 0;
        int nbWOChild = 0;
        int maxSize = 0;
        HashMap<BitSet, ArrayList<State>> current = new HashMap<>();
        HashMap<BitSet, ArrayList<State>> next = new HashMap<>();
        State root = new State();
        for (int i = 0; i < data.getnJobs(); i++) {
            BitSet done = new BitSet(data.getnJobs());
            done.set(i*data.getnMachines());
            State child = transition(root,done, i*data.getnMachines());
            if(child==null){
                continue;
            }
            current.put(done, new ArrayList<>());
            current.get(done).add(child);
        }
        for( int l=1; l<data.getnJobs()* data.getnMachines(); l++ ) {
            int nbNodesExplored = 0;
            int nbWOChlidpStage = 0;
            for (BitSet done: current.keySet()) {
                for (State state: current.get(done)) {
                    if(stateSpaceReduction(state,done)|| state.lb> ub){
                        continue;
                    }
                    nbNodesExplored++;
                    int nbChild = 0;
                    for (int i = state.eta.nextSetBit(0); i >= 0; i = state.eta.nextSetBit(i + 1)) {
                        if (state.preds.containsKey(i)) {
                            boolean add = true;
                            for(Integer p : state.preds.get(i)) {
                                if (!done.get(p)){
                                    add = false;
                                    break;
                                }
                            }
                            if (!add) {
                                continue;
                            }
                        }
                        BitSet newDone = (BitSet) done.clone();
                        newDone.set(i);
                        State newState = transition(state, newDone, i);
                        if (newState==null){
                            continue;
                        }
                        boolean dominated = false;
                        if (next.containsKey(newDone)) {
                            for (State n : next.get(newDone)) {
                                if (dominance(n, newState, newDone)) {
                                    dominated = true;
                                    break;
                                }
                            }
                            if (!dominated) {
                                Iterator<State> it = next.get(newDone).iterator();
                                while (it.hasNext()) {
                                    if (dominance(newState, it.next(), newDone)){
                                        it.remove();
                                    }
                                }
                                if (!next.containsKey(newDone)) {
                                    next.put(newDone, new ArrayList<>());
                                }
                                nbChild+=1;
                                next.get(newDone).add(newState);
                            }
                        }else{
                            if (!next.containsKey(newDone)) {
                                next.put(newDone, new ArrayList<>());
                            }
                            nbChild+=1;
                            next.get(newDone).add(newState);
                        }

                    }
                    if (nbChild==0){
                        nbWOChild += 1;
                        nbWOChlidpStage+=1;
                    }
                }
                maxSize = max(current.get(done).size(),maxSize);
            }
            current.clear();
            for (BitSet d : next.keySet()){
                current.put(d, next.get(d));
            }
            next.clear();
//            System.out.println("Stage : "+ l + " nbNodesExplored: " + nbNodesExplored + " nbExploredWOChlid: "+ nbWOChlidpStage);
            nbExploredTot+=nbNodesExplored;
        }
        BitSet set = new BitSet();
        for (int i = 0; i < data.getnMachines() * data.getnJobs(); i++) {
            set.set(i);
        }
        ArrayList<State> Xs = current.get(set);
//        System.out.println("nbNodesExploredTot: " + nbExploredTot);
//        System.out.println("nbNodesExploredWOChild: " + nbWOChild);
//        System.out.println("Maxsize = "+maxSize);
//        System.out.println(Xs.getFirst());

        SearchStatistics searchStatistics = new SearchStatistics(SearchStatus.OPTIMAL, nbExploredTot, maxSize, System.currentTimeMillis() - t0, getMakespan(Xs.getFirst(), set), -1, 0,0,0,new ArrayList<>());
        return searchStatistics;
    }

    private boolean stateSpaceReduction(State state, BitSet done){
        int makespan = getMakespan(state,done);
        for (int i = 0; i < data.getnMachines(); i++) {
            boolean oneNotInEta = false;
            boolean allEquals = true;
            for(int j=0; j< data.getnJobs(); j++){
                for(int k=0; k<data.getnMachines(); k++){
                    if (!done.get(j*data.getnMachines() + k)) {
                        if (data.getMachine()[j][k] == i) {
                            int apt = state.est[j][k] + data.getDuration()[j][k];
                            if (!state.eta.get(j * data.getnMachines() + k)) {
                                oneNotInEta = true;
                                apt = makespan+data.getDuration()[j][k];
                            }
                            if (apt != makespan+data.getDuration()[j][k]){
                                allEquals = false;
                            }
                        }
                        break;
                    }
                }
            }
            if(oneNotInEta && allEquals){
                return true;
            }
        }
        return false;
    }

    private boolean dominance(State a, State b, BitSet done) {
        if (done.cardinality()==data.getnMachines()*data.getnJobs()) {
            if (getMakespan(a,done)<=getMakespan(b,done)) {
                return true;
            }
            return false;
        }
        int makespanA = getMakespan(a,done);
        int makespanB = getMakespan(b,done);
        boolean oneStrictInequality = false;
        for (int i = 0; i < data.getnJobs(); i++) {
            for (int j = 0; j < data.getnMachines(); j++) {
                if (!done.get(i*data.getnMachines()+j)){
                    int aptA = a.est[i][j]+data.getDuration()[i][j];
                    int aptB = b.est[i][j]+data.getDuration()[i][j];
                    if (!a.eta.get(i*data.getnMachines()+j)){
                        aptA = makespanA+data.getDuration()[i][j];
                    }
                    if (!b.eta.get(i*data.getnMachines()+j)){
                        aptB = makespanB+data.getDuration()[i][j];
                    }
                    if (aptA <= aptB){
                        oneStrictInequality = true;
                    }else{
                        return false;
                    }
                    break;
                }
            }
        }
        return oneStrictInequality;
    }

    private State transition(State state, BitSet done, int decision){
        State newState = new State(state.est);
        int makespan = getMakespan(newState,done);
        newState.lastFilledMachine = data.getMachine()[decision/data.getnMachines()][decision%data.getnMachines()];
        int jobId = decision / data.getnMachines();
        int o = decision % data.getnMachines();
        int machine = data.getMachine()[jobId][o];
        for (int j : data.getTasks()[machine]) {
            if (!done.get(j)) {
                int job = j / data.getnMachines();
                int op = j % data.getnMachines();
                newState.est[job][op] = max(newState.est[job][op], newState.est[jobId][o] + data.getDuration()[jobId][o]);
                for (int ope = op + 1; ope < data.getnMachines(); ope++) {
                    newState.est[job][ope] = max(newState.est[job][ope], newState.est[job][ope - 1] + data.getDuration()[job][ope - 1]);
                }
            }
        }
        cp.getStateManager().saveState();

        try {
            for (int i = 0; i < data.getnJobs(); i++) {
                for (int j = 0; j < data.getnMachines(); j++) {
                    if (done.get(i*data.getnMachines()+j)){
                        activities[i][j].setStart(newState.est[i][j]);
                        activities[i][j].setEnd(newState.est[i][j]+data.getDuration()[i][j]);
                    }else{
                        int apt = newState.est[i][j] + data.getDuration()[i][j];
                        if (newState.est[i][j]+data.getDuration()[i][j] < makespan){
                            apt = makespan+data.getDuration()[i][j];
                        }else if (newState.est[i][j]+data.getDuration()[i][j]== makespan&& data.getMachine()[i][j]< newState.lastFilledMachine){
                            apt = makespan+data.getDuration()[i][j];
                        }
                        activities[i][j].setStartMin(apt - data.getDuration()[i][j]);
                        break;
                    }
                }
            }
            cp.fixPoint();
            newState.lb = lowerBound(activities);
        }catch (InconsistencyException e){
            cp.getStateManager().restoreState();
            return null;
        }

        for (int j = 0; j < data.getnMachines(); j++) {
            int[] taskbymachine = data.getTasks()[j];
            for (int i = 0; i <taskbymachine.length; i++) {
                for (int l = 0; l < taskbymachine.length; l++) {
                    if (i!=l) {
                        int jobid1 = taskbymachine[i] / data.getnMachines();
                        int opid1 = taskbymachine[i] % data.getnMachines();
                        int jobid2 = taskbymachine[l] / data.getnMachines();
                        int opid2 = taskbymachine[l] % data.getnMachines();
                        if (activities[jobid1][opid1].startMin() + this.data.getDuration()[jobid1][opid1] > activities[jobid2][opid2].endMax() - this.data.getDuration()[jobid2][opid2]) {
                            if (!newState.preds.containsKey(taskbymachine[i])) {
                                newState.preds.put(taskbymachine[i], new ArrayList<>());
                            }
                            newState.preds.get(taskbymachine[i]).add(taskbymachine[l]);
                        }
                    }

                }
            }
        }

        cp.getStateManager().restoreState();
        for (int i = 0; i < data.getnJobs(); i++) {
            for (int j = 0; j < data.getnMachines(); j++) {
                if (!done.get(i*data.getnMachines()+j)){
                    if (newState.est[i][j]+ data.getDuration()[i][j] > makespan){
                        newState.eta.set(i*data.getnMachines()+j);
                    }else if (newState.est[i][j]+ data.getDuration()[i][j] == makespan && data.getMachine()[i][j]> newState.lastFilledMachine){
                        newState.eta.set(i*data.getnMachines()+j);
                    }
                    break;
                }
            }
        }

        return newState;
    }

    private int getMakespan(State state, BitSet done){
        int makespan = 0;
        for (int i = done.nextSetBit(0); i >= 0; i = done.nextSetBit(i + 1)) {
            makespan = max(makespan, state.est[i/data.getnMachines()][i% data.getnMachines()]+data.getDuration()[i/data.getnMachines()][i%data.getnMachines()]);

        }
        return makespan;
    }

    private void initCPModel() {
        int nJobs = data.getnJobs();
        int nMachines = data.getnMachines();

        cp = makeSolver();

        activities = new CPIntervalVar[nJobs][nMachines];
        ArrayList<CPIntVar>[] startOnMachine = new ArrayList[nMachines];
        ArrayList<Integer>[] durationsOnMachine = new ArrayList[nMachines];
        ArrayList<CPIntervalVar>[] activitiesOnMachine = new ArrayList[nMachines];
        ArrayList<CPIntVar>[] positionOnMachine = new ArrayList[nMachines];

        for (int m = 0; m < nMachines; m++) {
            startOnMachine[m] = new ArrayList<CPIntVar>();
            durationsOnMachine[m] = new ArrayList<Integer>();
            activitiesOnMachine[m] = new ArrayList<>();
            positionOnMachine[m] = new ArrayList<>();
        }

        for (int i = 0; i < nJobs; i++) {
            for (int j = 0; j < nMachines; j++) {
                activities[i][j] = makeIntervalVar(cp, false, data.getDuration()[i][j], data.getDuration()[i][j]);
                activities[i][j].setEndMax(min(ub, data.getHorizon()) - q[i * data.getnMachines() + j]);
            }
        }
        for (int i = 0; i < data.getnJobs(); i++) {
            for (int j = 0; j < data.getnMachines() - 1; j++) {
                cp.post(endBeforeStart(activities[i][j], activities[i][j+1]));
            }
        }

        // no overlap between the activities on the same machine
        for (int m = 0; m < nMachines; m++) {
            ArrayList<CPIntervalVar> machineActivities = new ArrayList<>();
            for (int j = 0; j < nJobs; j++) {
                for (int i = 0; i < nMachines; i++) {
                    if (data.getMachine()[j][i] == m) {
                        machineActivities.add(activities[j][i]);
                    }
                }
            }
            cp.post(new DynamicHeadTailAdjustment(machineActivities.toArray(new CPIntervalVar[0])));
        }
    }
    private int lowerBound(CPIntervalVar[][] state){
        int bound = 0;
        int[] q = new int[data.getnMachines()*data.getnJobs()];
        int[] p = new int[data.getnMachines()*data.getnJobs()];
        Integer[][] jobByReleaseTime = new Integer[data.getnMachines()][data.getnJobs()];
        for (int i = 0; i < data.getnMachines(); i++) {
            for (int j = 0; j < data.getnJobs(); j++) {
                jobByReleaseTime[i][j] = data.getTasks()[i][j];
                p[j * data.getnMachines() + i] = data.getDuration()[j][i];

            }
            //Sort operations by earliest starting time
            Arrays.sort(jobByReleaseTime[i], Comparator.comparingInt(j -> state[(int) j / data.getnMachines()][(int) j % data.getnMachines()].startMin()));
        }
        for (int i = 0; i < data.getnJobs(); i++) {
            q[i*data.getnMachines()+data.getnMachines()-1] = 0;
            for (int j = data.getnMachines()-2 ; j >=0 ; j--) {
                q[i*data.getnMachines()+j] = q[i*data.getnMachines()+j+1] + data.getDuration()[i][j+1];
            }
        }
        for (int i = 0; i < data.getnMachines(); i++) {
            PriorityQueue<Integer> pq = new PriorityQueue<>(Comparator.comparingInt(a -> q[(int) a]).reversed());
            int t = 0;
            int idx = 0;
            while (idx < data.getnJobs() || !pq.isEmpty()) {
                // Add all operations released up to current time
                while (idx < data.getnJobs() && state[jobByReleaseTime[i][idx]/data.getnMachines()][jobByReleaseTime[i][idx]%data.getnMachines()].startMin() <= t) {
                    pq.offer(jobByReleaseTime[i][idx]);
                    idx++;
                }

                if (pq.isEmpty()) {
                    // If nothing available, jump to next release time
                    t = state[jobByReleaseTime[i][idx]/data.getnMachines()][jobByReleaseTime[i][idx]%data.getnMachines()].startMin();
                    continue;
                }

                int cur = pq.poll();

                // Next release time (when something more urgent could arrive)
                int nextRelease = (idx < data.getnJobs() ? state[jobByReleaseTime[i][idx]/data.getnMachines()][jobByReleaseTime[i][idx]%data.getnMachines()].startMin() : Integer.MAX_VALUE);

                // Run the operation until either it finishes or a new job arrives
                int runTime = Math.min(p[cur], nextRelease - t);

                p[cur] -= runTime;
                t += runTime;

                if (p[cur] == 0) {
                    bound = max(bound,t+q[cur]);
                } else {
                    pq.offer(cur); // preempted, reinsert
                }
            }
        }
        return bound;
    }

}
