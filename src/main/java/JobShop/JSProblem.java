package JobShop;

import org.ddolib.ddo.core.Decision;
import org.ddolib.modeling.InvalidSolutionException;
import org.ddolib.modeling.Problem;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;

import static java.lang.Math.*;
import static org.maxicp.cp.CPFactory.*;

public class JSProblem implements Problem<JSState> {

    public final JSInstance data;
    private int[] q;
    private CPSolver cp;
    private CPIntervalVar[][] activities;
    private int[][] starts;
    private int ub;
    public HashMap<Integer, HashSet<Integer>> precedences;
    public HashMap<Integer, ArrayList<Integer>> succs;

    public JSProblem(JSInstance data) {
        this.data = data;
        this.ub = data.getHorizon();
        q = new int[data.getnMachines() * data.getnJobs()];
        starts = new int[data.getnJobs()][data.getnMachines()];
        for (int i = 0; i < data.getnJobs(); i++) {
            for (int j = 0 ; j < data.getnMachines(); j++) {
                q[i * data.getnMachines() + j] = 0;
                starts[i][j] = 0;
            }
        }
        this.precedences = new HashMap<>();
        this.succs = new HashMap<>();
        initCPModel();
    }

    public void computeQ(int[] q, int t){
        if (!precedences.containsKey(t)){
            return;
        }
        for (int p: precedences.get(t)) {
            q[p] = max(q[p], q[t]+data.getDuration()[t/data.getnMachines()][t%data.getnMachines()]);
            computeQ(q,p);
        }
    }
    /*
    Add precedence task1 -> task2
     */
    public int addPrecedence(int task1, int task2){
        try {
            cp.post(endBeforeStart(activities[task1 / data.getnMachines()][task1 % data.getnMachines()], activities[task2 / data.getnMachines()][task2 % data.getnMachines()]));
        }catch (InconsistencyException e){
            return -1;
        }
        if (!precedences.containsKey(task2)) {
            precedences.put(task2, new HashSet<>());
        }
        precedences.get(task2).add(task1);
        if (!succs.containsKey(task1)) {
            succs.put(task1, new ArrayList<>());
        }
        succs.get(task1).add(task2);

        q[task1] = max(q[task1], q[task2]+data.getDuration()[task2/data.getnMachines()][task2%data.getnMachines()]);
        computeQ(q, task1);
        starts[task2/data.getnMachines()][task2%data.getnMachines()] = max(starts[task2/data.getnMachines()][task2%data.getnMachines()], starts[task1/data.getnMachines()][task1%data.getnMachines()] + data.getDuration()[task1/data.getnMachines()][task1%data.getnMachines()]);
        updateEst(starts, task2);
        return 0;
    }

    public void setUB(int ub){
        this.ub = ub;
    }

    public int clear(){
        for (int i = 0; i < data.getnJobs(); i++) {
            for (int j = 0 ; j < data.getnMachines(); j++) {
                q[i * data.getnMachines() + j] = 0;
                starts[i][j] = 0;
            }
        }
        try {
            initCPModel();
        }catch (InconsistencyException e){
            return -1;
        }
        precedences.clear();
        succs.clear();
        return 0;
    }

    @Override
    public int nbVars() {
        return data.getnMachines()*data.getnJobs();
    }

    @Override
    public JSState initialState() {
        JSState root = new JSState(data.getnJobs(), data.getnMachines());
        for (int i = 0; i < data.getnJobs(); i++) {
            for(int j = 0; j < data.getnMachines(); j++){
                if(!precedences.containsKey(i*data.getnMachines()+j)){
                    root.eta.set(i*data.getnMachines()+j);
                }
                root.est[i][j] = starts[i][j];
            }

        }
        root = fixPoint(root);
        return root;
    }

    @Override
    public double initialValue() {
        return 0;
    }

    @Override
    public Iterator<Integer> domain(JSState state, int l) {
        ArrayList<Integer> domain = new ArrayList<>();
        for (int i = state.eta.nextSetBit(0); i >= 0; i = state.eta.nextSetBit(i + 1)) {
            boolean add = true;
            if (state.preds.containsKey(i)) {
                for(Integer p : state.preds.get(i)) {
                    if (!state.done.get(p)){
                        add = false;
                        break;
                    }
                }
            }
            if (add) {
                domain.add(i);
            }
        }
        return domain.iterator();
    }

