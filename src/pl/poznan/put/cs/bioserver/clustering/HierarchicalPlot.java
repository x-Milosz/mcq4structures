package pl.poznan.put.cs.bioserver.clustering;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.DefaultXYDataset;

/**
 * Plot of dendrogram representing hierarchical clustering.
 */
public class HierarchicalPlot extends JFrame {
    private static final long serialVersionUID = 1L;

    private static String generateLabel(List<List<Integer>> ids,
            int[] pair, String[] labels) {
        List<Integer> a = ids.get(pair[0]);
        List<Integer> b = ids.get(pair[1]);

        a.addAll(b);
        ids.remove(pair[1]);

        StringWriter writer = new StringWriter();
        writer.append("[ ");
        for (int i : a) {
            writer.append(labels[i]);
            writer.append(", ");
        }
        writer.append(" ]");

        return writer.toString();
    }

    public JFreeChart chart;

    public HierarchicalPlot(double[][] distance, String[] labels, int linkage) {
        int[][] clustering = Clusterer
                .hierarchicalClustering(distance, linkage);
        List<Integer> allocation = Clusterer.clusters.get(0);

        List<double[]> clusters = new ArrayList<>();
        List<List<Integer>> ids = new ArrayList<>();
        for (int i = 0; i < distance.length; ++i) {
            clusters.add(new double[] { allocation.indexOf(i), 0 });
            List<Integer> vector = new ArrayList<>();
            vector.add(i);
            ids.add(vector);
        }

        DefaultXYDataset dataset = new DefaultXYDataset();
        double y = 0.0;
        for (int[] mergedPair : clustering) {
            double[] a = clusters.get(mergedPair[0]);
            double[] b = clusters.get(mergedPair[1]);
            y = Math.max(a[1], b[1]) + mergedPair[2];

            String label = HierarchicalPlot.generateLabel(ids, mergedPair,
                    labels);
            double[][] points = new double[][] { { a[0], a[0], b[0], b[0] },
                    { a[1], y, y, b[1] } };
            dataset.addSeries(label, points);

            a[0] = (a[0] + b[0]) / 2.0;
            a[1] = y;
            clusters.remove(mergedPair[1]);

            // y += 1.0;
        }

        NumberAxis xAxis = new NumberAxis();
        xAxis.setTickLabelsVisible(false);
        xAxis.setTickMarksVisible(false);
        xAxis.setAutoRange(false);
        xAxis.setRange(-1, labels.length);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setTickLabelsVisible(false);
        yAxis.setTickMarksVisible(false);
        XYPlot plot = new XYPlot(dataset, xAxis, yAxis,
                new XYLineAndShapeRenderer());
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);
        chart = new JFreeChart(plot);
        ChartPanel panel = new ChartPanel(chart);
        setContentPane(panel);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension size = toolkit.getScreenSize();
        setSize(size.width / 2, size.height / 2);
        setLocation(size.width / 4, size.height / 4);
    }
}
