package pl.poznan.put.comparison.global;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.poznan.put.matching.StructureSelection;

public class ParallelGlobalComparator extends Thread {
  private static final Logger LOGGER = LoggerFactory.getLogger(ParallelGlobalComparator.class);
  private final GlobalComparator comparator;
  private final List<StructureSelection> structures;
  private final ProgressListener progressListener;
  private ThreadPoolExecutor threadPool;
  private ExecutorCompletionService<CompareCallable.SingleResult> executor;

  public ParallelGlobalComparator(
      final GlobalComparator comparator,
      final List<StructureSelection> structures,
      final ProgressListener progressListener) {
    super();
    this.comparator = comparator;
    this.structures = Collections.unmodifiableList(structures);
    this.progressListener = progressListener;
  }

  @Override
  public final void run() {
    submitAll();
    waitForCompletion();

    final List<String> names = collectNames();
    final GlobalResult[][] results = fillResultsMatrix();
    final GlobalMatrix matrix = new GlobalMatrix(comparator, names, results);

    progressListener.complete(matrix);
  }

  private void submitAll() {
    threadPool =
        (ThreadPoolExecutor)
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    executor = new ExecutorCompletionService<>(threadPool);
    final int size = structures.size();

    for (int i = 0; i < size; i++) {
      for (int j = i + 1; j < size; j++) {
        final Callable<CompareCallable.SingleResult> task = new CompareCallable(structures, i, j);
        executor.submit(task);
      }
    }
  }

  private void waitForCompletion() {
    threadPool.shutdown();
    final long size = structures.size();
    final long all = (size * (size - 1)) / 2;
    long completed;

    while ((completed = threadPool.getCompletedTaskCount()) < all) {
      progressListener.setProgress((int) completed);
      try {
        Thread.sleep(500);
      } catch (final InterruptedException e) {
        interrupt();
      }
    }
    progressListener.setProgress((int) all);
  }

  private List<String> collectNames() {
    final List<String> names = new ArrayList<>();
    for (final StructureSelection selection : structures) {
      names.add(selection.getName());
    }
    return names;
  }

  private GlobalResult[][] fillResultsMatrix() {
    final int size = structures.size();
    final GlobalResult[][] results = new GlobalResult[size][];
    for (int i = 0; i < size; i++) {
      results[i] = new GlobalResult[size];
    }

    final int all = (size * (size - 1)) / 2;
    for (int i = 0; i < all; i++) {
      try {
        final CompareCallable.SingleResult result = executor.take().get();
        results[result.i][result.j] = results[result.j][result.i] = result.value;
      } catch (final InterruptedException | ExecutionException e) {
        ParallelGlobalComparator.LOGGER.error("Failed to compare a pair of structures", e);
      }
    }

    return results;
  }

  public interface ProgressListener {
    void setProgress(int progress);

    void complete(GlobalMatrix matrix);
  }

  public static class ProgressListenerAdapter implements ProgressListener {
    @Override
    public void setProgress(final int progress) {
      // do nothing
    }

    @Override
    public void complete(final GlobalMatrix matrix) {
      // do nothing
    }
  }

  public class CompareCallable implements Callable<CompareCallable.SingleResult> {
    private final int row;
    private final int column;
    private final StructureSelection s1;
    private final StructureSelection s2;

    private CompareCallable(
        final List<StructureSelection> structures, final int row, final int column) {
      super();
      s1 = structures.get(row);
      s2 = structures.get(column);
      this.row = row;
      this.column = column;
    }

    @Override
    public final SingleResult call() throws Exception {
      final GlobalResult comp = comparator.compareGlobally(s1, s2);
      return new SingleResult(row, column, comp);
    }

    public class SingleResult {
      private final int i;
      private final int j;
      private final GlobalResult value;

      private SingleResult(final int i, final int j, final GlobalResult value) {
        super();
        this.i = i;
        this.j = j;
        this.value = value;
      }
    }
  }
}