    public void updateEst(int[][] est, int t){
        if (!succs.containsKey(t)) {
            return;
        }
        for (int s: succs.get(t)) {
            est[s/data.getnMachines()][s%data.getnMachines()] = max(est[s/data.getnMachines()][s%data.getnMachines()], est[t/data.getnMachines()][t%data.getnMachines()]+data.getDuration()[t/data.getnMachines()][t%data.getnMachines()]);
            updateEst(est, s);
        }
    }

    @Override
    public JSState transition(JSState state, Decision decision) {
        JSState newState = new JSState(state.est);
        newState.done = (BitSet) state.done.clone();
        newState.done.set(decision.val());
        int makespan = getMakespan(newState);
        int jobId = decision.val() / data.getnMachines();
        int o = decision.val() % data.getnMachines();
        int machine = data.getMachine()[jobId][o];
        for (int j : data.getTasks()[machine]) {
            if (!newState.done.get(j)) {
                int job = j / data.getnMachines();
                int op = j % data.getnMachines();
                newState.est[job][op] = max(newState.est[job][op], newState.est[jobId][o] + data.getDuration()[jobId][o]);
                updateEst(newState.est, j);
            }
        }
        for(int i=0; i<data.getnMachines(); i++){
            if (!newState.done.get(jobId*data.getnMachines()+i)) {
                newState.est[jobId][i] = max(newState.est[jobId][i], newState.est[jobId][o]+data.getDuration()[jobId][o]);
            }
        }

        newState = fixPoint(newState);
        if (newState==null){
            return null;
        }

        for (int i = 0; i < data.getnJobs(); i++) {
            for (int j = 0; j < data.getnMachines(); j++) {
                if (!newState.done.get(i*data.getnMachines()+j)){
                    boolean available = true;
                    if (precedences.containsKey(i*data.getnMachines()+j)) {
                        for (int pred : precedences.get(i * data.getnMachines() + j)) {
                            if (!newState.done.get(pred)) {
                                available = false;
                                break;
                            }
                        }
                    }
                    if (available) {
                        if (newState.est[i][j] + data.getDuration()[i][j] > makespan) {
                            newState.eta.set(i * data.getnMachines() + j);
                        } else if (newState.est[i][j] + data.getDuration()[i][j] == makespan && data.getMachine()[i][j] > newState.lastMachine) {
                            newState.eta.set(i * data.getnMachines() + j);
                        }
                    }
                }
            }
        }
        return newState;
    }

    @Override
    public double transitionCost(JSState state, Decision decision) {
        int makespan = getMakespan(state);
        int jobId = decision.val() / data.getnMachines();
        int opId = decision.val() % data.getnMachines();
        if (state.done.cardinality()==this.nbVars()-1){
            ub = min(ub, state.est[jobId][opId]+data.getDuration()[jobId][opId]);
        }

        return state.est[jobId][opId]+data.getDuration()[jobId][opId] - makespan;
    }

    @Override
    public double evaluate(int[] ints) throws InvalidSolutionException {
        return 0;
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
                activities[i][j].setEndMax(ub - q[i * data.getnMachines() + j]);
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
            cp.post(noOverlap(machineActivities.toArray(new CPIntervalVar[0])));
        }

