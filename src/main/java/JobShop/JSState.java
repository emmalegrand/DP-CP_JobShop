package JobShop;

import java.util.*;

import static java.lang.Math.max;

public class JSState {
    public int[][] est;
    public BitSet done;
    public BitSet eta;
    public HashMap<Integer, HashSet<Integer>> preds;
    public int lb;
    public int lastMachine;
    public int makespan;

    public JSState(int[][] est) {
        this.est = new int[est.length][est[0].length];
        for (int i = 0; i < est.length; i++) {
            for (int j = 0; j < est[0].length; j++) {
                this.est[i][j] = est[i][j];
            }
        }
        this.done = new BitSet(est.length* est[0].length);
        this.preds = new HashMap<>();
        this.eta = new BitSet(est.length);
        this.makespan = 0;
    }
    public JSState(int nJobs, int nMachines){
        this.est = new int[nJobs][nMachines];
        this.done = new BitSet(nJobs* nMachines);
        this.preds = new HashMap<>();
        this.eta = new BitSet(nJobs);
        this.makespan = 0;
    }

    @Override
    public String toString() {
        return "Est : "+Arrays.deepToString(est) + "\n Done : " + done + "\n Preds : "+preds;
    }

    @Override
    public int hashCode(){

        int hash = 0;
        for (int i = 0; i < est.length; i++) {
            for (int j = 0; j < est[0].length; j++) {
                if (!this.done.get(i*est[0].length+j)) {
                    if (this.eta.get(i*est[0].length+j)) {
                        hash += Objects.hash(this.est[i][j]);
                    }else {
                        hash += Objects.hash(this.makespan);
                    }

                }
            }
        }
        hash+= Objects.hash(this.done);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof JSState) {
            JSState js = (JSState) obj;
            if (js.done.equals(this.done)) {
                for (int i = 0; i < this.est.length; i++) {
                    for (int j = 0; j < this.est[0].length; j++) {
                        if (!js.done.get(i* this.est[0].length+j)) {
                            int estObj = js.est[i][j];
                            int est = this.est[i][j];
                            if (!js.eta.get(i* this.est[0].length+j)) {
                                estObj = js.makespan;
                            }
                            if (!this.eta.get(i* this.est[0].length+j)) {
                                est = this.makespan;
                            }
                            if (estObj != est) {
                                return false;
                            }
                        }
                    }
                }
                return true;
            }else{
                return false;
            }
        }
        return false;
    }
}
