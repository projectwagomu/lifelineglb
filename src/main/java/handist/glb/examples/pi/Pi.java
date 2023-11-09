package handist.glb.examples.pi;

import static apgas.Constructs.here;
import static apgas.Constructs.places;

import handist.glb.examples.util.*;
import handist.glb.multiworker.Bag;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;
import java.io.Serializable;
import java.util.concurrent.ThreadLocalRandom;

public class Pi implements Bag<Pi, LongSum>, Serializable {

  public long sum;
  public long to_throw = 0;

  public long to_init = 0;

  public int my_id;

  public Pi(long _init) {
    this.to_init = _init;
  }

  @Override
  public void initStaticTasks(int workerId) {
    final int workerPerPlace =
        GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get();
    final int h = (here().id * workerPerPlace) + workerId;
    final int max = places().size() * workerPerPlace;
    long basicWork = to_init / max;
    if (h != 0) {
      this.to_throw = basicWork;
    } else {
      long remainder = to_init % max;
      this.to_throw = basicWork + remainder;
    }
    this.my_id = workerId;
  }

  @Override
  public boolean isEmpty() {
    return this.to_throw == 0;
  }

  @Override
  public boolean isSplittable() {
    return this.to_throw > 1;
  }

  @Override
  public void merge(Pi other) {
    if (other == null) {
      System.out.println("Merge: other is null?!");
      return; // <- weird
    }
    this.to_throw += other.to_throw;
    this.sum += other.sum;
  }

  @Override
  public int process(int workAmount, LongSum sharedObject) {
    int i;
    for (i = 0; this.to_throw > 0 && i < workAmount; ++i) {
      this.to_throw -= 1;
      double x = ThreadLocalRandom.current().nextDouble();
      double y = ThreadLocalRandom.current().nextDouble();
      this.sum += (x * x + y * y <= 1.0) ? 1 : 0;
    }
    return i;
  }

  @Override
  public Pi split(boolean takeAll) {
    Pi other = new Pi(to_init);
    long to_give;
    if (takeAll) {
      to_give = this.to_throw;
    } else {
      to_give = this.to_throw / 2;
    }
    this.to_throw -= to_give;
    other.to_throw = to_give;
    return other;
  }

  @Override
  public void submit(LongSum sum) {
    System.out.println(here() + " sum=" + sum + ", this.sum=" + this.sum);
    sum.sum += this.sum;
  }

  @Override
  public LongSum getResult() {
    return new LongSum(this.sum);
  }

  @Override
  public long getCurrentTaskCount() {
    return to_throw;
  }
}