        for (int j = 0; j < nJobs; j++) {
            ArrayList<CPIntervalVar> jobActivities = new ArrayList<>();
            for (int i = 0; i < nMachines; i++) {
                jobActivities.add(activities[j][i]);
            }
            cp.post(noOverlap(jobActivities.toArray(new CPIntervalVar[0])));
        }

    }

    private JSState fixPoint(JSState state){
        int level = cp.getStateManager().getLevel();
        cp.getStateManager().saveState();
        int makespan = getMakespan(state);
        try {
            for (int i = 0; i < data.getnJobs(); i++) {
                for (int j = 0; j < data.getnMachines(); j++) {
                    if (state.done.get(i*data.getnMachines()+j)){
                        activities[i][j].setStart(state.est[i][j]);
                        activities[i][j].setEnd(state.est[i][j]+data.getDuration()[i][j]);
                    }else{
                        boolean explored = true;
                        for (Integer pred : precedences.getOrDefault(i* data.getnMachines()+j, new HashSet<>())){
                            if (!state.done.get(pred)){
                                explored = false;
                                break;
                            }
                        }
                        if (explored) {
                            int apt = state.est[i][j] + data.getDuration()[i][j];
                            if (state.est[i][j] + data.getDuration()[i][j] < makespan) {
                                int es = Integer.MAX_VALUE;
                                for (int t : data.getTasks()[data.getMachine()[i][j]]) {
                                    if (!state.done.get(t)) {
                                        if (state.est[t / data.getnMachines()][t % data.getnMachines()] + data.getDuration()[t / data.getnMachines()][t % data.getnMachines()] > makespan ||
                                                (state.est[t / data.getnMachines()][t % data.getnMachines()] + data.getDuration()[t / data.getnMachines()][t % data.getnMachines()] == makespan && data.getMachine()[t / data.getnMachines()][t % data.getnMachines()] > state.lastMachine)) {
                                            es = min(es, state.est[t / data.getnMachines()][t % data.getnMachines()] + data.getDuration()[t / data.getnMachines()][t % data.getnMachines()]);
                                        }
                                    }
                                }
                                for (int t = 0 ; t < data.getnMachines(); t++){
                                    if (t!=j &&!state.done.get(i* data.getnMachines()+t)) {
                                        if (!precedences.containsKey(i* data.getnMachines()+t)||!precedences.get(i* data.getnMachines()+t).contains(i* data.getnMachines()+j)){
                                            if (state.est[i][t] + data.getDuration()[i][t] > makespan ||
                                                    (state.est[i][t] + data.getDuration()[i][t] == makespan && data.getMachine()[i][t] > state.lastMachine)) {
                                                es = min(es, state.est[i][t] + data.getDuration()[i][t]);
                                            }
                                        }
                                    }
                                }
                                apt = es + data.getDuration()[i][j];
                            } else if (state.est[i][j] + data.getDuration()[i][j] == makespan && data.getMachine()[i][j] < state.lastMachine) {
                                int es = Integer.MAX_VALUE;
                                for (int t : data.getTasks()[data.getMachine()[i][j]]) {
                                    if (!state.done.get(t)) {
                                        if (state.est[t / data.getnMachines()][t % data.getnMachines()] + data.getDuration()[t / data.getnMachines()][t % data.getnMachines()] > makespan ||
                                                (state.est[t / data.getnMachines()][t % data.getnMachines()] + data.getDuration()[t / data.getnMachines()][t % data.getnMachines()] == makespan && data.getMachine()[t / data.getnMachines()][t % data.getnMachines()] > state.lastMachine)) {
                                            es = min(es, state.est[t / data.getnMachines()][t % data.getnMachines()] + data.getDuration()[t / data.getnMachines()][t % data.getnMachines()]);
                                        }
                                    }
                                }
                                for (int t = 0 ; t < data.getnMachines(); t++){
                                    if (t!=j &&!state.done.get(i* data.getnMachines()+t)) {
                                        if (!precedences.containsKey(i* data.getnMachines()+t)||!precedences.get(i* data.getnMachines()+t).contains(i* data.getnMachines()+j)){
                                            if (state.est[i][t] + data.getDuration()[i][t] > makespan ||
                                                    (state.est[i][t] + data.getDuration()[i][t] == makespan && data.getMachine()[i][t] > state.lastMachine)) {
                                                es = min(es, state.est[i][t] + data.getDuration()[i][t]);
                                            }
                                        }
                                    }
                                }
                                apt = es + data.getDuration()[i][j];
                            }
                            activities[i][j].setStartMin(apt - data.getDuration()[i][j]);
                        }
                    }
                }
            }
            for (int i = 0; i < data.getnJobs(); i++) {
                for (int j = 0; j < data.getnMachines(); j++) {
                    if (!state.done.get(i * data.getnMachines() + j)) {
                        activities[i][j].setEndMax(ub - q[i * data.getnMachines() + j]);
                    }
                }
            }
            cp.fixPoint();

            state.lb = lowerBound(activities);
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
                            if (!state.preds.containsKey(taskbymachine[i])) {
                                state.preds.put(taskbymachine[i], new HashSet<>());
                            }
                            state.preds.get(taskbymachine[i]).add(taskbymachine[l]);
                        }
                    }

                }
            }
        }
        for (int i=0; i<data.getnJobs(); i++) {
            for (int j = 0; j < data.getnMachines(); j++) {
                for(int k=0; k<data.getnMachines(); k++) {
                    if (j!=k){
                        if (activities[i][j].startMin() + this.data.getDuration()[i][j] > activities[i][k].endMax() - this.data.getDuration()[i][k]) {
                            if (!state.preds.containsKey(i* data.getnMachines()+j)) {
                                state.preds.put(i* data.getnMachines()+j, new HashSet<>());
                            }
                            state.preds.get(i* data.getnMachines()+j).add(i* data.getnMachines()+k);
                        }
                    }
                }
            }
        }

        state.lb = dichotomicSearch(state);
        cp.getStateManager().restoreStateUntil(level);
        return state;
    }

    private int dichotomicSearch(JSState state){
        int min = state.lb;
        int max = ub;
        while (min < max) {
            int mid = (min+max)/2;
            cp.getStateManager().saveState();
            try{
                for (int i = 0; i < data.getnJobs(); i++) {
                    for (int j = 0; j < data.getnMachines(); j++) {
                        if (!state.done.get(i * data.getnMachines() + j)) {
                            activities[i][j].setEndMax(mid - q[i * data.getnMachines() + j]);
                        }
                    }
                }
                cp.fixPoint();
                max = mid;

            }catch(InconsistencyException e){
                min = mid+1;
                cp.getStateManager().restoreState();
            }
        }
        return min;
    }

    private int lowerBound(CPIntervalVar[][] state){
        int bound = 0;
        int[] p = new int[data.getnMachines()*data.getnJobs()];
        Integer[][] jobByReleaseTime = new Integer[data.getnMachines()][data.getnJobs()];
        for (int i = 0; i < data.getnMachines(); i++) {
            for (int j = 0; j < data.getnJobs(); j++) {
                jobByReleaseTime[i][j] = data.getTasks()[i][j];
                p[j * data.getnMachines() + i] = data.getDuration()[j][i];

            }
            //Sort operations by earliest starting time
            Arrays.sort(jobByReleaseTime[i], Comparator.comparingInt(j -> state[j / data.getnMachines()][j % data.getnMachines()].startMin()));
        }
        for (int i = 0; i < data.getnMachines(); i++) {
            PriorityQueue<Integer> pq = new PriorityQueue<>(Comparator.comparingInt(a ->state[a / data.getnMachines()][a % data.getnMachines()].endMax()));
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
    @Override
    public boolean stateSpaceReduction(JSState state) {
        int makespan = getMakespan(state);
        for (int i = 0; i < data.getnMachines(); i++) {
            boolean oneNotInEta = false;
            boolean allEquals = true;
            boolean sameJob = true;
            for (int j = 0; j < data.getnJobs(); j++) {
                for (int k = 0; k < data.getnMachines(); k++) {
                    if (!state.done.get(j * data.getnMachines() + k)) {
                        boolean allPredecessorDone = true;
                        if (precedences.containsKey(j * data.getnMachines() + k)) {
                            for (Integer p : precedences.get(j * data.getnMachines() + k)) {
                                if (!state.done.get(p)) {
                                    allPredecessorDone = false;
                                    break;
                                }
                            }
                        }
                        if (allPredecessorDone && data.getMachine()[j][k] == i) {
                            int apt = state.est[j][k] + data.getDuration()[j][k];
                            if (!state.eta.get(j * data.getnMachines() + k)) {
                                oneNotInEta = true;
                                apt = makespan+data.getDuration()[j][k];
                                for(int l=0; l<data.getnMachines(); l++) {
                                    if (l!=k && !state.done.get(j * data.getnMachines() + l)) {
                                        if (precedences.containsKey(j * data.getnMachines() + l)) {
                                            if (!precedences.get(j * data.getnMachines() + l).contains(j * data.getnMachines() + k)) {
                                                if (state.est[j][l] <= state.est[j][k] + data.getDuration()[j][k] && state.est[j][k] <= state.est[j][l]+data.getDuration()[j][l]) {
                                                    sameJob = false;
                                                }
                                            }
                                        } else {
                                            if (state.est[j][l] <= state.est[j][k] + data.getDuration()[j][k] && state.est[j][k] <= state.est[j][l]+data.getDuration()[j][l]) {
                                                sameJob = false;
                                            }
                                        }
                                    }
                                }
                            }
                            if (apt != makespan+data.getDuration()[j][k]){
                                allEquals = false;
                            }
                        }
                    }
                }
            }
            if (oneNotInEta && allEquals && sameJob) {
                return true;
            }
        }
        return false;
    }

    public int getMakespan(JSState state){
        int makespan = 0;
        for (int i = 0; i < data.getnJobs(); i++) {
            for (int j = 0; j < data.getnMachines(); j++) {
                if (state.done.get(i*data.getnMachines()+j)){
                    makespan = max(makespan, state.est[i][j]+data.getDuration()[i][j]);
                }
            }
        }
        return makespan;
    }
}
